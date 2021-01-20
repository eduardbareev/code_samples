import re


def gsm_7bit_pack(seps):
    b = bytearray()
    x = 0
    for septet_a_idx, septet_a in enumerate(seps):
        x = 0 if x == 7 else x + 1
        if x == 0:
            continue
        oct_part_a = septet_a >> (x - 1)
        if septet_a_idx + 1 != len(seps):
            oct_part_b = (seps[septet_a_idx + 1] << (8 - x)) & 0xFF
        else:
            oct_part_b = 0
        b.append(oct_part_a + oct_part_b)
    return b


def gsm_7bit_unpack(octs):
    b = bytearray()
    b_septet_part = 0
    x = 0
    for oct_rd_idx, oct_rd in enumerate(octs):
        x = 1 if x == 7 else x + 1
        a_septet_part = (oct_rd << (x - 1)) & 0b01111111
        b.append(a_septet_part + b_septet_part)
        b_septet_part = oct_rd >> (8 - x)
        if x == 7:
            b.append(b_septet_part)
            b_septet_part = 0
    return b


def decode_gsm7b_default(bytes_):
    return conv_gsm7b_default_to_unicode(bytes_)


def conv_gsm7b_default_to_unicode(bytes_):
    bytes_transl = [gsm7_unicode[b] for b in bytes_]
    return unicode_ints_to_utf32be_bytes(bytes_transl).decode('utf-32-be')


def conv_unicode_to_gsm7b_default(text):
    rmap = {u: c for c, u in enumerate(gsm7_unicode)}
    return bytearray([rmap[ord(c)] for c in text])


def unicode_ints_to_utf32be_bytes(ints):
    ba = bytearray()
    for b in ints:
        u = b.to_bytes(length=4, byteorder='big', signed=False)
        ba.extend(u)
    return ba


def hexbytes_to_bytes(bytes_):
    return bytes([(int(x.group(), 16)) for x in re.finditer('.{2}', bytes_)])


def bytes_to_hexbytes(bytes_):
    return ''.join([format(b, '02X') for b in bytes_])


def decode_ucspdu(hextext_bytes):
    return hexbytes_to_bytes(hextext_bytes.decode('latin1')).decode('UTF-16BE')


def encode_to_7bitpackedhex(text):
    return bytes_to_hexbytes(gsm_7bit_pack(conv_unicode_to_gsm7b_default(text)))


def decode_8x(bytes_):
    if is_supported_8x(bytes_):
        off = bytes_[2]
        unicode_32 = bytearray()
        for b in bytes_[3:]:
            sept = b & 0b01111111
            if b & 0b10000000:
                unicode_num = (off << (8 - 1)) + sept
            else:
                unicode_num = gsm7_unicode[sept]
            unicode_32.extend(unicode_num.to_bytes(
                length=4, byteorder='big', signed=False))
        return unicode_32.decode('utf-32-be')
    else:
        raise Exception('is_supported_8x=False')


def chk_all_hi_bit(bytes_):
    for b in bytes_:
        if b & 0b10000000:
            return True
    return False


def is_supported_8x(bytestr):
    return bytestr[0] == 0x81


def addrbook_name_decode_guess(bytes_):
    hex_encoded = not re.match(br'[\dA-F]{2,}$', bytes_) is None
    all_hi_bit_clean = not chk_all_hi_bit(bytes_)
    if (not hex_encoded) and all_hi_bit_clean:
        # gsm 7 (non-packed)
        return conv_gsm7b_default_to_unicode(bytes_)
    elif (not hex_encoded) and (not all_hi_bit_clean):
        raise Exception('not implemented or bad encoding')
    elif hex_encoded:
        actual_bytes = hexbytes_to_bytes(bytes_.decode('latin1'))
        is_supported_8x_ = is_supported_8x(actual_bytes)
        if is_supported_8x_:
            return decode_8x(actual_bytes)
        else:
            try:
                return decode_gsm7b_default(actual_bytes)
            except Exception:
                raise Exception(
                    f'not implemented or bad encoding: hi_bits_0={all_hi_bit_clean} hex_encoded={hex_encoded} is_supported_8x_={is_supported_8x_} str={bytes_}')
    else:
        raise Exception(
            'not implemented or bad encoding: hi_bits_0={hi_bits_0} hex_encoded={hex_encoded}')


