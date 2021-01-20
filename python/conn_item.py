import datetime
from enum import Enum

from logbook import Logger

import cell_equipment
import config
import db
import ppp
import prx
import routing_tables
import util

logger = Logger(__name__)


class ChState(Enum):
    CH_NOTHING = 1
    CH_STPPING = 2
    CH_RUNNING = 4
    CH_ERRNOTRNNGPPPTERM = 5
    CH_ESTABLISHING = 3
    CH_ESTATTEMPTFAIL = 6


class conn_item:
    def __init__(self, sfs_id, cell_equip: cell_equipment.cell_equipment):
        self.sfs_id = sfs_id
        self.cell_equip: cell_equipment.cell_equipment = cell_equip
        self.ppp: ppp.ppp_sess = self._create_ppp_inst()
        self.sim_data = self._load_sim_data()
        self.status = ChState.CH_NOTHING
        self.routing_tab_num = None

    def _set_status(self, status):
        if self.status:
            old = self.status.name
        else:
            old = 'None'
        logger.info(f'conn_item {self} {old} -> {status.name}')
        self.status = status

    def _load_sim_data(self):
        imsi = int(self.cell_equip.imsi)
        conn = db.get_conn('conn_item during init')
        sd = db.load_sim_data(sn=imsi, conn=conn)
        if sd is None:
            logger.debug(f'no sim data by {imsi}. first seen')
            max_port = db.find_sim_free_port(conn=conn)
            if max_port is None:
                max_port = 3000
            pref_port = max_port + 1
            params = dict(sn=imsi,
                          remark=None,
                          port=pref_port,
                          carrier=self.cell_equip.carrier_clname,
                          reconn_minutes=120)
            sd = db.sim_data(**params)
            logger.info(f'creating new sim_data obj {params}')
            db.store_sim_data(sd=sd, conn=conn)
            sd = db.load_sim_data(sn=imsi, conn=conn)
        logger.info(f'sim data: {sd.sn} {sd.port}')
        return sd

    def gone(self):
        pass

    @staticmethod
    def _date():
        return datetime.datetime.now().replace(microsecond=0).astimezone()

    def _create_ppp_inst(self):
        # lock must be already released, since this should be called from constructor
        logger.debug(f'starting ppp {self}')
        ps = ppp.ppp_sess(
            model_i=self.cell_equip.modem_model_i,
            carrier_i=self.cell_equip.carrier,
            sysfs_id=self.sfs_id
        )
        return ps

    def __str__(self):
        tags = ['ch',
                self.sfs_id,
                util.shoten(self.cell_equip.model_clname),
                util.shoten(self.cell_equip.carrier_clname),
                ]
        if hasattr(self, 'sim_data'):
            tags.append(f'{self.sim_data.port}')
        if hasattr(self, 'ppp'):
            tags.append(f'({self.ppp})')
        return ' '.join(tags)

    def on_exit_shutdown(self):
        if self.status != ChState.CH_RUNNING:
            return
        self._set_status(ChState.CH_STPPING)
        self._shutdown()
        self._set_status(ChState.CH_NOTHING)

    def _shutdown(self):
        logger.debug('_shutdown for {self}')
        self.curr_conn_est_dt = None
        if self.ppp.get_state() in (ppp.PppState.RUNNING, ppp.PppState.ERRNOTRNNG):
            logger.debug(f'stopping ppp {self}')
            self.ppp.stop()
            logger.debug(f'ppp stopped {self}')
        self._rollback_routing()
        if hasattr(prx, 'prx_i'):
            p: prx.three_proxy = prx.prx_i
            try:
                # port may not be actually served
                p.rm_by_port(self.sim_data.port)
            except Exception:
                pass
        self.routing_tab_num = None

    def shutdown(self):
        self._check_in_state('manual_shutdown', (ChState.CH_RUNNING,))
        self._set_status(ChState.CH_STPPING)
        self._shutdown()
        self._set_status(ChState.CH_NOTHING)

    def _rollback_routing(self):
        if self.routing_tab_num:
            logger.debug(f'removing routing {self} = tab={self.routing_tab_num}')
            rs = routing_tables.rpdb_rules()
            rs.del_by(tab_del=self.routing_tab_num)
            table_i = routing_tables.tab_rules(self.routing_tab_num)
            table_i.empty()

    def _set_up_routing(self):
        tb_nums_range = range(*config.config['tables_range'])
        self.routing_tab_num = routing_tables.pick_num_for_new_table(tb_nums_range)
        logger.debug(f'table num for {self} = {self.routing_tab_num}')
        rs = routing_tables.rpdb_rules()
        rs.add(self.ppp.local_addr, self.routing_tab_num)
        table_i = routing_tables.tab_rules(self.routing_tab_num)
        table_i.add(f'default via {self.ppp.remote_addr} dev ppp{self.ppp.unit_num}')

    def ch_est(self, simulate_fail=None):
        self._check_in_state('ch_est()', (ChState.CH_ESTATTEMPTFAIL, ChState.CH_NOTHING))
        try:
            self._set_status(ChState.CH_ESTABLISHING)
            self._ch_est(simulate_fail=simulate_fail)
            self._set_status(ChState.CH_RUNNING)
        except Exception:
            self._set_status(ChState.CH_ESTATTEMPTFAIL)
            raise

    def _ch_est(self, simulate_fail=None):
        try:
            self.curr_conn_est_dt = None
            logger.info(f'establishing channel {self}')
            self.ppp.conn(simulate_fail=simulate_fail)
            self._set_up_routing()
            self._upd_proxy()
            logger.info(f'established sucessfully conn_br {self}')
            self.curr_conn_est_dt = self._date()
        except Exception:
            self._rollback_routing()
            raise

    def _upd_proxy(self):
        if not hasattr(prx, 'prx_i') or (not prx.prx_i):
            prx.prx_i = prx.three_proxy()
        prx_i: prx.three_proxy = prx.prx_i
        comment = f'{self.cell_equip} ' \
                  f'ppp {self.ppp.get_pid_or_none()} ' \
                  f'unit {self.ppp.unit_num} ' \
                  f'local {self.ppp.local_addr} ' \
                  f'remote {self.ppp.remote_addr}' \
                  f'table {self.routing_tab_num}'
        prx_i.add(self.sim_data.port, self.ppp.local_addr, comment)
        prx_i.start()  # starts or does nothing if already running

    def is_conn_lost(self):
        if self.status != ChState.CH_RUNNING:
            raise Exception(f'{self} check_actual_sess_state only '
                            f'applicable for CH_RUNNING. '
                            f'current staus = {self.status}')
        ppp_state = self.ppp.get_state()
        if ppp_state == ppp.PppState.ERRNOTRNNG:
            return True
        return False

    def get_status(self):
        return self.status

    def is_time_for_sched_reconn(self):
        return self.till_sched_reconn() <= datetime.timedelta(0)

    def till_sched_reconn(self):
        self.schd_reconn_every = datetime.timedelta(
            minutes=int(self.sim_data.reconn_minutes)
        )
        now_dt = datetime.datetime.now().astimezone().replace(microsecond=0)
        diff = now_dt - self.curr_conn_est_dt
        return self.schd_reconn_every - diff

    def conn_uptime(self):
        now_dt = datetime.datetime.now().astimezone().replace(microsecond=0)
        return now_dt - self.curr_conn_est_dt

    def get_test_cmds(self):
        if not self.cell_equip.carrier.int_net_addr_pingable():
            return None
        carrier_internal_ping_addr = self.cell_equip.carrier.int_net_addr_pingable()[0]
        local_ping = f'ping -I {self.ppp.local_addr} -W5 -w5 {carrier_internal_ping_addr}'
        local_http = f'curl -D- -o/dev/null --interface {self.ppp.local_addr} http://{carrier_internal_ping_addr}/'
        prx_i = prx.prx_i
        proxy_http = f'curl -D- -o/dev/null ' \
                     f'--socks5 ' \
                     f'{prx_i.conf_dict["conn_user"]}' \
                     f':{prx_i.conf_dict["conn_passwd"]}' \
                     f'@{prx_i.in_iface}' \
                     f':{self.sim_data.port} http://{carrier_internal_ping_addr}/'
        return dict(local_ping=local_ping, local_http=local_http, proxy_http=proxy_http)

    def _check_in_state(self, action_name: str, state_tuple: tuple):
        state_names = '/'.join([n.name for n in state_tuple])
        if self.status not in state_tuple:
            raise Exception(f'{self.sfs_id} {self.__class__.__name__} '
                            f'{action_name} can only be performed on '
                            f'{state_names}. current status={self.status.name}')
