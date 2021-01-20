import abc
import re
from time import sleep

from logbook import Logger

import gsm_encodings
import tty_at

logger = Logger(__name__)


class CarrierNameRespParsErrProbablyNotReady(Exception): pass


class CarrierNameRespParsErr(Exception): pass


class at_modem():
    def __init__(self, unknwn_port1, unknwn_port2):
        self.main_port, self.recv_port = self._sort_out_ports(unknwn_port1, unknwn_port2)
        self.manufacturer = self._get_manufacturer()
        self.model = self._get_model()

    def _log_sorted_out_ports(self):
        logger.debug(
            f'ports sorted out for {self}: main_port = {self.main_port} recv_port = {self.recv_port}')

    def _get_model(self):
        r = tty_at.cmd_and_rcv(self.main_port, 'AT+GMM', econd_line_exprs=[b'OK'],
                               econd_line_cnt=None)
        return r[1][0]

    def _get_manufacturer(self):
        r = tty_at.cmd_and_rcv(self.main_port, 'AT+GMI', econd_line_exprs=[b'OK'],
                               econd_line_cnt=None)
        if len(r[1]) == 2:
            return r[1][0]
        return None

    def get_cgsn(self):
        r = tty_at.cmd_and_rcv(self.main_port, 'AT+CGSN', econd_line_exprs=[b'OK'],
                               econd_line_cnt=None)
        return r[1][0]

    def get_esn_imei(self):
        r = tty_at.cmd_and_rcv(self.main_port, 'AT+GSN', econd_line_exprs=[b'OK'],
                               econd_line_cnt=None)
        return r[1][0]

    def get_imsi(self):
        # international mobile subscriber identity [zte docs]
        r = tty_at.cmd_and_rcv(self.main_port, 'AT+CIMI', econd_line_exprs=[b'OK'],
                               econd_line_cnt=None)
        return r[1][0]

    def get_signal(self):
        r = tty_at.cmd_and_rcv(self.main_port, 'AT+CSQ', econd_line_exprs=[br'\+CSQ\:', ],
                               econd_line_cnt=None)
        r = r[1][0]
        t = tuple(int(x.strip()) for x in re.split('[:,]', r)[1:])
        return t[0]

    def get_carrier_poll(self, ):
        carrier_name = None
        attempt = 0
        attempt_max = 45
        while attempt <= attempt_max:
            attempt += 1
            try:
                logger.debug(f'calling get_carrier() on {self} attempt {attempt}/{attempt_max}')
                carrier_name = self.get_carrier()
                break
            except CarrierNameRespParsErrProbablyNotReady:
                logger.warning(
                    f'got CarrierNameRespParsErrProbablyNotReady from {self} attempt {attempt}/{attempt_max}')
                sleep(0.5)
        if carrier_name is None:
            msg = f"can't get carrier name from {self} after {attempt}/{attempt_max} attempts"
            logger.error(msg)
            raise Exception(msg)
        return carrier_name

    def get_carrier(self):
        ls = tty_at.cmd_and_rcv(self.main_port, 'AT+COPS?',
                                econd_line_exprs=[br'\+COPS\:', br'\+CME ERROR\:'],
                                econd_line_cnt=None)
        lline = ls[0]
        if 'CME ERROR' in lline:
            raise Exception(lline)
        if '+COPS' in lline:
            try:
                return lline.split(',')[2].replace('"', '')
            except Exception:
                raise CarrierNameRespParsErrProbablyNotReady(
                    f'can\'t parse "get carrier" responce from modem. probably modem need more time to initialise sim card {ls}')
        else:
            raise CarrierNameRespParsErr(f'can\'t parse get carrier resp from modem. {ls}')

    def sr_ussd(self, cmd, add_log_info=None):
        at_cmd = self.mk_ussd_cmd(cmd)
        logger.debug(
            f'{self} add_log_info={add_log_info} sending ussd {at_cmd} to {self.main_port}')
        tty_at.cmd_and_rcv(port_str=self.main_port,
                           cmd=at_cmd,
                           econd_line_exprs=[b'OK'],
                           econd_line_cnt=None,
                           add_log_info=add_log_info)
        logger.debug(f'{self} ussd sent {at_cmd} to main_port = {self.main_port}')
        read_wait_timeout = 10
        logger.debug(f'starting listen_for to wait ussd '
                     f'at {self.recv_port}. timeout is {read_wait_timeout}')
        line, lines = tty_at.listen_for(port_str=self.recv_port,
                                        econd_line_exprs=[br'\+CUSD\:'],
                                        to=read_wait_timeout,
                                        ret_bytes=True)
        logger.debug(f'got ussd resp {line}')
        return self.preparse_ussd_resp_at_syntax(line)

    @staticmethod
    def preparse_ussd_resp_at_syntax(full_at_str, add_log_info=None):
        m = re.match(br'\+CUSD: \d,"(\w+)"', full_at_str)
        if not m:
            raise Exception(f"can't parse ussd response={full_at_str} add_log_info={add_log_info}")
        return m.group(1)

    def __repr__(self):
        return 'at_modem ' + self.model_clname

    def read_addr_book_generic(self, enc, port_str, book="SM"):
        if enc:
            # this sets encoding
            tty_at.cmd_and_rcv(
                port_str=port_str,
                cmd=f'AT+CSCS="{enc}"',
                econd_line_exprs=[b'OK'],
                econd_line_cnt=None,
            )

        # select phonebook
        retries_max = 15
        attempt = 0
        success = False
        while attempt <= retries_max:
            attempt += 1
            try:
                tty_at.cmd_and_rcv(
                    port_str=port_str,
                    cmd=f'AT+CPBS="{book}"',
                    econd_line_exprs=[b'OK'],
                    econd_line_cnt=None,
                    econd_line_exprs_raise=[b'\\+CME\\ ERROR\\:\\ SIM\\ busy']
                )
                success = True
                break
            except tty_at.ExpectedErrExcp as e:
                logger.info(f'{self} got {e} {e.err_name} attempt {attempt} of {retries_max}')
                if e.err_name == '_CME_ERROR_SIM_busy_':
                    logger.info(f'{self} got {e} attempt {attempt} of {retries_max}')
                    sleep(0.5)
                else:
                    raise

        if not success:
            m = f'error after {attempt} of {retries_max} retries. see previous records. {self}'
            logger.info(m)
            raise Exception(m)

        # book info
        econd_line, accum_lines, all_bytes = tty_at.cmd_and_rcv(
            port_str=port_str,
            cmd='AT+CPBR=?',
            econd_line_exprs=[b'OK'],
            econd_line_cnt=None,
        )

        num1, book_capacity = re.match(r'\+CPBR: \((\d+)-(\d+)\)', accum_lines[0]).groups()

        econd_line, accum_lines, all_bytes = tty_at.cmd_and_rcv(
            port_str=port_str,
            cmd=f'AT+CPBR=1,{book_capacity}',
            econd_line_exprs=[b'OK'],
            econd_line_cnt=None,
            ret_bytes=True
        )

        entries = []
        for line in accum_lines:
            if line == b'OK':
                continue
            if line == b'+CME ERROR: invalid characters in text string':
                continue

            try:
                idx, number, obscure_int, name = self.dec_addr_book_at_resp_line(line=line)
            except Exception as e:
                logger.warn(f'can\'t decode address book entry from {self}. "{line}" {e}')
                continue
            if enc is None:
                name_dec = gsm_encodings.addrbook_name_decode_guess(name)
            elif enc == 'UCS2':
                name_dec = gsm_encodings.decode_ucspdu(name)
                pass
            else:
                raise Exception(f'encoding {enc} is not supported')
            entries.append((name_dec, number.decode('latin1') if number else None))
        return entries

    @staticmethod
    def _sort_strings_by_suffix_num(port_names_list):
        def f(e):
            return int(re.match(r'[a-zA-z/]+(\d+)', e).group(1))

        return sorted(port_names_list, key=f)

    def modem_ppp_opts(self):
        pass

    @abc.abstractmethod
    def read_addr_book(self):
        pass


