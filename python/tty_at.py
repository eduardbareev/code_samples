import os
import re
import termios
import time

import serial
from logbook import Logger
from serial import SerialException

from util import lsof

logger = Logger(__name__)


class AtRespReadErr(Exception): pass


class PortInUseAccToLsof(Exception): pass


class ExpectedErrExcp(Exception): pass


s_ports = {}


def get_s_port(port_str, add_info=None, **kwargs):
    if port_str in s_ports:
        raise Exception(f'{port_str} is in use')
    if lsof([port_str]):
        raise PortInUseAccToLsof('port_str is use according to lsof')

    tr = 0
    maxtr = 3
    s = None
    while tr <= maxtr:
        tr += 1
        try:
            s = serial.Serial(port_str, **kwargs)
            break
        except (SerialException, termios.error) as e:
            logger.warning(
                f'got exception {e} for {port_str} with kwargs={kwargs}. attempt {tr}/{maxtr} retrying')

    if s is None:
        m = f'can\'t open port {port_str} with kwargs={kwargs} after {tr} retries'
        logger.error(m)
        raise Exception(m)
    if tr > 1:
        logger.info(f'successfully open port {port_str} with kwargs={kwargs} after {tr} retries')

    s_ports[port_str] = {'add_info': add_info, 's_port': s}
    return s


def release_s_port(port_str):
    s_ports[port_str]['s_port'].close()
    del s_ports[port_str]


def try_ports(ports, per_port_timeout=3):
    # determing talkable ports
    tlk_ports = []
    msgs = {}
    set_log_str = ",".join([os.path.basename(x) for x in ports])
    for p in ports:
        pname = os.path.basename(p)
        try:
            s_cmd(port_str=p, cmd='AT', timeout=1, dont_log_anything=True)
            cmd_and_rcv(port_str=p, cmd='AT', econd_line_exprs=[b'OK'], total_to=per_port_timeout,
                        econd_line_cnt=None, add_log_info='try_ports', dont_log_anything=True)
            msgs[pname] = f'AT -> OK'
            tlk_ports.append(p)
        except AtRespReadErr as e:
            msgs[pname] = f'AT -> no OK during {per_port_timeout}s: {e}'
        except BrokenPipeError as e:
            msgs[pname] = f'AT -> excp {e}'
    if len(tlk_ports) == 0:
        logger.warning(
            f'try_ports: no working ports found (len(tlk_ports) == 0). set={set_log_str}')
    else:
        report = '; '.join([f'{pn}: {msg}' for pn, msg in msgs.items()])
        logger.debug(f"try_ports: {report} set={set_log_str}")
    return tlk_ports


def s_cmd(port_str, cmd, timeout=1, add_log_info=None, trunc_dgb_log=False,
          dont_even_try_read=False, dont_log_anything=False):
    cmd = cmd.strip('\n\r')
    cmd_snd_b = bytes(cmd + '\r', 'latin1')
    s_port = get_s_port(port_str, dsrdtr=True, rtscts=True)
    s_port.read_all()

    snd_log_str = f'sent {cmd_snd_b} to {port_str} {add_log_info if add_log_info else ""}'

    s_port.timeout = timeout
    try:
        s_port.write(cmd_snd_b)
        s_port.flush()
    except Exception as e:
        if not dont_log_anything:
            logger.debug(f's_cmd: got excp {e} after {snd_log_str}')
        raise

    # dont_even_try_read is for commands like "AT+CFUN=1,1"
    if dont_even_try_read:
        if not dont_log_anything:
            logger.debug(f's_cmd: not trying to read after {snd_log_str}')
    else:
        s_port.timeout = timeout
        rd = s_port.read(10000000)
        rd_shrt = rd[:50] if trunc_dgb_log else rd
        if not dont_log_anything:
            logger.debug(f's_cmd: {snd_log_str}. got >{rd_shrt}<  during {timeout}s')
    release_s_port(port_str)


