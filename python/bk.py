#!/usr/bin/env python

import argparse
import datetime
import json
import os
import os.path
import re
import subprocess
import sys
from math import floor
from time import sleep
from time import time as timef

import libvirt
import lxml.etree as ET
import progressbar
from tabulate import tabulate

import include

BORG_REPO = '/mnt/hdd/backups/borg2'
BTR_SNAP_PATH = '/tmpsnpforbck'
EXCLUDE_TXT_PATH = 'bk-exclude.txt'
vm_state_names = {
    libvirt.VIR_DOMAIN_NOSTATE:     'NOSTATE',
    libvirt.VIR_DOMAIN_RUNNING:     'RUNNING',
    libvirt.VIR_DOMAIN_BLOCKED:     'BLOCKED',
    libvirt.VIR_DOMAIN_PAUSED:      'PAUSED',
    libvirt.VIR_DOMAIN_SHUTDOWN:    'SHUTDOWN',
    libvirt.VIR_DOMAIN_SHUTOFF:     'SHUTOFF',
    libvirt.VIR_DOMAIN_CRASHED:     'CRASHED',
    libvirt.VIR_DOMAIN_PMSUSPENDED: 'PMSUSPENDED',
}
prune_num = 30


def vm_state_name(num):
    return vm_state_names.get(num, num)


def err(m):
    print(m)
    sys.exit(1)


def warn(m):
    print(m)


def collect_vms_backup_flags():
    conn = libvirt.open('qemu:///system')

    allvms = conn.listAllDomains(libvirt.VIR_CONNECT_LIST_DOMAINS_PERSISTENT)

    names_and_backup_flags = []

    for vm in allvms:
        try:
            metadata_xml_str = vm.metadata(type=libvirt.VIR_DOMAIN_METADATA_ELEMENT,
                                           uri="http://example.com/backup")
        except libvirt.libvirtError as e:
            if e.get_error_code() != libvirt.VIR_ERR_NO_DOMAIN_METADATA:
                raise e
            metadata_xml_str = None

        if metadata_xml_str:

            xml_tree = ET.fromstring(metadata_xml_str)
            b = bool(int(xml_tree.xpath('do')[0].text))
        else:
            b = None

        names_and_backup_flags.append({'name': vm.name(), 'backup': b, 'vm': vm})
    conn.close()
    return names_and_backup_flags


def get_disks(vm):
    xmlstr = vm.XMLDesc()

    xml = ET.fromstring(xmlstr)
    disks = []
    for disk in xml.xpath('devices/disk[@device="disk"]'):
        disks.append(
            {"file": disk.xpath('source')[0].get('file'),
             "target_dev_id": disk.xpath('target')[0].get('dev')}
        )
    return disks


def state_wait(vm, t_state):
    start = timef()
    name = vm.name()
    while True:
        sleep(1)
        state, reason = vm.state()
        include.debugp(f'waiting {name} to became {vm_state_name(t_state)}. '
                       f'state={vm_state_name(state)}')

        if state == t_state:
            break
        if (timef() - start) > 20:
            break
    if state != t_state:
        return False
    return True


def backup_vm_disks(disk_paths, repo_path_and_arc_name):
    dry_run_opt = ['--dry-run'] if args.dry_run else []
    progress_opt = ['--progress'] if args.progress else []
    p_params = [
        '/usr/bin/borg',
        'create',
        *dry_run_opt,
        *progress_opt,
        '--verbose',
        # '--progress',
        '--compression=lz4',
        repo_path_and_arc_name,
        *disk_paths
    ]

    env_m = os.environ.copy()
    env_m['BORG_UNKNOWN_UNENCRYPTED_REPO_ACCESS_IS_OK'] = 'yes'

    include.subprocrun(p_params, check=True, env=env_m)


def libvirt_err_callback(userdata, err):
    # https://stackoverflow.com/a/45543887
    pass