class unknown_tmp_modem(at_modem):
    def _sort_out_ports(self, unk_port_1, unk_port_2):
        # just picking arbitrary ports for unknown devices
        main_port, recv_port = self._sort_strings_by_suffix_num([unk_port_1, unk_port_2])
        return main_port, recv_port


class specific_harware(at_modem):
    def __new__(cls, *args, **kwargs):
        if cls == specific_harware:
            raise Exception(f'{cls.__name__} not supposed to be instantiated')
        return object.__new__(cls)

    def __init__(self, *args, **kwargs):
        self.model_clname = self.__class__.__name__
        super(specific_harware, self).__init__(*args, **kwargs)


class zte_corporation_mf667(specific_harware):
    id_unswitched = None
    id = (0x19d2, 0x0016)

    @staticmethod
    def mk_ussd_cmd(code):
        return f'AT+CUSD=1,"{code}",15'

    def read_addr_book(self):
        return self.read_addr_book_generic(enc=None, port_str=self.main_port)

    def dec_addr_book_at_resp_line(self, line, ):
        r = re.match(br'\+ZCPBR: (\d+),"([\d+*#]+)?","(\w+)"', line)
        if r:
            idx, number, name = r.groups()
            return idx, number, None, name
        else:
            raise Exception(f"can't parse addr book entry: {line}. instance = {str(self)}")

    def _sort_out_ports(self, unk_port_1, unk_port_2):
        # <at_modem.zte_corporation_mf667 object at 0x7f2767286978>
        # sent=/dev/ttyUSB7 recv=/dev/ttyUSB7 res=+CUSD: 0,"003600310035002E0030
        # sent=/dev/ttyUSB7 recv=/dev/ttyUSB8 res=nothing
        recv_port, main_port = self._sort_strings_by_suffix_num([unk_port_1, unk_port_2])
        return main_port, recv_port

    def modem_ppp_opts(self):
        return []