def cmd_and_rcv(port_str,
                cmd,
                econd_line_exprs,
                econd_line_cnt,
                total_to=1,
                add_log_info=None,
                ret_bytes=False,
                econd_line_exprs_raise=None,
                dont_log_anything=False):
    # econd_line_cnt = None # read stops after first one
    # econd_line_cnt = -1   # reads all until timeout
    # econd_line_cnt = N    # reads N lines
    cmd = cmd.strip('\n\r')
    cmd_snd_b = bytes(cmd + '\r', 'latin1')
    add_log_info_str = f' ({add_log_info})' if add_log_info else ''
    sent_log_str = f'sent {cmd_snd_b} {port_str}{add_log_info_str}.'

    if econd_line_exprs is not None and (econd_line_cnt is not None):
        raise Exception('either econd_line_exprs or econd_line_cnt should be not none')
    if econd_line_exprs is econd_line_cnt is None:
        raise Exception('should provide econd_line_exprs or econd_line_cnt')

    s_port = get_s_port(port_str, dsrdtr=True, rtscts=True)

    def end_cond(ec_curr_line_bytes):
        if econd_line_exprs_raise is not None:
            for e in econd_line_exprs_raise:
                if re.match(e, ec_curr_line_bytes):
                    v = re.sub(r'[^\w]+', '_', ec_curr_line_bytes.decode('latin1'))
                    e = ExpectedErrExcp(
                        f'{sent_log_str} got {ec_curr_line_bytes}. listed in econd_line_exprs_raise')
                    e.err_name = v
                    release_s_port(port_str)
                    raise e

        if econd_line_exprs is not None:
            for e in econd_line_exprs:
                if re.match(e, ec_curr_line_bytes):
                    return True
            return False
        elif econd_line_cnt is not None:
            if econd_line_cnt == len(lines_accum):
                return True
            return False
        else:
            raise Exception('some error')

    def add_line(bs):
        if ret_bytes:
            stripline = bs.rstrip(b'\n\r')
        else:
            stripline = bs.decode('latin1').rstrip('\n\r')
        if lines_rd_cnt == 0 and ((stripline == cmd) or (stripline == cmd.encode('latin1'))):
            return
        if 0 == len(stripline):
            return
        lines_accum.append(stripline)

    def discard_read_all():
        s_port.read_all()

    discard_read_all()

    s_port.timeout = 0.5
    s_port.write(cmd_snd_b)

    s_port.timeout = 0.5
    curr_line_bytes = b''
    all_bytes = b''
    lines_accum = []

    lines_rd_cnt = 0
    start = time.time()
    total_timeout = total_to

    while True:
        if (time.time() - start) >= total_timeout:
            release_s_port(port_str)
            raise AtRespReadErr(
                f"cmd_and_rcv: can't finish read. {sent_log_str}. all rcvd:>{all_bytes}<")
        b = s_port.read(1)
        curr_line_bytes += b
        all_bytes += b
        if len(curr_line_bytes) > 1 and (curr_line_bytes[-2:] == b'\r\n'):
            add_line(curr_line_bytes)
            lines_rd_cnt += 1
            if end_cond(curr_line_bytes):
                discard_read_all()
                if not dont_log_anything:
                    logger.debug(
                        f'cmd_and_rcv: {sent_log_str} got {(lines_accum[-1], lines_accum, all_bytes)}')
                release_s_port(port_str)
                return lines_accum[-1], lines_accum, all_bytes
            else:
                curr_line_bytes = b''


def listen_for(port_str, econd_line_exprs, to, add_log_info=None, ret_bytes=False):
    logger.debug(f'listen_for. expecting {econd_line_exprs}, to={to}, add_log_info={add_log_info}')

    def prep_line(_bytes):
        if ret_bytes:
            return curr_line_bytes.rstrip(b'\n\r')
        else:
            return curr_line_bytes.decode('latin1').rstrip('\n\r')

    def end_cond(ec_curr_line_bytes):
        for e in econd_line_exprs:
            if re.match(e, ec_curr_line_bytes):
                logger.debug(
                    f'listen_for consumed line {ec_curr_line_bytes} matches read {e} end condition, add_log_info={add_log_info}')
                return True
        logger.debug(
            f'listen_for consumed line {ec_curr_line_bytes} skipped, add_log_info={add_log_info}')
        return False

    def add_line(bs):
        stripline = prep_line(bs)

        if '' == stripline:
            return
        lines_accum.append(stripline)

    s_port = get_s_port(port_str, dsrdtr=True, rtscts=True)

    s_port.timeout = 0.5
    curr_line_bytes = b''
    all_bytes = b''
    lines_accum = []

    lines_rd_cnt = 0
    start = time.time()
    total_timeout = to

    while True:
        time_spent = time.time() - start
        if time_spent >= total_timeout:
            release_s_port(port_str)
            raise Exception(f"listen_for() can't finish because of "
                            f"timeout. total_timeout={total_timeout} "
                            f"time_spent={time_spent}. all "
                            f"bytes read = {all_bytes}. , add_log_info={add_log_info}")
        b = s_port.read(1)
        curr_line_bytes += b
        all_bytes += b
        if len(curr_line_bytes) > 1 and (curr_line_bytes[-2:] == b'\r\n'):
            add_line(curr_line_bytes)
            lines_rd_cnt += 1
            if end_cond(curr_line_bytes):
                release_s_port(port_str)
                return lines_accum[-1:][0], lines_accum
            else:
                curr_line_bytes = b''
