import collections
import datetime
import enum
import functools
import re
import threading
import time
import traceback
from time import sleep
from typing import Dict

from logbook import Logger

import at_modem
import carrier
import cell_equipment
import conn_check
import conn_item
import ppp
import sysfsdev
import tty_at
import ums
import util
from config import config

logger = Logger(__name__)


class SlotState(enum.Enum):
    FOUND = enum.auto()
    EMPTY = enum.auto()
    SWITCHING = enum.auto()
    INITIALIZING = enum.auto()
    INITIALIZING_FAILED = enum.auto()
    READY = enum.auto()
    SL_ESTABLISHING = enum.auto()
    SL_ESTATTEMPTFAIL = enum.auto()
    SL_RUNNING = enum.auto()
    SL_DISCONNECTING = enum.auto()
    SL_ERRCONNLOST = enum.auto()
    RESETTING = enum.auto()
    RESET_FAILED = enum.auto()


def _locks_or_skip_decor(func):
    def decorated(self, *args, **kwrgs):
        acquired = self._lock(addinfo=func.__name__, blocking=False)
        if not acquired:
            return
        try:
            return func(self, *args, **kwrgs)
        finally:
            self._release(addinfo=func.__name__)

    functools.update_wrapper(wrapper=decorated, wrapped=func)
    return decorated


def _locks_decor(func):
    def decorated(self, *args, **kwrgs):
        self._lock(addinfo=func.__name__)
        try:
            return func(self, *args, **kwrgs)
        finally:
            self._release(addinfo=func.__name__)

    functools.update_wrapper(wrapper=decorated, wrapped=func)
    return decorated