def bck_vm_libvirt(borg_repo):
    libvirt.registerErrorHandler(f=libvirt_err_callback, ctx=None)
    names_and_backup_flags = collect_vms_backup_flags()

    vb = [{k: v for k, v in e.items() if k in ('name', 'backup',)} for e in names_and_backup_flags]
    include.debugp(tabulate(vb, headers="keys", tablefmt='fancy_grid'))

    list_to_backup = [x for x in names_and_backup_flags if x['backup'] == True]

    vms_brg_arc_prefix = 'vms-'

    if args.prune:
        for n in [n['name'] for n in list_to_backup]:
            bk_prune(borg_repo, vms_brg_arc_prefix + n, prune_num)

    for n, x in enumerate(list_to_backup):
        vm = x['vm']
        name = x["name"]

        state, reason = vm.state()
        include.debugp(f'({n + 1}/{len(list_to_backup)}) going to backup "{name}" '
                       f'state={vm_state_name(state)}')
        base_disks = get_disks(vm)

        if state == libvirt.VIR_DOMAIN_RUNNING:
            if not args.dry_run:
                snp_cr_xml = "\
                    <domainsnapshot>\
                        <name>tmpsnpsh</name>\
                    </domainsnapshot>"
                vm.snapshotCreateXML(snp_cr_xml,
                                     libvirt.VIR_DOMAIN_SNAPSHOT_CREATE_NO_METADATA
                                     | libvirt.VIR_DOMAIN_SNAPSHOT_CREATE_QUIESCE
                                     | libvirt.VIR_DOMAIN_SNAPSHOT_CREATE_DISK_ONLY
                                     )

        repo_path_and_arc_name = borg_repo + '::' \
                                 + vms_brg_arc_prefix + name + '-' + get_ts_str()

        if not args.dry_run:
            backup_vm_disks([x['file'] for x in base_disks], repo_path_and_arc_name)
            report_text = get_arc_info_report_text(repo_path_and_arc_name)
            include.report_print(report_text + '\n')
            if args.check:
                include.bck_check(repo_path_and_arc_name)

        else:
            include.report_print(f"dry-run. backup {base_disks} to {repo_path_and_arc_name}")

        if state == libvirt.VIR_DOMAIN_RUNNING:
            if not args.dry_run:
                for bd in base_disks:
                    dev_id = bd['target_dev_id']
                    vm.blockCommit(disk=dev_id, base=None, top=None,
                                   flags=libvirt.VIR_DOMAIN_BLOCK_COMMIT_ACTIVE
                                         | libvirt.VIR_DOMAIN_BLOCK_COMMIT_DELETE)

                    while True:
                        sleep(1)
                        blkjinfo = vm.blockJobInfo(path=dev_id)
                        include.debugp(f"blkjinfo={blkjinfo}")
                        if blkjinfo['cur'] == blkjinfo['end']:
                            include.debugp("blockjob complete")
                            break
                    include.debugp("issuing pivot")
                    vm.blockJobAbort(disk=dev_id, flags=libvirt.VIR_DOMAIN_BLOCK_JOB_ABORT_PIVOT)


def bk_prune(borg_repo, prefix, keep_last_n):
    p_params = [
        '/usr/bin/borg',
        'prune',
        borg_repo,
        '-P',
        prefix,
        '--keep-last',
        str(keep_last_n)
    ]

    env_m = os.environ.copy()
    env_m['BORG_UNKNOWN_UNENCRYPTED_REPO_ACCESS_IS_OK'] = 'yes'

    include.subprocrun(p_params, check=True, env=env_m)


def btr_snap_mk(src, snap_path):
    if os.path.exists(snap_path):
        raise Exception(f'btr_snap_mk: {snap_path} already exists')

    include.subprocrun(['/usr/sbin/btrfs', 'subvolume', 'snapshot', '-r', src, snap_path],
                       check=True, stdout=subprocess.DEVNULL)


def btr_snap_rm(snap_path):
    if os.path.exists(snap_path):
        include.subprocrun(['/usr/sbin/btrfs', 'subvolume', 'delete', snap_path],
                           check=True, stdout=subprocess.DEVNULL)


def get_ts_str():
    ts_str = datetime.datetime.now().astimezone().strftime('%Y-%m-%d-%H-%M-%S%z')
    return re.sub('\\+(?=\\d{4})', '-', ts_str)


