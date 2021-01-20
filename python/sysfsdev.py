TREE_ROOT = '/sys/bus/usb/devices/'
import hwdata

usb = hwdata.USB()


def read_sysfs():
    import os
    import re
    ids = [x for x in os.listdir(TREE_ROOT) if re.match(r'\d+[-\d.]+$', x)]
    props_fnames = ['product', 'manufacturer', 'busnum', 'devnum', 'idVendor', 'idProduct']
    devs = []
    for id_ in ids:
        fsr = {}
        for basename in props_fnames:
            f_path = os.path.join(TREE_ROOT, id_, basename)
            if os.path.isfile(f_path):
                value = open(f_path, 'r').read().strip()
            else:
                value = None
            fsr[basename] = value
        fsr['id'] = id_
        devs.append(fsr)
    return devs


class sfsdev():
    def _read_file_prop(self, relative_path):
        import os
        path = os.path.join(self.full_path, relative_path)
        if os.path.exists(path):
            return open(path, 'r').read().strip()
        return None

    def reload(self):
        self._load_data()

    def __init__(self, id_str):
        self.id = id_str
        import os
        self.full_path = os.path.join(TREE_ROOT, self.id)
        if not os.path.exists(self.full_path):
            raise Exception(f'{self.full_path} does not exist')
        self._load_data()

    def _load_data(self):
        def _load_id_vendor():
            r = self._read_file_prop('idVendor')
            if r is None:
                return None
            return int(r, 16)

        def _load_id_vendor_text():
            _id = _load_id_vendor()
            if _id is None:
                return None
            return usb.get_vendor(format(_id, '04x'))

        def _load_id_product():
            r = self._read_file_prop('idProduct')
            if r is None:
                return None
            return int(r, 16)

        def _load_id_product_text():
            prod_id = _load_id_product()
            if prod_id is None:
                return None
            vend_id = _load_id_vendor()
            if vend_id is None:
                return None
            return usb.get_device(format(vend_id, '04x'), format(prod_id, '04x'))

        def _load_busnum():
            r = self._read_file_prop('busnum')
            if r is None:
                return None
            return int(r)

        def _load_devnum():
            r = self._read_file_prop('devnum')
            if r is None:
                return None
            return int(r)

        def _load_product():
            return self._read_file_prop('product')

        def _load_manufacturer():
            return self._read_file_prop('manufacturer')

        def _load_device_class():
            r = self._read_file_prop('bDeviceClass')
            if r is None:
                return None
            return dev_class(int(r, 16))

        def _load_ttys():
            import os
            import glob
            port_paths = glob.glob(self.full_path + '/*/ttyUSB*', )
            if len(port_paths) == 0:
                return None
            port_basenames = [os.path.join('/dev/', os.path.basename(x)) for x in port_paths]
            return port_basenames

        def _load_behind_hubs():
            return self.id.count('.')

        def _load_is_root_hub():
            return self.id.count('-') == 0

        self.id_vendor         = _load_id_vendor()
        self.id_vendor_text    = _load_id_vendor_text()
        self.id_product        = _load_id_product()
        self.id_product_text   = _load_id_product_text()
        self.busnum            = _load_busnum()
        self.devnum            = _load_devnum()
        self.product           = _load_product()
        self.manufacturer      = _load_manufacturer()
        self.device_class      = _load_device_class()
        self.ttys              = _load_ttys()
        self.behind_hubs       = _load_behind_hubs()
        self.is_root_hub       = _load_is_root_hub()

    def present(self):
        import os
        return os.path.exists(self.full_path)

    def _raise_if_absent(self):
        if not self.present():
            raise Exception(f'device {self.id} is absent')


from enum import Enum


class dev_class(Enum):
    Defined_at_Interface_level     = 0x00
    Audio                          = 0x01
    Communications                 = 0x02
    Human_Interface_Device         = 0x03
    Physical_Interface_Device      = 0x05
    Imaging                        = 0x06
    Printer                        = 0x07
    Mass_Storage                   = 0x08
    Hub                            = 0x09
    CDC_Data                       = 0x0a
    Chip_SmartCard                 = 0x0b
    Content_Security               = 0x0d
    Video                          = 0x0e
    Xbox                           = 0x58
    Diagnostic                     = 0xdc
    Wireless                       = 0xe0
    Miscellaneous_Device           = 0xef
    Application_Specific_Interface = 0xfe
    Vendor_Specific_Class          = 0xff
