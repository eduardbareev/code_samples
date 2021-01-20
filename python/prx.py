import fcntl
import os
import signal
import subprocess

from logbook import Logger

import util
from config import config

logger = Logger(__name__)
STATIC_STR_FOR_CMD_GREP = '_prxstaticstr'


class three_proxy():
    def __init__(self, ):
        self.conf_dict = config['3proxy']
        self.in_iface = self.conf_dict['in_iface']
        self.ports_n_out_ifaces = []
        self.last_feed_config_text = None
        self.proc = None

    def add(self, in_port, out_iface, comment):
        if in_port in [p for p, i, c in self.ports_n_out_ifaces]:
            raise Exception('proxy port {p} already assigned')
        self.ports_n_out_ifaces.append((in_port, out_iface, comment))
        if self.proc:
            self._reload_conf()

    def rm_by_port(self, port):
        el_rm = next(filter(lambda x: x[0] == port, self.ports_n_out_ifaces), None)
        if not el_rm:
            raise Exception(f'port {port} not served by proxy')
        logger.debug(f'removing port channel {el_rm} from proxy')
        self.ports_n_out_ifaces.remove(el_rm)
        if self.proc:
            self._reload_conf()
        # note already established connections are continue
        # to be supported by 3proxy

    def _reload_conf(self):
        self._w_conf_start()
        logger.debug(f'telling 3proxy to reload config')
        self.proc.send_signal(signal.SIGUSR1)
        self._w_conf_release()

    @staticmethod
    def get_preamble():
        subs = {k: v for k, v
                in config['3proxy'].items()
                if k in ['conn_user', 'conn_passwd']}
        substituted = 'auth strong\nusers {conn_user}:CL:{conn_passwd}'.format(**subs)

        literal_additions_path = config['prx_verbatim_conf_path']
        literal_additions = ''
        if os.path.exists(literal_additions_path):
            f = open(literal_additions_path, 'r')
            text = f.read()
            text = text.strip('\n')
            f.close()
            literal_additions = f'\n# from {literal_additions_path}\n' \
                                f'{text}\n' \
                                f'# end from {literal_additions_path}'
        return substituted + literal_additions

    def get_ifaces_conf_text(self):
        line_tpl = '# {comment}:\nsocks -n -p{port} -i{in_iface} -e{out_iface}'
        conf_text = '\n\n'.join([line_tpl.format(
            port=port,
            out_iface=out_iface,
            comment=comment,
            in_iface=self.in_iface)
            for port, out_iface, comment
            in self.ports_n_out_ifaces])
        return conf_text

    def get_conf_text(self):
        preamble = self.get_preamble()
        ifaces_conf_text = self.get_ifaces_conf_text()
        sep = '#' * 50
        return self.dedent_lines(f"""\
            {sep}
            {preamble}
            {sep}
            {ifaces_conf_text}
            {'':#<50}
        """) + '\n'

    @staticmethod
    def _get_conf_path():
        return config['3proxy']['conf_path'] + STATIC_STR_FOR_CMD_GREP

    def start(self, ok_already_started=True):
        if ok_already_started:
            if self.proc:
                return
        else:
            if self.proc:
                raise Exception('3proxy already started')
        self._w_conf_start()
        args = ['/usr/bin/3proxy', self._get_conf_path()]
        self.proc = subprocess.Popen(args=args)
        logger.debug(
            f'3proxy started pid={self.proc.pid} with {self._get_conf_path()}. >{" ".join(args)}< ')
        self._w_conf_release()

    def stop(self):
        conf_path = self._get_conf_path()
        if os.path.exists(conf_path):
            os.remove(conf_path)
        if self.proc:
            pid = self.proc.pid
            logger.debug(f'terminating 3prox pid {pid}')
            self.proc.terminate()
            logger.debug(f'wait() {pid}')
            s = self.proc.wait()
            logger.debug(f'returned from wait() of {pid}: {s}')

    @staticmethod
    def dedent_lines(s):
        ls = [line.strip('\n\t\r ') for line in s.splitlines()]
        t = '\n'.join(ls)
        return t

    def running(self):
        return self.proc is not None

    def _w_conf_start(self):
        self.conf_file_h = open(self._get_conf_path(), 'w')
        fcntl.flock(self.conf_file_h, fcntl.LOCK_EX)
        self.conf_file_h.write(self.get_conf_text())
        self.conf_file_h.flush()

    def _w_conf_release(self):
        fcntl.flock(self.conf_file_h, fcntl.LOCK_UN)
        self.conf_file_h.close()


def shut_down_any_related_prx_procs():
    util.pkill(str=STATIC_STR_FOR_CMD_GREP, signal_=signal.SIGTERM, log_str='proxy')