def get_arc_info_report_text(path_name):
    p_params = [
        '/usr/bin/borg',
        'info',
        '--json',
        path_name,
    ]

    r = include.subprocrun(p_params, check=True, stdout=subprocess.PIPE)

    j = json.loads(r.stdout.decode('latin1'))

    compressed_size = j['archives'][0]['stats']['compressed_size']
    deduplicated_size = j['archives'][0]['stats']['deduplicated_size']
    original_size = j['archives'][0]['stats']['original_size']
    name = j['archives'][0]['name']

    def to_mb(ibytes):
        return f"{(ibytes // (1024 ** 2))}MiB"

    t = (f"borg info parsed: {name}: "
         f"original: {to_mb(original_size)} "
         f"compressed: {to_mb(compressed_size)} "
         f"deduplicated: {(to_mb(deduplicated_size))}")

    return t


def bk_root(borg_repo):
    if args.prune:
        bk_prune(borg_repo, 'root-', prune_num)

    btr_snap_path = BTR_SNAP_PATH

    btr_snap_rm(btr_snap_path)
    btr_snap_mk('/', btr_snap_path)

    exl_file = os.path.join(sys.path[0], EXCLUDE_TXT_PATH)

    items_to_backup = os.listdir(btr_snap_path)

    dry_run_opt = ['--dry-run'] if args.dry_run else []
    progress_opt = ['--progress'] if args.progress else []

    arc_name = 'root-' + get_ts_str()
    repo_path_and_arc_name = borg_repo + '::' + arc_name

    p_params = [
        '/usr/bin/borg',
        'create',
        '--verbose',
        *dry_run_opt,
        *progress_opt,
        '--one-file-system',
        '--exclude-caches',
        f'--exclude-from={exl_file}',
        '--exclude-if-present',
        'borg_ignore',
        '--keep-exclude-tags',
        '--compression=auto,lz4',
        repo_path_and_arc_name,
        *items_to_backup
    ]

    env_m = os.environ.copy()
    env_m['BORG_UNKNOWN_UNENCRYPTED_REPO_ACCESS_IS_OK'] = 'yes'

    try:
        old_wd = os.getcwd()
        os.chdir(btr_snap_path)
        include.subprocrun(p_params, check=True, env=env_m)
        os.chdir(old_wd)
        report_text = get_arc_info_report_text(repo_path_and_arc_name)
        include.report_print(report_text + '\n')
        if args.check:
            include.bck_check(repo_path_and_arc_name)
    except Exception as e:
        raise e
    finally:
        btr_snap_rm(btr_snap_path)
        pass


def poweroff():
    include.subprocrun(['/usr/sbin/poweroff'], )


def main():
    borg_repo = BORG_REPO

    if 0 != os.geteuid():
        err('should be run as root')

    parser = argparse.ArgumentParser()

    parser.add_argument("--root", help="backup root filesystem", action='store_true')
    parser.add_argument("--verbose", action='store_true')
    parser.add_argument("--boot", help="backup boot partition", action='store_true')
    parser.add_argument("--vms", help="backup vm images", action='store_true')
    parser.add_argument("--poweroff-timeout", default=60, type=int)
    parser.add_argument("--prune", help="prune", action='store_true')
    parser.add_argument("--check", help="prune", action='store_true', default=True)

    parser.add_argument("--dry-run", help="dry-run", action='store_true')
    parser.add_argument("--progress", help="progress", action='store_true')

    group = parser.add_mutually_exclusive_group(required=True)
    group.add_argument("--poweroff", help="power off", action='store_true')
    group.add_argument("--nopoweroff", help="don't power off", action='store_true')

    global args
    args = parser.parse_args()
    include.args = args

    if not args.root and not args.vms and not args.boot:
        parser.error("at least one of --root, --vms or --boot should be provided")

    if args.root:
        bk_root(borg_repo)

    if args.vms:
        bck_vm_libvirt(borg_repo)

    if args.boot:
        pass

    start = timef()
    if args.poweroff:
        bar = progressbar.ProgressBar(max_value=args.poweroff_timeout)
        while True:
            elapsed = floor(timef() - start)
            if elapsed >= args.poweroff_timeout:
                break
            bar.update(elapsed)
            sleep(0.5)
        poweroff()


if __name__ == "__main__":
    main()