class hardware_slot():
    failed_conn_attmpt_t = collections.namedtuple('failed_conn_attmpt_t',
                                                  ['conseq_failed_conn_attmps_cnt',
                                                   'retr_failed_conn_attmp',
                                                   'dt', ])

    def _lock(self, addinfo, blocking=True, timeout=-1):
        log = False
        requested_timeout = timeout
        th_id = threading.get_ident()
        if blocking:
            if requested_timeout and requested_timeout > 0:
                # non-infinite wait
                r = self.lock.acquire(blocking, requested_timeout)
                if r:
                    self.busy_state = (True, th_id, addinfo)
                    if log:
                        logger.debug(
                            f'{addinfo} th{th_id} successfully (blocking (timeout={requested_timeout}) mode requested by caller) acquired lock for {self.sfs_id}')
                else:
                    logger.debug(
                        f'{addinfo} th{th_id} failed (blocking (timeout={requested_timeout}) mode requested by caller) to acquire lock for {self.sfs_id}')
                return r
            else:
                # infinite wait
                d_timeout = 2
                while True:
                    r = self.lock.acquire(blocking, d_timeout)
                    if r:
                        self.busy_state = (True, th_id, addinfo)
                        if log:
                            logger.debug(
                                f'{addinfo} th{th_id} successfully (infinite blocking mode requested by caller) acquired lock for {self.sfs_id}')
                        return r
                    else:
                        logger.warning(
                            f'{addinfo} th{th_id} FAILED (infinite blocking mode requested by caller) to acquire lock within {d_timeout}s for {self.sfs_id}')
                        pass
        else:
            if requested_timeout and requested_timeout > 0:
                raise Exception('It is forbidden to specify a timeout when blocking is false.')
            r = self.lock.acquire(blocking)
            if r:
                self.busy_state = (True, th_id, addinfo)
                if log:
                    logger.debug(
                        f'{addinfo} th{th_id} successfully (non-blocking mode requested by caller) acquired lock for {self.sfs_id}')
            else:
                if log:
                    logger.debug(
                        f'{addinfo} th{th_id} failed (non-blocking mode requested by caller) to acquire lock for {self.sfs_id}')
            return r

    def _release(self, addinfo):
        log = False
        th_id = threading.get_ident()
        self.lock.release()
        self.busy_state = (False, None, None)
        if log:
            logger.debug(f'{addinfo} th{th_id} released lock for {self.sfs_id}')
        return None

    def __str__(self):
        return self.sfs_id

    def __init__(self, sysfs_id):
        self.sfs_id = sysfs_id
        self.lock = threading.Lock()
        self.busy_state = (False, None, None)
        self.failed_conn_attmpt = None
        self._new_or_reallocated_slot()

    def _new_or_reallocated_slot(self):
        self.sfs_obj = sysfsdev.sfsdev(self.sfs_id)
        self.status = None
        self._set_status(SlotState.FOUND)
        self.netw_conn_status = conn_check.ChannelNetConnState.NA
        self.netw_conn_probing = False
        self.cell_equip: cell_equipment.cell_equipment = None
        self.modem_i: at_modem.specific_harware = None
        self.carrier_i: carrier.carrier = None
        self.conn_item = None
        self._switch_pending = self._needs_switch(self.sfs_obj.id_vendor, self.sfs_obj.id_product)

        self.failed_conn_attmpt = None
        self.netw_conn_status_dt = None

    def _reallocated(self):
        self._new_or_reallocated_slot()

    def _gone(self):
        logger.info(f'slot {self}. _gone()')
        self._set_status(SlotState.EMPTY)
        if hasattr(self, 'conn_item') and self.conn_item:
            self.conn_item.on_exit_shutdown()
        self.conn_item = None
        self.cell_equip = None
        self.modem_i = None
        self.carrier_i = None
        self.failed_conn_attmpt = None

    @staticmethod
    def _needs_switch(id_vendor, id_product):
        all_unswitched_ids_tuples = [x.id_unswitched for x
                                     in at_modem.get_all_models()
                                     if x.id_unswitched is not None]

        return (id_vendor, id_product) in all_unswitched_ids_tuples

    @_locks_or_skip_decor
    def tick(self):
        def dev_gone_or_sl_reallocated():
            p = self.sfs_obj.present()
            if self.status == SlotState.EMPTY:
                if p:
                    logger.info(f'{self.sfs_id} reallocated by some device')
                    self._reallocated()
                    return True
            else:
                if not p:
                    logger.info(f'device in {self.sfs_id} gone. ({self.status} -> absent)')
                    self._gone()
                    return True
            return False

        def scheduled_auto_reconnect():
            if self.status == SlotState.SL_RUNNING:
                if self.conn_item.is_time_for_sched_reconn():
                    logger.info(f'{self} going to be reconnected (planned)')
                    self.failed_conn_attmpt = self.failed_conn_attmpt_t(
                        0, True, datetime.datetime.now().astimezone())
                    self._reconn()
                    return True
            return False

        def check_actual_sess_state():
            if self.status == SlotState.SL_RUNNING:
                if self.conn_item.is_conn_lost():
                    self._set_status(SlotState.SL_ERRCONNLOST)
                    logger.warning(f'{self} connection lost')
                    return True
            if self.status == SlotState.SL_ERRCONNLOST:
                self.failed_conn_attmpt = self.failed_conn_attmpt_t(
                    0, True, datetime.datetime.now().astimezone())
                logger.warning(f'{self} found in SL_ERRCONNLOST. '
                               f'attempting to reconnect now. {self.failed_conn_attmpt}')
                self._reconn()
                return True
            return False

        def failed_reconn_retry():
            cfg_conseq_failed_conn_attmps_retr_cnt = config['conseq_failed_conn_attmps_retr_cnt']
            cfg_conseq_failed_conn_attmps_retr_interval = config[
                'conseq_failed_conn_attmps_retr_interval']

            if self.status != SlotState.SL_ESTATTEMPTFAIL:
                return False

            if self.failed_conn_attmpt and \
                    (not self.failed_conn_attmpt.retr_failed_conn_attmp):
                return False

            if self.failed_conn_attmpt and \
                    (self.failed_conn_attmpt.conseq_failed_conn_attmps_cnt
                     == cfg_conseq_failed_conn_attmps_retr_cnt):
                self.failed_conn_attmpt = self.failed_conn_attmpt._replace(
                    retr_failed_conn_attmp=False)
                logger.warning(f'{self} exceeded reconnection retry attempts '
                               f'leaving in SL_ESTATTEMPTFAIL. '
                               f'{self.failed_conn_attmpt}')
                return False

            if self.failed_conn_attmpt and self.failed_conn_attmpt.retr_failed_conn_attmp:
                now = datetime.datetime.now().astimezone()
                last_retr_secs_ago = (now - self.failed_conn_attmpt.dt).total_seconds()
                if last_retr_secs_ago < cfg_conseq_failed_conn_attmps_retr_interval:
                    return False

                logger.warning(f'{self} retry reconnecting '
                               f'{self.failed_conn_attmpt} '
                               f'last_retr_secs_ago={last_retr_secs_ago}')
                self._reconn()
            return False

        def upd_netw_connectivity_status():
            if self.status != SlotState.SL_RUNNING:
                return False

            return self._upd_netw_connectivity_status()

        if dev_gone_or_sl_reallocated(): return
        if check_actual_sess_state(): return
        if scheduled_auto_reconnect(): return
        if failed_reconn_retry(): return
        if upd_netw_connectivity_status(): return

    def _upd_netw_connectivity_status(self):
        self.carrier_i: carrier.carrier
        if 0 == len(self.carrier_i.int_net_addr_pingable()):
            return False

        interv = datetime.timedelta(
            seconds=config['connectivity_check_interval_seconds']
        )

        now = datetime.datetime.now().astimezone().replace(microsecond=0)

        itstime = (self.netw_conn_status_dt is None) \
            or ((now - self.netw_conn_status_dt) > interv)

        if itstime:
            logger.info(f'{self} _upd_netw_connectivity_status: '
                        f'going to probe connectivity')
            try:
                self.netw_conn_probing = True
                self.netw_conn_status = \
                    conn_check.channel_connectivity_check_procedure(self.conn_item)
            except Exception as e:
                logger.error(f'{self} error during conn chk. excp: {e} {traceback.format_exc()}')
            finally:
                self.netw_conn_status_dt = now
                self.netw_conn_probing = False
            return True
        return False

    @_locks_decor
    def on_app_shutdown(self):
        logger.info(f'slot {self}. on_app_shutdown')
        if hasattr(self, 'conn_item') and self.conn_item:
            self._disconnect()

    @_locks_decor
    def manual_disconnect(self):
        logger.info(f'slot {self}. manual disconnect command')
        self._check_in_state('init()', (SlotState.SL_RUNNING,))
        self._disconnect()

    def _disconnect(self):
        self._set_status(SlotState.SL_DISCONNECTING)
        if self.conn_item:
            self.conn_item.shutdown()
            self.conn_item = None
        self._set_status(SlotState.READY)

    def get_state_view(self):
        def port_info_str(any_ports, tlk, main, recv):
            if not any_ports or (len(any_ports) == 0):
                return ''

            def num(s):
                if s is None:
                    return None
                if type(s) == int:
                    return s
                return int(re.search('\\d+', s).group(0))

            any_ports = [num(p) for p in any_ports]
            tlk = [num(p) for p in (tlk if tlk else [])]
            main = num(main)
            recv = num(recv)
            ps = []
            for p in any_ports:
                t = '-'
                if p in tlk:
                    t = 'a'
                if main == p:
                    t = 'm'
                if recv == p:
                    t = 'r'
                ps.append((t, p))
            ps = sorted(ps)
            return ','.join([str(p) + t for t, p in ps])

        dev_lock_info = self.busy_state

        e = {
            'busy': dev_lock_info[0],
            'lock_info': (dev_lock_info[0], dev_lock_info[1], str(dev_lock_info[2])),
            'status': self.status.name,
            'netw_check_result': self.netw_conn_status.name,
            'netw_conn_probing': self.netw_conn_probing,
            'sfs_id': self.sfs_id,
            'ums_pending': self._switch_pending,
            'dbg_props': util.dbg_attrs(self),
        }

        if self.cell_equip:
            e['cell_equip'] = {
                'model_clname': self.cell_equip.model_clname,
                'carrier_clname': self.cell_equip.carrier_clname,
                'cgsn': self.cell_equip.cgsn,
                'esn_imei': self.cell_equip.esn_imei,
                'imsi': self.cell_equip.imsi,
                'ownn_stored': self.cell_equip.ownn_stored,
            }
        else:
            e['cell_equip'] = None

        def time_interval(td: datetime.timedelta):
            if td is None:
                return None
            return ':'.join(str(td).split(':')[:-1])

        def dt_str(dt):
            if dt is None:
                return None
            return dt.strftime('%d.%m.%Y %H:%M:%S')

        if self.status != SlotState.EMPTY:
            usb_dev_info = {'sysfs_path': self.sfs_id,
                            'ports_info_1': self.sfs_obj.ttys,
                            'product': self.sfs_obj.product,
                            'manufacturer': self.sfs_obj.manufacturer,
                            'busnum': self.sfs_obj.busnum,
                            'devnum': self.sfs_obj.devnum}
            e['usb_dev_info'] = usb_dev_info
            e['ports_info'] = port_info_str(
                any_ports=self.sfs_obj.ttys or [],
                tlk=self.t_tty_names if self.modem_i else [],
                main=self.modem_i.main_port if self.modem_i else None,
                recv=self.modem_i.recv_port if self.modem_i else None,
            )
            if self.conn_item:
                e['conn_br_dbg_props'] = util.dbg_attrs(self.conn_item)
                e['sim_data'] = util.dbg_attrs(self.conn_item.sim_data)
                e['routing_table'] = self.conn_item.routing_tab_num
                e['ppp'] = {
                    'state': self.conn_item.ppp.get_state().name
                }
                e['ch_status'] = self.conn_item.get_status().name
                if self.conn_item.ppp.get_state() == ppp.PppState.RUNNING:
                    e['ppp']['local_addr'] = self.conn_item.ppp.local_addr
                    e['ppp']['remote_addr'] = self.conn_item.ppp.remote_addr
                    e['ppp']['unit_num'] = self.conn_item.ppp.unit_num
                    e['ppp']['forked_proc_pid'] = self.conn_item.ppp.forked_proc_pid
                    e['ppp']['linkname'] = self.conn_item.ppp.linkname
                if self.conn_item.get_status() == conn_item.ChState.CH_RUNNING:
                    e['curl_snippets'] = self.conn_item.get_test_cmds()
                    e['curr_conn_est_dt'] = dt_str(self.conn_item.curr_conn_est_dt)
                    e['curr_conn_time_ago_str'] = time_interval(self.conn_item.conn_uptime())
                if self.conn_item.get_status() == conn_item.ChState.CH_RUNNING:
                    e['till_sched_reconn'] = time_interval(self.conn_item.till_sched_reconn())
                    e['is_time_for_sched_reconn'] = self.conn_item.is_time_for_sched_reconn()

                    pass
        else:
            e['usb_dev_info'] = None
            e['ports_info'] = None
        return e

    def _set_status(self, status):
        if self.status:
            old = self.status.name
        else:
            old = 'None'
        logger.info(f'slot {self} {old} -> {status.name}')
        self.status = status
        if not self.status == SlotState.SL_RUNNING:
            self.netw_conn_status = conn_check.ChannelNetConnState.NA

    def _ports_are_here(self):
        if self.sfs_obj.ttys and (len(self.sfs_obj.ttys) > 1):
            # logger.debug()
            return True
        else:
            logger.error(f'{self} wrong ports count = {len(self.sfs_obj.ttys)}')
            return False

    def _switch_if_need(self):
        if not self._switch_pending:
            return
        self._set_status(SlotState.SWITCHING)
        ums.usbmodeswitch(self.sfs_obj.full_path)
        self.sfs_obj.reload()
        if not self._ports_are_here():
            raise Exception(f'{self.sfs_id} have no ports after usbmodeswitch')
        self._switch_pending = False

    @_locks_decor
    def init(self, reset_first=False):
        logger.info(f'external init(). reset_first={reset_first}')
        self._check_in_state('init()', (SlotState.INITIALIZING_FAILED,
                                        SlotState.FOUND,
                                        SlotState.SL_ESTATTEMPTFAIL))
        try:
            if reset_first:
                self._reset()
                # sometimes it needs some more time
                sleep(2)
            self._set_status(SlotState.INITIALIZING)
            self._init()
        except Exception:
            self._set_status(SlotState.INITIALIZING_FAILED)
            raise

    def _working_ports(self, per_port_timeout=5):
        self.sfs_obj.reload()
        any_ports = self.sfs_obj.ttys

        if any_ports is None:
            logger.debug(f'{self} have no ports at all in sysfs. ')
            return []
        else:
            logger.debug(f"all {self} ports ({len(any_ports)}): "
                         f"{','.join(any_ports)}")
            tlk_ports = tty_at.try_ports(any_ports, per_port_timeout=per_port_timeout)

            if len(tlk_ports) == 0:
                logger.debug(f'{self} have no working tty ports. (out of {len(any_ports)}) ')
                return []
            logger.debug(f'{self} working ports ({len(any_ports)}): {tlk_ports} ')
            return tlk_ports

    def _init(self):
        self._switch_if_need()
        # _switch_if_need leaves state in "swithching"
        self._set_status(SlotState.INITIALIZING)

        if not self._ports_are_here():
            raise Exception(f'{self.sfs_id} have no ports')

        t_tty_names = self._working_ports(per_port_timeout=3)
        if len(t_tty_names) != 2:
            m = f'can\'t init {self} because wrong number of working ports {len(t_tty_names)}'
            logger.error(m)
            raise Exception(m)

        tmp_modem_obj = at_modem.unknown_tmp_modem(*t_tty_names)

        if tmp_modem_obj.manufacturer:
            manufacturer_p = tmp_modem_obj.manufacturer
        else:
            manufacturer_p = self.sfs_obj.id_vendor_text
        model_p = tmp_modem_obj.model
        model_clname = at_modem.format_model_clname(manufacturer_p, model_p)
        tmp_modem_obj.model_clname = model_clname

        carrier_r_name = tmp_modem_obj.get_carrier_poll()
        carrier_key = re.sub('\\W+', '_', carrier_r_name).lower()

        logger.debug(f'determined equipment for {self}: '
                     f'model_clname={model_clname} '
                     f'carrier_r_name={carrier_r_name} '
                     f'carrier_key={carrier_key} ')
        modem_i: at_modem.specific_harware = vars(at_modem)[model_clname](*t_tty_names)

        carrier_class = carrier.match_carrier_by_id(carrier_key)
        if not carrier_class:
            m = f"can't instantiate carrier type. " \
                f"carrier_r_name={carrier_r_name} " \
                f"carrier_key={carrier_key}"
            logger.error(m)
            raise Exception(m)

        carrier_i: carrier.carrier = carrier_class()

        cell_equipment_i = cell_equipment.cell_equipment(modem_i, carrier_i)
        self.t_tty_names = t_tty_names
        self.modem_i, self.carrier_i, self.cell_equip = \
            modem_i, carrier_i, cell_equipment_i
        self._set_status(SlotState.READY)

    @_locks_decor
    def get_signal(self):
        return self.cell_equip.get_signal()

    @_locks_decor
    def get_balance(self):
        return self.cell_equip.get_balance()

    def _est(self):
        self._check_in_state('est()', (SlotState.READY, SlotState.SL_ESTATTEMPTFAIL))
        logger.info(f'{self} establising connection')
        try:
            if config['reset_on_establish']:
                self._reset()
                # leaving it for few seconds after reset
                from time import sleep
                sleep(2)
                self._init()
            self._set_status(SlotState.SL_ESTABLISHING)
            br = conn_item.conn_item(self.sfs_id, self.cell_equip)
            br.ch_est()

            self.conn_item = br
            self._set_status(SlotState.SL_RUNNING)
            logger.info(f'{self} connection established successfully')
            self.failed_conn_attmpt = None
        except Exception:
            logger.warning(f'{self} failed to establish connection')
            self._set_status(SlotState.SL_ESTATTEMPTFAIL)
            if self.failed_conn_attmpt:
                self.failed_conn_attmpt = self.failed_conn_attmpt._replace(
                    conseq_failed_conn_attmps_cnt=
                    1 + self.failed_conn_attmpt.conseq_failed_conn_attmps_cnt,
                    dt=datetime.datetime.now().astimezone(),
                )
                logger.info(f'{self} updated failed_conn_attmpt: {self.failed_conn_attmpt}')
            raise

    def _reconn(self):
        logger.info(f'{self} _reconn() reconnecting {self.failed_conn_attmpt}')
        self._disconnect()
        try:
            self._est()
        except Exception as e:
            logger.warning(f'{self} reconnect failed {e}')
            raise

    @_locks_decor
    def est(self):
        logger.info(f'{self} est() external call')
        self.failed_conn_attmpt = None
        self._est()

    def _check_in_state(self, action_name: str, state_tuple: tuple):
        state_names = '/'.join([n.name for n in state_tuple])
        if self.status not in state_tuple:
            raise Exception(f'{self.sfs_id} {self.__class__.__name__} '
                            f'{action_name} can only be performed on '
                            f'{state_names}. current status={self.status.name}')

    def _reset_at_cfun(self, tlk_ports_before):
        logger.info(f'{self} is going to be power-cycled using AT command')

        tty_at.s_cmd(tlk_ports_before[0],
                     'AT+CFUN=1,1',
                     timeout=5,
                     dont_even_try_read=True)

        # we have to keep holding lock acquired by
        # calling method to avoid device tree polling
        # running in another thread to call tick() and find
        # out that device is gone and further call tick() again
        # to find it come back). likely  polling thread currently
        # already entered tick() and waiting for lock to release

        logger.debug(f'sent power-cycle AT command to {self}. '
                     f'waiting it do disappear')

        # modem received power-cycle command but
        # expected to remain visible in the system
        # for few seconds

        def poll_state_change(target_presence, timeout, tick_sleep=0.5):
            start = time.time()
            while True:
                psc_time_spent = round(time.time() - start, 1)
                psc_present = self.sfs_obj.present()
                if psc_present:
                    self.sfs_obj.reload()
                    psc_ports = self.sfs_obj.ttys
                else:
                    psc_ports = None
                if (psc_present == target_presence) or psc_time_spent >= timeout:
                    return psc_present, psc_ports, psc_time_spent
                time.sleep(tick_sleep)

        present, ports, time_spent = poll_state_change(False, 30)

        if not present:
            logger.debug(f'modem {self} gone during power-cycle as '
                         f'expected at {time_spent} second ')
        else:
            m = f'modem {self} expected to go offline but still found ' \
                f'in system after {time_spent} seconds. ' \
                f'reset attempt failed'
            logger.error(m)
            raise Exception(m)

        # now waiting it to became online

        present, ports, time_spent = poll_state_change(True, 30)

        if present:
            logger.debug(f'modem {self} came back during power-cycle '
                         f'as expected at {time_spent} second ')
        else:
            m = f'modem {self} expected to come back ' \
                f'but still offline after {time_spent} seconds. ' \
                f'reset attempt failed'
            logger.error(m)
            raise Exception(m)

        self.sfs_obj.reload()

        self._switch_pending = self._needs_switch(self.sfs_obj.id_vendor,
                                                  self.sfs_obj.id_product)
        self._switch_if_need()

        self.sfs_obj.reload()

        tlk_ports_after = tty_at.try_ports(self.sfs_obj.ttys, per_port_timeout=5)

        if len(tlk_ports_after) > 1:
            logger.info(f'modem {self} sucessfully reset '
                        f"tlk_ports_before={','.join(tlk_ports_before)} "
                        f"tlk_ports_after={','.join(tlk_ports_after)}")
        else:
            m = f'resetting modem {self} failed. ' \
                f"ports after reset attempt: " \
                f" ({len(tlk_ports_after)}) {','.join(tlk_ports_after)} "
            logger.error(m)
            raise Exception(m)

    @_locks_decor
    def reset(self):
        self._reset()
        self._init()

    def _reset(self):
        self._check_in_state('reset()', (SlotState.READY,
                                         SlotState.FOUND,
                                         SlotState.INITIALIZING_FAILED,
                                         SlotState.SL_ESTATTEMPTFAIL,))
        self._set_status(SlotState.RESETTING)

        self.cell_equip = None
        self.modem_i = None
        self.carrier_i = None
        self.conn_item = None

        logger.info(f'reset procecdure for {self}')
        if not self.sfs_obj.present():
            m = f'{self} absent from sys fs. can\'t reset because ' \
                f'system doesn\'t knows about device'
            logger.error(m)
            self._set_status(SlotState.RESET_FAILED)
            raise Exception(m)
        tlk_ports_before = self._working_ports()
        if len(tlk_ports_before) == 0:
            m = f'{self} have no working tty ports. ' \
                f'can\'t reset using AT CFUN because nowhere ' \
                f'to send AT command'
            logger.error(m)
            self._set_status(SlotState.RESET_FAILED)
            raise Exception(m)
        try:
            self._reset_at_cfun(tlk_ports_before)
        except Exception:
            self._set_status(SlotState.RESET_FAILED)
            raise
        logger.info(f'{self} _reset considers power-cyclying successful')
        self._set_status(SlotState.FOUND)


