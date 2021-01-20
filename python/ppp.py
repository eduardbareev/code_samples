import fcntl
import os
import random
import re
import shlex
import signal
import string
import subprocess
from enum import Enum
from time import sleep

import psutil
from logbook import Logger

import at_modem
import carrier
import config
import util

logger = Logger(__name__)
CHAT_TPL = """\
ABORT BUSY
ABORT 'NO CARRIER'
ABORT ERROR
REPORT CONNECT
#'' "AT+CFUN=1,1"
"" "ATZ"
OK 'AT+CGDCONT=1,"IP","{apn}"'
OK "ATD{phone}"
CONNECT \\c
"""
LINKNAME_G = 'scmngppp_'
COMMON_PPP_OPTS = ['updetach', 'modem', 'novj', 'noccp', 'debug', 'noipdefault', 'nodefaultroute']
STATUS_CODES = {
    0: "Pppd has detached, or otherwise the connection was successfully established and terminated at the peer's request.",
    1: 'An immediately fatal error of some kind occurred, such as an essential system call failing, or running out of virtual memory.',
    2: 'An error was detected in processing the options given, such as two mutually exclusive options being used.',
    3: 'Pppd is not setuid-root and the invoking user is not root.',
    4: 'The kernel does not support PPP, for example, the PPP kernel driver is not included or cannot be loaded.',
    5: 'Pppd terminated because it was sent a SIGINT, SIGTERM or SIGHUP signal.',
    6: 'The serial port could not be locked.',
    7: 'The serial port could not be opened.',
    8: 'The connect script failed (returned a non-zero exit status).',
    9: 'The command specified as the argument to the pty option could not be run.',
    10: "The PPP negotiation failed, that is, it didn't reach the point where at least one network protocol (e.g. IP) was running.",
    11: 'The peer system failed (or refused) to authenticate itself.',
    12: 'The link was established successfully and terminated because it was idle.',
    13: 'The link was established successfully and terminated because the connect time limit was reached.',
    14: 'Callback was negotiated and an incoming call should arrive shortly.',
    15: 'The link was terminated because the peer is not responding to echo requests.',
    16: 'The link was terminated by the modem hanging up.',
    17: 'The PPP negotiation failed because serial loopback was detected.',
    18: 'The init script failed (returned a non-zero exit status).',
    19: 'We failed to authenticate ourselves to the peer.'
}


class PppState(Enum):
    NOTHING = 3
    STPPING = 2
    RUNNING = 4
    ERRNOTRNNG = 5
    CNNCTNG = 6