class huawei_e352(specific_harware):
    id_unswitched = (0x12d1, 0x1446)
    id = (0x12d1, 0x1506)

    def read_addr_book(self):
        return self.read_addr_book_generic(enc='UCS2', port_str=self.main_port)

    @staticmethod
    def mk_ussd_cmd(code):
        code_e = gsm_encodings.encode_to_7bitpackedhex(code)
        return f'AT+CUSD=1,{code_e},15'

    def dec_addr_book_at_resp_line(self, line, ):
        r = re.match(br'\+CPBR: (\d+),"([\d+*#]+)",(\d+),"(\w+)"', line)
        if r:
            idx, number, obscure_int, name = r.groups()
            return idx, number, obscure_int, name
        else:
            raise Exception(f"can't addr book parse entry: {line}. instance = {str(self)}")

    def _sort_out_ports(self, unk_port_1, unk_port_2):
        # <at_modem.huawei_e352 object at 0x7ff8b39a5908>
        # sent=/dev/ttyUSB0 recv=/dev/ttyUSB0 res=nothing
        # sent=/dev/ttyUSB0 recv=/dev/ttyUSB1 res=+CUSD: 0,"003400350031002E0036
        main_port, recv_port = self._sort_strings_by_suffix_num([unk_port_1, unk_port_2])
        return main_port, recv_port

    def modem_ppp_opts(self):
        return []


class huawei_e3372(specific_harware):
    id_unswitched = (0x12d1, 0x14fe)
    id = (0x12d1, 0x1506)

    def read_addr_book(self):
        return self.read_addr_book_generic(enc='UCS2', port_str=self.main_port)

    @staticmethod
    def mk_ussd_cmd(code):
        code_e = gsm_encodings.encode_to_7bitpackedhex(code)
        return f'AT+CUSD=1,{code_e},15'

    def dec_addr_book_at_resp_line(self, line, ):
        r = re.match(br'\+CPBR: (\d+),"([\d+*#]+)",(\d+),"(\w+)"', line)
        if r:
            idx, number, obscure_int, name = r.groups()
            return idx, number, obscure_int, name
        else:
            raise Exception(f"can't addr book parse entry: {line}. instance = {str(self)}")

    def _sort_out_ports(self, unk_port_1, unk_port_2):
        recv_port, main_port = self._sort_strings_by_suffix_num([unk_port_1, unk_port_2])
        return main_port, recv_port

    def modem_ppp_opts(self):
        return []


class huawei_technolo_e3372(huawei_e3372): pass


class huawei_e1550(specific_harware):
    id_unswitched = (0x12d1, 0x1446)
    id = (0x12d1, 0x1003)

    def read_addr_book(self):
        return self.read_addr_book_generic(enc='UCS2', port_str=self.main_port)

    @staticmethod
    def mk_ussd_cmd(code):
        code_e = gsm_encodings.encode_to_7bitpackedhex(code)
        return f'AT+CUSD=1,{code_e},15'

    def dec_addr_book_at_resp_line(self, line, ):
        # asterisk char is in ([\d+\+\*#]*) because
        # there are entries without number, with just name
        r = re.match(br'\+CPBR: (\d+),"([\d+*#]*)",(\d+),"(\w+)"', line)
        if r:
            idx, number, obscure_int, name = r.groups()
            return idx, number, obscure_int, name
        else:
            raise Exception(f"can't addr book parse entry: {line}. instance = {str(self)}")

    def _sort_out_ports(self, unk_port_1, unk_port_2):
        # <at_modem.huawei_e1550 object at 0x7fc0b67bb9b0>
        # sent=/dev/ttyUSB9 recv=/dev/ttyUSB9 res=nothing
        # sent=/dev/ttyUSB9 recv=/dev/ttyUSB10 res=+CUSD: 0,"00360031002E00300030
        main_port, recv_port = self._sort_strings_by_suffix_num([unk_port_1, unk_port_2])
        return main_port, recv_port


def format_model_clname(manufacturer, model):
    import re
    model = re.sub('\\W', '_', f"{manufacturer[:15]}_{model}")
    model = re.sub('_{2,}', '_', model)
    model = model.lower()
    return model


def get_all_models():
    return specific_harware.__subclasses__()