class hardware_group():
    def __init__(self):
        self.equipment: Dict[str, hardware_slot] = {}

    def init(self):
        self.reread_discover()

    def reread_discover(self):
        devs = sysfsdev.read_sysfs()
        for d in devs:
            id_product = int(d['idProduct'], 16) if d['idProduct'] else None
            id_vendor = int(d['idVendor'], 16) if d['idVendor'] else None
            sysfs_id = d['id']
            if not self._dev_match(id_product, id_vendor):
                continue

            already_known = self.equipment.get(sysfs_id)
            if already_known is not None:
                # already_known.on_device_found_in_sysfs()
                pass
            else:
                logger.info(f'new device found {sysfs_id}')
                self.equipment[sysfs_id] = hardware_slot(sysfs_id)

        for d in self.equipment.values():
            d.tick()

    def on_app_shutdown(self):
        for d in self.equipment.values():
            d.on_app_shutdown()

    def get_state_view(self):
        return [d.get_state_view() for d in self.equipment.values()]

    @staticmethod
    def _dev_match(id_product, id_vendor):
        all_ids_tuples = [y for x in at_modem.get_all_models()
                          for y in [x.id_unswitched, x.id]]
        return (id_vendor, id_product) in all_ids_tuples


def shutdown_all_channels_on_exit():
    equipment.on_app_shutdown()


equipment = hardware_group()
