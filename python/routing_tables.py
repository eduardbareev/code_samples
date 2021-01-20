import collections
import re
import subprocess
from enum import Enum

from logbook import Logger

import config

logger = Logger(__name__)


class tables_file():
    class Line(Enum):
        COMMENT = 1
        ENTRY = 2
        EMPTY = 3

    def __init__(self, path='/etc/iproute2/rt_tables'):
        self.path = path
        fh = open(self.path, 'r')
        text = fh.read()
        fh.close()
        lines = []
        nums = []
        tabs = []
        for line in text.splitlines():
            lstrp = line.strip()
            if lstrp == '':
                lines.append((tables_file.Line.EMPTY, None, None))
            elif lstrp.startswith('#'):
                lines.append((tables_file.Line.COMMENT, lstrp, None))
            else:
                m = re.match(r'(\d+)\s+([\w._]+)(\s*#?.*)', lstrp)
                if m:
                    num, tab, trailing_comment = m.groups()
                    num = int(num)
                    if num in nums:
                        raise Exception(f'error while parsing rt_tables. duplicate number {num}')
                    nums.append(num)
                    if tab in tabs:
                        raise Exception(
                            f'error while parsing rt_tables. duplicate table name {tab}')
                    tabs.append(tab)
                    lines.append((tables_file.Line.ENTRY, lstrp, (num, tab, trailing_comment)))
                else:
                    break
        self.lines = lines

    def mk_text(self):
        fls = []
        for line in self.lines:
            ltype, original, data = line
            if ltype == tables_file.Line.ENTRY:
                if original is not None:
                    fls.append(original)
                else:
                    fls.append(f'{data[0]}    {data[1]}')
            elif ltype == tables_file.Line.EMPTY:
                fls.append('')
            elif ltype == tables_file.Line.COMMENT:
                fls.append(original)
            else:
                raise Exception('something is wrong')
        return '\n'.join(fls) + '\n'

    def get_entries(self):
        return {d[0]: d[1] for t, o, d in self.lines if t == tables_file.Line.ENTRY}

    def add(self, num, tabname, pick_num_range=None):
        entries = self.get_entries()
        exising_nums = entries.keys()
        exising_tabs = entries.values()
        if num is None:
            if pick_num_range is None:
                raise Exception('provide "pick_num_range" parameter when not providing "num"')
            free_nums = set(pick_num_range) - set(exising_nums)
            if 0 == len(free_nums):
                raise Exception('no free nums')
            num = min(free_nums)
        if tabname in exising_tabs:
            raise Exception('table {tabname} already exists')
        if num in exising_nums:
            raise Exception('entry with id {num} already exists')
        self.lines.append((tables_file.Line.ENTRY, None, (num, tabname, None)))

    def _rm(self, tab_name_to_delete=None, num_to_delete=None):
        entries = self.get_entries()
        existsing_tabs = entries.values()
        existsing_nums = entries.keys()
        if (tab_name_to_delete is not None) and tab_name_to_delete not in existsing_tabs:
            raise Exception(f'{tab_name_to_delete} not found in table')
        if (num_to_delete is not None) and num_to_delete not in existsing_nums:
            raise Exception(f'{tab_name_to_delete} not found in table')
        for v in self.lines:
            ltype, orig_line, data = v
            if data:
                num, tab, trailing_comment = data
            else:
                num, tab, trailing_comment = (None, None, None)
            if ((tab_name_to_delete is not None) and (tab == tab_name_to_delete)) or \
                    ((num_to_delete is not None) and (num == num_to_delete)):
                self.lines.remove(v)
                break

    def rm_by_tab_name(self, tab_name):
        self._rm(tab_name_to_delete=tab_name)

    def rm_by_num(self, num):
        self._rm(num_to_delete=num)

    def save(self):
        text = self.mk_text()
        fh = open(self.path, 'w')
        fh.write(text)

    def exists(self, table):
        entries = self.get_entries()
        existsing_tabs = entries.values()
        return table in existsing_tabs