class ppp_sess():
    def __init__(self,
                 model_i: at_modem.at_modem,
                 carrier_i: carrier.carrier,
                 sysfs_id
                 ):
        self.model_i = model_i
        self.carrier_i = carrier_i
        self.sysfs_id = sysfs_id
        self.forked_proc_pid = None
        self._should_run = False
        self._stopping_currently = False
        self._connecting_currently = False
        self.linkname = False
        self.unit_num, self.local_addr, self.remote_addr = None, None, None

    @staticmethod
    def _find_forked_proc(forked_proc_srch_str, call_proc):
        def opts_debug_format(opts):
            if opts is None:
                return None
            return ' '.join([shlex.quote(o) for o in opts])

        forked_proc_srch_res = []
        max_attempt = 3
        attempt = 0
        while attempt < max_attempt:
            attempt += 1
            forked_proc_srch_res = util.pgrep(forked_proc_srch_str)
            # either proc.wait() returns before process actually terminated
            # or psutils results are stale by the time it returns them,
            # but sometimes both child and parent processes are returned.
            # have to handle that excplicitly
            tota_grept = len(forked_proc_srch_res)
            for mtch_n, mtch_p in enumerate(forked_proc_srch_res[:]):
                exclude = mtch_p.pid == call_proc.pid
                if exclude:
                    forked_proc_srch_res.remove(mtch_p)

                # meantime process may suddenly disapper
                # and ppid(),cmdline() calls will fail
                mtch_pid = mtch_p.pid
                try:
                    mtch_ppid = mtch_p.ppid()
                except Exception:
                    mtch_ppid = None
                try:
                    mtch_cmdline = mtch_p.cmdline()
                except Exception:
                    mtch_cmdline = None

                logger.debug(f'attempt({attempt}/{max_attempt}) matched pppd '
                             f'matched({mtch_n}/{tota_grept}): '
                             f'exclude={exclude} ppid={mtch_ppid} pid={mtch_pid} '
                             f'cmdline: {opts_debug_format(mtch_cmdline)}')

            if len(forked_proc_srch_res) == 0:
                msg = f'{attempt}/{max_attempt} can\'t. find ppp forked process. forked_proc_srch_str="{forked_proc_srch_str}"'
                logger.error(msg)
                if attempt == max_attempt:
                    raise Exception(msg)
            elif len(forked_proc_srch_res) > 1:
                msg = f'{attempt}/{max_attempt} found more than one process matching pattern and having ppid=1 while searching for ppp forked proces. forked_proc_srch_str="{forked_proc_srch_str}'
                logger.error(msg)
                if attempt == max_attempt:
                    raise Exception(msg)
            else:
                break

        return forked_proc_srch_res[0]

    def _get_linkname(self):
        return f'{LINKNAME_G}{self.sysfs_id}_{self._rand_str(6)}'

    @staticmethod
    def _cmd_debug_format(opts):
        return ' '.join([shlex.quote(o) for o in opts])

    def _get_opts(self, linkname, simulate_fail):
        modem_ppp_opts = self.model_i.modem_ppp_opts()
        carrier_ppp_opts = self.carrier_i.carrier_ppp_opts()
        carrier_ppp_chat_conf = self.carrier_i.carrier_ppp_chat_conf()

        chat_text = CHAT_TPL.format(
            apn=carrier_ppp_chat_conf['apn'],
            phone=carrier_ppp_chat_conf['number'],
        )
        # _write_chat_file() acquires flock on chat_file_h
        chat_file_h, chat_file_path = self._write_chat_file(chat_text)

        port = self.model_i.main_port
        logger.debug(f'types: {type(self.model_i)}, {type(self.carrier_i)} '
                     f'carrier_ppp_opts={carrier_ppp_opts} '
                     f'modem_ppp_opts={modem_ppp_opts} '
                     f'carrier_ppp_chat_conf={carrier_ppp_chat_conf} ')
        chat_to = config.config['ppp_chat_timeout']
        chat_option = f"/usr/sbin/chat -s -v -t {chat_to} -f {chat_file_path}"

        ppp_opts = ['/usr/sbin/pppd', port,
                    'linkname', linkname,
                    'connect', chat_option]
        ppp_opts.extend(COMMON_PPP_OPTS)
        ppp_opts.extend(carrier_ppp_opts)
        if simulate_fail:
            ppp_opts.remove('noipdefault')
        logger.debug(f'chat: {chat_text}')
        return ppp_opts, chat_file_h

    def _wait_ppp_call_proc_and_handle_exit_code(self, call_proc):
        call_proc.wait()  # wait() waits for termination and sets .returncode property
        if call_proc.returncode != 0:
            self._connecting_currently = False
            self._should_run = False
            raise Exception(f'pppd conn err. code={call_proc.returncode} '
                            f'({STATUS_CODES[call_proc.returncode]})')

    def conn(self, simulate_fail=None):
        self._connecting_currently = True
        self._should_run = True
        linkname = self._get_linkname()
        logger.debug(f'linkname for {self} = {linkname}')
        ppp_opts, chat_file_h = self._get_opts(linkname=linkname, simulate_fail=simulate_fail)
        try:
            logger.debug(f"""going to start ppp: >{self._cmd_debug_format(ppp_opts)}<""")
            call_proc = subprocess.Popen(args=ppp_opts, stdout=subprocess.PIPE, )
            call_proc_stdout_lines = self._read_ppp_call_proc_stdout(call_proc)
            self._wait_ppp_call_proc_and_handle_exit_code(call_proc)  # raises exceptions
            logger.debug(
                f'invoked pppd ({call_proc.pid}) process terminated successfully. going to find its fork')
            forked_proc = self._find_forked_proc(linkname, call_proc)
            logger.info(f'ppp forked proc found pid={forked_proc.pid}')
            conn_params = self._ex_data_from_ppp_log(call_proc_stdout_lines).values()
            self.unit_num, self.local_addr, self.remote_addr = conn_params
            logger.info(f'ppp conn opts = {conn_params}')
            self.forked_proc_pid = forked_proc.pid
            self.linkname = linkname
        finally:
            fcntl.flock(chat_file_h, fcntl.LOCK_UN)
            chat_file_h.close()
            self._connecting_currently = False

    @staticmethod
    def _read_ppp_call_proc_stdout(call_proc):
        lines = []
        while True:
            # quote from docs:
            #   readline(): Read until newline or EOF and return a single str.
            #   If the stream is already at EOF, an empty string is returned
            l_read = call_proc.stdout.readline()
            lprocessed = l_read.decode('latin1').strip('\n')
            logger.debug('pppd:' + lprocessed)
            lines.append(lprocessed)
            if len(l_read) == 0:
                break
        return lines

    def stop(self):
        self._should_run = False
        self._stopping_currently = True
        if not psutil.pid_exists(self.forked_proc_pid):
            logger.debug('ppp forked proc requested to be terminated, but it\'s not found')
        else:
            some_proc = psutil.Process(self.forked_proc_pid)
            if self.linkname in some_proc.cmdline():
                os.kill(self.forked_proc_pid, signal.SIGINT)
                while True:
                    if not psutil.pid_exists(self.forked_proc_pid):
                        break
                    logger.debug(f'pid={self.forked_proc_pid} still running. waiting.')
                    sleep(0.3)
                logger.debug(f'ppp pid={self.forked_proc_pid} terminated.')
            else:
                logger.warning('asked to terminate ppp '
                               '{self.forked_proc_pid}, found process with '
                               'such pid, but it doesn\'t have linkname in cmdline.'
                               f'same pid proc: pid={self.forked_proc_pid} '
                               f'cmdline={some_proc.cmdline()} ppid={some_proc.ppid}')
        self._stopping_currently = False
        self.linkname = None
        self.forked_proc_pid = None

    @staticmethod
    def _ex_data_from_ppp_log(lines):
        def ex(exp):
            return [re.match(string=line, pattern=exp).groups()
                    for line
                    in lines if re.match(string=line, pattern=exp)][0]

        # 'Using interface ppp0',
        # 'local  IP address 100.83.165.96',
        # 'remote IP address 10.64.64.64',
        # 'primary   DNS address 10.10.30.146',
        # 'secondary DNS address 10.10.30.150',
        ppp_n = int(ex(r'Using interface ppp(\d+)')[0])
        local = ex(r'local  IP address ((\d+\.)+\d+)')[0]
        remote = ex(r'remote IP address ((\d+\.)+\d+)')[0]
        return dict(ppp_num=ppp_n, local=local, remote=remote)

    @staticmethod
    def _rand_str(slen):
        cset = (string.ascii_letters + string.digits)
        return ''.join(random.choices(cset, k=slen))

    def _get_ch_file_path(self, ):
        carrier_part = type(self.carrier_i).__name__[:3]
        model_part = self.model_i.model_clname[:3] + self.model_i.model_clname[-5:]
        cdir = config.config['pppd']['conf_dir']
        name = f'chat_{carrier_part}_{model_part}'
        return os.path.join(cdir, name)

    def _write_chat_file(self, text):
        chat_file_path = self._get_ch_file_path()
        chat_file_h = open(chat_file_path, 'w')
        fcntl.flock(chat_file_h, fcntl.LOCK_EX)
        chat_file_h.write(text)
        chat_file_h.flush()
        # .conn() eventually closes this file
        return chat_file_h, chat_file_path

    def _remove_chat_file(self):
        if hasattr(self, 'chat_file_path') and self.chat_file_path:
            conf_path = self.chat_file_path
            if os.path.exists(conf_path):
                os.remove(conf_path)

    def __str__(self):
        tags = ['ppp']
        state = None
        try:
            state = self.get_state()
        except Exception:
            pass
        tags.append(state.name)
        if hasattr(self, 'forked_proc_pid'):
            tags.append(f'pid={self.forked_proc_pid}')
        if hasattr(self, 'unit_num'):
            tags.append(f'u={self.unit_num}')
        if hasattr(self, 'local_addr'):
            tags.append(f'l={self.local_addr}')
        if hasattr(self, 'remote_addr'):
            tags.append(f'r={self.remote_addr}')
        return ' '.join(tags)

    def __del__(self):
        pass

    def get_state(self):
        if self._should_run:
            pruns = self.check_proc_running()
            if pruns:
                return PppState.RUNNING
            else:
                if self._connecting_currently:
                    return PppState.CNNCTNG
                else:
                    return PppState.ERRNOTRNNG
        else:
            if self.forked_proc_pid:
                pruns = self.check_proc_running()
            else:
                pruns = False

            if pruns:
                if self._stopping_currently:
                    return PppState.STPPING
                else:
                    raise Exception('pppd process state tracking bug')
            else:
                return PppState.NOTHING

    def get_pid_or_none(self):
        if self.check_proc_running():
            return self.forked_proc_pid
        return None

    def check_proc_running(self):
        if self.forked_proc_pid:
            return psutil.pid_exists(self.forked_proc_pid)


def shut_down_any_related_ppp_procs():
    util.pkill(str=LINKNAME_G, signal_=signal.SIGINT, log_str='pppd')
