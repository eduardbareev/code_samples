import random
import re
import subprocess
import time
from enum import Enum, auto

from logbook import Logger

import config
import conn_item
from config import config

logger = Logger(__name__)


class ChannelNetConnState(Enum):
    OK_FULL_ACCESS = auto()
    PARTIAL_CONNECTIVITY = auto()
    TOTAL_CONN_LOSS = auto()
    NA = auto()


def channel_connectivity_check_procedure(channel, simulate_total_conn_loss=None):
    start = time.time()
    channel: conn_item.conn_item = channel

    logger.debug(f'connectivity check for {channel}')
    src_if_addr = channel.ppp.local_addr

    non_routeables = [
        # RFC5737
        '192.0.2.1',
        '198.51.100.1',
        '203.0.113.1',
    ]

    if not simulate_total_conn_loss:
        carrier_resources = channel.cell_equip.carrier.int_net_addr_pingable()
        _global_addresses = global_addresses
    else:
        carrier_resources = non_routeables
        _global_addresses = non_routeables

    netw_test_res = examine(network_if=src_if_addr,
                            carrier_resources=carrier_resources,
                            internet_resources_pool=_global_addresses)
    partial_connectivity, inet_reachable_ssl_ok_no_mitm, tests = netw_test_res

    logger.debug(f'trace= {debug_log_str_from_tests(tests)} {channel}')

    _map = {
        (True, True): ChannelNetConnState.OK_FULL_ACCESS,
        (True, False): ChannelNetConnState.PARTIAL_CONNECTIVITY,
        (False, False): ChannelNetConnState.TOTAL_CONN_LOSS,
    }

    state = _map[(partial_connectivity, inet_reachable_ssl_ok_no_mitm)]
    spent = round(time.time() - start, 1)
    logger.debug(f'part...={partial_connectivity}, '
                 f'...no_mitm={inet_reachable_ssl_ok_no_mitm} '
                 f'state={state.name}. took {spent}s {channel}')

    return state


