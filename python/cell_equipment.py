import math

from logbook import Logger

import at_modem
import carrier
import gsm_encodings

logger = Logger(__name__)


class cell_equipment():
    def __init__(self, modem: at_modem.specific_harware, carrier_: carrier.carrier):
        self.modem_model_i: at_modem.at_modem = modem
        self.carrier: carrier.carrier = carrier_
        self.model_clname = self.modem_model_i.__class__.__name__
        self.carrier_clname = carrier_.__class__.__name__

        self.cgsn = self.modem_model_i.get_cgsn()
        self.esn_imei = self.modem_model_i.get_esn_imei()
        self.imsi = self.modem_model_i.get_imsi()  # international mobile subscriber identity [zte docs]

        self.addr_book = self.modem_model_i.read_addr_book()
        try:
            self.ownn_stored = [u for a, u in self.addr_book if a == 'NUM'][0]
        except IndexError:
            self.ownn_stored = None

    def __str__(self):
        return f'equipment({self.model_clname}+{self.carrier_clname})'

    def get_balance(self):
        r = self.modem_model_i.sr_ussd(self.carrier.balance_ussd_code)
        return gsm_encodings.decode_ucspdu(r)

    def get_signal(self):
        s = self.modem_model_i.get_signal()
        return s, math.floor((int(s) / 31) * 100)