class rpdb_rules():
    def __init__(self, skip_dup_chk=False):
        if not skip_dup_chk:
            self.check_dup()

    @staticmethod
    def get_rules():
        outp = subprocess.check_output(['ip', 'rule', 'show']).decode('latin1')
        ls = outp.splitlines()
        return [(y, z.strip()) for y, z in [(x.split(':')) for x in ls]]

    def check_dup(self):
        self.get_rules()
        rules = self.get_rules()
        cond_col = [cond for num, cond in rules]
        dups = ([x for x, occ_cnt in collections.Counter(cond_col).items() if occ_cnt > 1])
        if len(dups):
            raise Exception(
                f'there is more than one occurence of following rpdb rules (ip rule): {dups}')

    def parse_cond(self, cond):
        src, tab = re.match(r'from ([\w.\d]+) lookup ([\w.\d]+)', cond).groups()
        tab = self.numeric(tab)
        return src, tab

    @staticmethod
    def numeric(whatever):
        try:
            return int(whatever)
        except ValueError:
            return whatever

    def match(self, cond, src_del=None, tab_del=None, func_true_on_match=None):
        tab_del = self.numeric(tab_del)
        if None is src_del is tab_del is func_true_on_match:
            raise Exception('either src, tab or func param sould be provided')
        src, tab = self.parse_cond(cond)
        if src_del and (src != src_del):
            return False
        if tab_del and (tab != tab_del):
            return False
        if func_true_on_match and (not func_true_on_match(src, tab)):
            return False
        return True

    def del_by(self, src_del=None, tab_del=None, func_true_on_match=None):
        """
        | rp.del_by(func_true_on_match=lambda s,t: t in range(399,400+1))
        """
        for num, cond in self.get_rules():
            if not self.match(cond, src_del, tab_del, func_true_on_match):
                continue
            src, tab = self.parse_cond(cond)
            args = ['ip', 'rule', 'delete', 'from', src, 'table', str(tab)]
            logger.debug(f'deleting ip rule {" ".join(args)}')
            subprocess.check_call(args)

    def add(self, src, tab):
        """
        | rp.add('8.8.8.8', 351)
        """
        if self.exists(src, tab):
            raise Exception(f'can\'t add {src} {tab}. already exists')
        args = ['ip', 'rule', 'add', 'from', src, 'table', str(tab)]
        subprocess.check_call(args)

    def exists(self, src=None, tab=None):
        """
        | rp.exists(tab=300)
        """
        for num, cond in self.get_rules():
            if self.match(cond, src, tab):
                return True
        return False

    def get_all_numeric_tables_mentioned(self):
        nums = []
        table_ids_including_text_aliases = [self.parse_cond(cond)[1] for someint, cond in
                                            self.get_rules()]
        for text in table_ids_including_text_aliases:
            try:
                numeric = int(text)
                nums.append(numeric)
            except ValueError:
                pass
        return nums


class tab_rules():
    def __init__(self, tab: int):
        """
        | t_rules = tab_rules(300)
        """
        self.tab_num = tab

    def add(self, cmd_text: str):
        """
        | t_rules = tab_rules(300)
        | t_rules.add('default via 10.64.64.64 dev ppp0')
        """
        args = ['ip', 'route', 'add', 'table', str(self.tab_num)]
        args.extend(cmd_text.split())
        subprocess.check_call(args)

    def empty(self):
        args = ['ip', 'route', 'flush', 'table', str(self.tab_num)]
        logger.debug(f'issuing {" ".join(args)}')
        subprocess.check_call(args)


def get_table_ids_from_active_routes():
    out = subprocess.check_output(['ip', 'route', 'show', 'table', 'all'])
    out = out.decode('latin1')
    ls = out.splitlines()
    nums = []
    for line in ls:
        m = re.search(r'table (\d+)', line)
        if m:
            nums.append(int(m.group(1)))
    return nums


def get_all_table_numbers_reserved_or_in_use(dont_inc_from_file_and_system=False):
    tf = tables_file()
    from_file = list(tf.get_entries().keys())
    from_routes = get_table_ids_from_active_routes()
    rpdb_rs = rpdb_rules(skip_dup_chk=True)
    from_rpdb_rules = rpdb_rs.get_all_numeric_tables_mentioned()

    if dont_inc_from_file_and_system:
        taken = set(from_routes + from_rpdb_rules)
        taken -= {255, 254, 253, 0}  # reserved or managed by os
        taken -= set(from_file)
    else:
        taken = set(from_routes + from_rpdb_rules + from_file)
        taken |= {255, 254, 253, 0}  # reserved or managed by os

    return taken


def pick_num_for_new_table(nrange, also_exclude=None):
    taken = get_all_table_numbers_reserved_or_in_use()
    if also_exclude:
        taken.update(also_exclude)
    for n in nrange:
        if n not in taken:
            return n
    raise Exception('can\'t come up with number sice all taken or range too narrow')


def flush_everyting_within_managed_range():
    def fm(v):
        if isinstance(v, range):
            return f'{v.start}-{v.stop - 1}'
        elif isinstance(v, set) or isinstance(v, filter):
            return ','.join([str(x) for x in v]) if len(v) > 0 else 'empty'
        else:
            return str(v)

    tb_nums_range = range(*config.config['tables_range'])
    all_active = get_all_table_numbers_reserved_or_in_use()

    # numbers that are in use (except listed in rt_tables file and system ones)
    # so we believe we can delete such tables
    active_minus_protected = get_all_table_numbers_reserved_or_in_use(
        dont_inc_from_file_and_system=True)

    overlap = set(filter(lambda x: x in tb_nums_range, active_minus_protected))
    if len(overlap):
        logger.warn(f'routing tables overlap found. all_active={fm(all_active)}. '
                    f'active_minus_protected={fm(active_minus_protected)}. '
                    f'range we concerned of {fm(tb_nums_range)}. '
                    f'overlap with active_minus_protected: {fm(overlap)}')
        rp = rpdb_rules(skip_dup_chk=True)
        rp.del_by(func_true_on_match=lambda s, t: t in overlap)
        for n in overlap:
            tb = tab_rules(n)
            tb.empty()
    else:
        logger.info(f'no ovelap. all_active={fm(all_active)}. '
                    f'active_minus_protected={fm(active_minus_protected)}. '
                    f'range we concerned of {fm(tb_nums_range)}. '
                    f'overlap with active_minus_protected: {fm(overlap)}')