def examine(network_if, carrier_resources, internet_resources_pool):
    class test_type(Enum):
        INTERNET_PING = auto()
        INTERNET_PLAIN_HTTP = auto()
        INTERNET_SSL_HTTP = auto()
        CARRIER_PING = auto()
        CARRIER_PLAIN_HTTP = auto()

    performed_tests = []

    def test_rec(r_test_type, r_resource, test_result):
        performed_tests.append(
            (r_test_type, r_resource, test_result.result_code, test_result.test_took_s))

    timeout_ext_ping = config['conn_chk']['timeout_ext_ping']
    timeout_ext_ping_no_ans = config['conn_chk']['timeout_ext_ping_no_ans']
    timeout_carrier_ping = config['conn_chk']['timeout_carrier_ping']
    timeout_carrier_ping_no_ans = config['conn_chk']['timeout_carrier_ping_no_ans']
    timeout_ext_http = config['conn_chk']['timeout_ext_http']
    timeout_carrier_http = config['conn_chk']['timeout_carrier_http']
    timeout_ext_ssl = config['conn_chk']['timeout_ext_ssl']

    # check we have either ping or plain http access
    # to at least one of three random external
    # resources (break on first success of either ping or plain http)
    _internet_ping_or_plain_http_succeed = False
    for resource in random.choices(internet_resources_pool, k=3):
        ping_t = ping(resource, src=network_if, timeout=timeout_ext_ping,
                      timeout_no_ans=timeout_ext_ping_no_ans)
        # logger.debug(f'inet ping: {ping_t.result_code.name}')
        test_rec(test_type.INTERNET_PING, resource, ping_t)
        if ping_t.result_code == PingResultCode.OK:
            _internet_ping_or_plain_http_succeed = True
            break
        else:
            http_t = http(f'http://{resource}/', src=network_if, timeout=timeout_ext_http)
            # logger.debug(f'inet http: {plain_t.result_code.name}')
            test_rec(test_type.INTERNET_PLAIN_HTTP, resource, http_t)
            if http_t.result_code == HttpTestResultCode.OK:
                _internet_ping_or_plain_http_succeed = True
                break
    if _internet_ping_or_plain_http_succeed:
        # if yes,
        # declare partial_connectivity=True
        partial_connectivity = True
        # then try external http+ssl (don't check plain http or ping)
        # (assuming they can't fake http ssl cert)
        # (break on first success successfull HTTP+SSL request)
        _at_least_some_ssl_succeed = False
        for resource in random.choices(internet_resources_pool, k=3):
            ssl_t = http(f'https://{resource}/', src=network_if, timeout=timeout_ext_ssl)
            # logger.debug(f'inet httpS: {ssl_t.result_code.name}')
            test_rec(test_type.INTERNET_SSL_HTTP, resource, ssl_t)
            if ssl_t.result_code == HttpTestResultCode.OK:
                # if at least one successfull http+ssl request performed
                # declare everything is great and break out whole test procudure
                # set inet_reachable_ssl_ok_no_mitm=True
                # leave partial_connectivity=True
                _at_least_some_ssl_succeed = True
                break
        # if in the course of three attempts, no single successfull httP+SSL
        # request was perfomed (all three yielded SSL _or other_ errors )
        # conclude that they apparently intercept traffic and exit from test procedure
        # there is also no need to test if we have access to carrier
        # network resources. set inet_reachable_ssl_ok_no_mitm=False
        inet_reachable_ssl_ok_no_mitm = _at_least_some_ssl_succeed
    else:
        # if no (no single ping or plain http request to the internet succeed)
        # try reach carrier owned resources by both ping and plain
        # http (no need to test ssl).
        # stop trying after first success  (be it ping or plain http)
        _ping_or_plain_http_to_carrier_succeed = False
        for carrier_resource in carrier_resources:
            ping_t = ping(carrier_resource, src=network_if, timeout=timeout_carrier_ping,
                          timeout_no_ans=timeout_carrier_ping_no_ans)
            # logger.debug(f'ping carrier: {ping_t.result_code.name}')
            test_rec(test_type.CARRIER_PING, carrier_resource, ping_t)
            if ping_t.result_code == PingResultCode.OK:
                _ping_or_plain_http_to_carrier_succeed = True
                break
            else:

                http_t = http(f'http://{carrier_resource}/', src=network_if,
                              timeout=timeout_carrier_http)
                # logger.debug(f'http carrier: {plain_t.result_code.name}')
                test_rec(test_type.CARRIER_PLAIN_HTTP, carrier_resource, http_t)
                if http_t.result_code == HttpTestResultCode.OK:
                    _ping_or_plain_http_to_carrier_succeed = True
                    break
        if _ping_or_plain_http_to_carrier_succeed:
            # if there is at least some connectivity with carrier network
            # set partial_connectivity=True
            # set inet_reachable_ssl_ok_no_mitm=False
            # (because internet is obviously inreachable given
            # http tests failed)
            # exit
            inet_reachable_ssl_ok_no_mitm = False
            partial_connectivity = True
        else:
            # if no, all ways to connect all known carrier servers
            # failed (resources x (ping,http) loop finished)
            # declare partial_connectivity=False (which probably means
            # ppp-session broken) and exis from test procedure.
            # left inet_reachable_ssl_ok_no_mitm=False (since there
            # is essentially no connection at all).
            # set partial_connectivity=False
            partial_connectivity = False
            inet_reachable_ssl_ok_no_mitm = False
    return partial_connectivity, inet_reachable_ssl_ok_no_mitm, performed_tests


class PingResultCode(Enum):
    OK = 1
    NO_RESPONSE = 2
    OTHER_ERROR = 3


from collections import namedtuple

PingResult = namedtuple('PingResult',
                        ['result_code', 'transmitted', 'received', 'stdout', 'stderr', 'ping_time',
                         'test_took_s'])