# GSM 7 bit Default Alphabet to Unicode
# 3GPP TS 23.038
# http://www.unicode.org/Public/MAPPINGS/ETSI/GSM0338.TXT
gsm7_unicode = [
    0x0040, 0x00a3, 0x0024, 0x00a5,
    0x00e8, 0x00e9, 0x00f9, 0x00ec,
    0x00f2, 0x00e7, 0x000a, 0x00d8,
    0x00f8, 0x000d, 0x00c5, 0x00e5,
    0x0394, 0x005f, 0x03a6, 0x0393,
    0x039b, 0x03a9, 0x03a0, 0x03a8,
    0x03a3, 0x0398, 0x039e, 0x00a0,
    0x00c6, 0x00e6, 0x00df, 0x00c9,
    0x0020, 0x0021, 0x0022, 0x0023,
    0x00a4, 0x0025, 0x0026, 0x0027,
    0x0028, 0x0029, 0x002a, 0x002b,
    0x002c, 0x002d, 0x002e, 0x002f,
    0x0030, 0x0031, 0x0032, 0x0033,
    0x0034, 0x0035, 0x0036, 0x0037,
    0x0038, 0x0039, 0x003a, 0x003b,
    0x003c, 0x003d, 0x003e, 0x003f,
    0x00a1, 0x0041, 0x0042, 0x0043,
    0x0044, 0x0045, 0x0046, 0x0047,
    0x0048, 0x0049, 0x004a, 0x004b,
    0x004c, 0x004d, 0x004e, 0x004f,
    0x0050, 0x0051, 0x0052, 0x0053,
    0x0054, 0x0055, 0x0056, 0x0057,
    0x0058, 0x0059, 0x005a, 0x00c4,
    0x00d6, 0x00d1, 0x00dc, 0x00a7,
    0x00bf, 0x0061, 0x0062, 0x0063,
    0x0064, 0x0065, 0x0066, 0x0067,
    0x0068, 0x0069, 0x006a, 0x006b,
    0x006c, 0x006d, 0x006e, 0x006f,
    0x0070, 0x0071, 0x0072, 0x0073,
    0x0074, 0x0075, 0x0076, 0x0077,
    0x0078, 0x0079, 0x007a, 0x00e4,
    0x00f6, 0x00f1, 0x00fc, 0x00e0,
]

import unittest


class Tests(unittest.TestCase):
    def test_decode_8x(self):
        expected = 'Би.МойБаланс'
        decoded = decode_8x(hexbytes_to_bytes('810C0891B82E9CBEB991B0BBB0BDC1'))
        self.assertEqual(expected, decoded)

    def test_decode_ucspdu(self):
        expected = '153.33 р.\nУзнайте, где сейчас ваши близкие! Локатор покажет! Подкл.: *306#'
        decoded = decode_ucspdu(
            b'003100350033002E0033003300200440002E000A04230437043D0430043904420435002C002004330434043500200441043504390447043004410020043204300448043800200431043B04380437043A0438043500210020041B043E043A04300442043E04400020043F043E043A043004360435044200210020041F043E0434043A043B002E003A0020002A0033003000360023')
        self.assertEqual(expected, decoded)

    def test_decode_gsm7b_default(self):
        expected = 'Design@Home'
        decoded = decode_gsm7b_default(gsm_7bit_unpack(hexbytes_to_bytes('C4F23C7D760390EF7619')))
        self.assertEqual(expected, decoded)

    def test_conv_gsm7b_default_to_unicode(self):
        inp = br'[\]^_`{|}~'  # gsm7b_default byte sequence
        decoded_u = conv_gsm7b_default_to_unicode(inp)
        self.assertEqual(decoded_u, 'ÄÖÑÜ§¿äöñü')  # characters it corresponds to

        reencoded_back = conv_unicode_to_gsm7b_default(decoded_u)
        self.assertEqual(reencoded_back, inp)

    def encode_to_7bitpackedhex(self):
        self.assertEqual(encode_to_7bitpackedhex('*100#'), 'AA180C3602')


class TestsDecAddrbookGuess(unittest.TestCase):
    def test_non_hex_str_ret_as_is(self):
        inp = b'na\xFFme'
        self.assertRaisesRegex(Exception, r'not implemented or bad \w+', addrbook_name_decode_guess, inp)

        inp = br'[\]^_`{|}~'  # bytes of gsm 7 default
        self.assertEqual(addrbook_name_decode_guess(inp), 'ÄÖÑÜ§¿äöñü')  # correct unicode str

    def test_hex_encoded_ucs(self):
        inp = br'[\]^_`{|}~'  # bytes of gsm 7 default
        self.assertEqual(addrbook_name_decode_guess(inp), 'ÄÖÑÜ§¿äöñü')  # correct unicode str
