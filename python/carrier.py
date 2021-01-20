import abc


class carrier():
    @abc.abstractmethod
    def carrier_ppp_opts(self): pass

    @abc.abstractmethod
    def carrier_ppp_chat_conf(self): pass

    @abc.abstractmethod
    def int_net_addr_pingable(self): pass


class beeline(carrier):
    id_keys = ('beeline',)

    def __init__(self):
        self.balance_ussd_code = '*102#'

    def carrier_ppp_opts(self):
        return ['user', 'beeline', 'password', 'beeline']

    def carrier_ppp_chat_conf(self):
        return {'number': '*99#', 'apn': 'internet.beeline.ru', }

    def int_net_addr_pingable(self):
        return ['moskva.beeline.ru', 'balance.beeline.ru', 'my.beeline.ru']


class megafon(carrier):
    id_keys = ('megafon',)

    def __init__(self):
        self.balance_ussd_code = '*100#'

    def carrier_ppp_opts(self):
        return ['user', 'gdata', ]

    def carrier_ppp_chat_conf(self):
        return {'number': '*99#', 'apn': 'internet', }

    def int_net_addr_pingable(self):
        return ['moscow.megafon.ru', 'samara.shop.megafon.ru', 'lk.megafon.ru']


class mts_rus(carrier):
    id_keys = ('mts_rus',)

    def __init__(self):
        self.balance_ussd_code = '*100#'

    def carrier_ppp_opts(self):
        return ['user', 'mts', 'password', 'mts']

    def carrier_ppp_chat_conf(self):
        return {'number': '*99#', 'apn': 'internet.mts.ru', }

    def int_net_addr_pingable(self):
        return ['payment.mts.ru', 'mts.ru', 'internet.mts.ru', 'login.mts.ru']


class tele2(carrier):
    id_keys = ('tele2', '25020')

    def __init__(self):
        self.balance_ussd_code = '*105#'

    def carrier_ppp_opts(self):
        return ['user', 'tele2', 'password', 'tele2']

    def carrier_ppp_chat_conf(self):
        return {'number': '*99#', 'apn': 'internet.tele2.ru', }

    def int_net_addr_pingable(self):
        return ['tele2.ru', 'login.tele2.ru', 'shop.tele2.ru']


def match_carrier_by_id(id_):
    cs = carrier.__subclasses__()
    m = [x for x in cs if id_ in x.id_keys]
    return m[0] if m else None