def ping(host, src=None, timeout=2, timeout_no_ans=2, cnt=1) -> PingResult:
    src_ars = ['-I', src] if src else []
    args = ['ping', *src_ars, '-w', str(timeout), '-W', str(timeout_no_ans), '-c', str(cnt), host]
    # logger.debug(f"ping cmd: {' '.join(args)}")
    start = time.time()
    result = subprocess.run(args, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=False)
    took = time.time() - start
    transmitted, received = None, None
    stdout = result.stdout.decode('utf8')
    stderr = result.stderr.decode('utf8')
    delays = []
    if result.returncode in (0, 1):
        for line in stdout.splitlines():
            m = re.match(r'(\d+) packets transmitted, (\d+) received', line)
            if m:
                transmitted, received = m.groups()
            if result.returncode == 0:
                m = re.search(r'time=([\d.]+)', line)
                if m:
                    delays.append(float(m.group(1)))
    code = {
        0: PingResultCode.OK,
        2: PingResultCode.OTHER_ERROR,
        1: PingResultCode.NO_RESPONSE,
    }[result.returncode]
    return PingResult(code, transmitted, received, stdout, stderr, delays, took)


class HttpTestResultCode(Enum):
    SSL_ERR = 1
    OK = 2
    CNNORRDERR = 3  # read timeout, too many redirects, "connection error", ChunkedEncodingError
    OTHER_ERROR = 4


HttpTestResult = namedtuple('HttpTestResult', ['result_code', 'exception', 'test_took_s'])


def http(url, src=None, timeout=2) -> HttpTestResult:
    import requests
    from requests.adapters import HTTPAdapter
    from requests.packages.urllib3.poolmanager import PoolManager
    class SourceAddressAdapter(HTTPAdapter):
        def __init__(self, source_address, **kwargs):
            self.source_address = source_address
            super(SourceAddressAdapter, self).__init__(**kwargs)

        def init_poolmanager(self, connections, maxsize, block=False):
            self.poolmanager = PoolManager(num_pools=connections,
                                           maxsize=maxsize,
                                           block=block,
                                           source_address=self.source_address)

    req_sess = requests.Session()
    if src:
        req_sess.mount('http://', SourceAddressAdapter((src, 0)))
        req_sess.mount('https://', SourceAddressAdapter((src, 0)))
    headers = {
        'User-Agent': 'Mozilla/5.0 (iPhone; CPU iPhone OS 11_0 like Mac OS X) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Mobile/15A372 Safari/604.1',
        'Accept-Encoding': 'gzip, deflate, br',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Connection': 'close',
        'Accept-Language': "en-US,en;q=0.5",
        'Referer': "https://www.google.ru/",
    }
    start = time.time()
    try:
        req_sess.get(url, timeout=timeout, allow_redirects=False, headers=headers)
        dur = time.time() - start
        return HttpTestResult(HttpTestResultCode.OK, None, dur)
    except requests.exceptions.SSLError as e:
        dur = time.time() - start
        return HttpTestResult(HttpTestResultCode.SSL_ERR, e, dur)
    except (requests.exceptions.ReadTimeout,
            requests.exceptions.ConnectionError,
            requests.exceptions.TooManyRedirects,
            requests.exceptions.ChunkedEncodingError) as e:
        dur = time.time() - start
        return HttpTestResult(HttpTestResultCode.CNNORRDERR, e, dur)
    except Exception as e:
        dur = time.time() - start
        return HttpTestResult(HttpTestResultCode.OTHER_ERROR, e, dur)


def load_global_addresses():
    return open(config.top_sites_file_path).read().splitlines()


def debug_log_str_from_tests(tests):
    test_results_strs = {
        PingResultCode.OK: 'OK',
        PingResultCode.NO_RESPONSE: 'FAIL',
        PingResultCode.OTHER_ERROR: 'FAIL',
        HttpTestResultCode.SSL_ERR: 'FAIL',
        HttpTestResultCode.OK: 'OK',
        HttpTestResultCode.CNNORRDERR: 'FAIL',
        HttpTestResultCode.OTHER_ERROR: 'FAIL',
    }

    t_strs = [f'{t_type.name}:{resourse}:{round(test_took_s, 1)}:{test_results_strs[result_code]}'
              for t_type, resourse, result_code, test_took_s in tests]
    t_str = '; '.join(t_strs)
    return t_str


global_addresses = load_global_addresses()
