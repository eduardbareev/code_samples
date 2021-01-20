package com.drscbt.shared.piclib.pnm;

import com.drscbt.shared.utils.Utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class PNM {
    public static PNMReader getReader(InputStream is) {
        Header header = _readHeader(is);
        PNMReader rdr = null;
        PNMType pnmType = PNMType.byPNMId(header.typeId);
        switch (pnmType) {
            case PLAIN_COLOR_PPM_P_3:
                rdr = new PPMP3RdrPlain(header, is);
                break;
            case PLAIN_GRAYSCALE_PGM_P_2:
                rdr = new PGMP2RdrPlain(header, is);
                break;
            case RAW_COLOR_PPM_P_6:
                rdr = new PPMRdrRaw(header, is);
                break;
            case RAW_GRAYSCALE_PGM_P_5:
                rdr = new PGMP5RdrRaw(header, is);
                break;
            default:
                _e(String.format("reading %s P%d is not supported", pnmType.toString(), header.typeId));
                break;
        }

        return rdr;
    }

    public static PNMWriter getWriter(OutputStream os, PNMType type, int w, int h) {
        PNMWriter writer = null;
        switch (type) {
            case PLAIN_COLOR_PPM_P_3:
                writer = new PPMWriterPlain(os, w, h);
                break;
            case PLAIN_GRAYSCALE_PGM_P_2:
                writer = new PGMWriterPlain(os, w, h);
                break;
            default:
                _e(String.format("writing %s is not supported", type));
                break;
        }
        _writeHeader(os, type, w, h);
        return writer;
    }

    static void _e(String s) {
        throw new RuntimeException("err: " + s);
    }

    static private void _writeHeader(OutputStream os, PNMType type, int w, int h) {
        int iType = type.pId;
        String header = String.format("P%d\n%d %d\n255\n", iType, w, h);
        byte[] headerBytes = Utils.strEncode(header, "latin1");
        try {
            os.write(headerBytes);
            os.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static private Header _readHeader(InputStream is) {
        HeaderState state = HeaderState.INIT;
        HeaderCharType chType;

        int numAcc = 0;

        int NUMS_TO_COLLECT = 4;
        List<Integer> numbers = new ArrayList<Integer>();

        readLoop: for (;;) {
            int r;
            try {
                r = is.read();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            char c = 0;

            if (r == -1) {
                chType = HeaderCharType.R_EOF;
            } else {
                c = (char) r;
                if (c == '\r' || c == ' ' || c == '\t') {
                    chType = HeaderCharType.WSP;
                } else if (c == '\n') {
                    chType = HeaderCharType.NL;
                } else if (c == 'P') {
                    chType = HeaderCharType.CHAR_P;
                } else if (c == '#') {
                    chType = HeaderCharType.NUM_SGN;
                } else if ((c - 48) >= 0 && (c - 48) <= 9) {
                    chType = HeaderCharType.NUMERIC;
                } else {
                    chType = HeaderCharType.OTHER;
                }
            }

            switch (state) {
                case INIT:
                    switch (chType) {
                        case WSP:
                            break;
                        case CHAR_P:
                            state = HeaderState.CHARP_READ;
                            break;
                        default:
                            break readLoop;
                    }
                    break;
                case CHARP_READ:
                    switch (chType) {
                        case NUMERIC:
                            numAcc = c - 48;
                            state = HeaderState.READING_NUMERIC;
                            break;
                        default:
                            break readLoop;
                    }
                    break;
                case READING_NUMERIC:
                    switch (chType) {
                        case NUMERIC:
                            numAcc *= 10;
                            numAcc += c - 48;
                            state = HeaderState.READING_NUMERIC;
                            break;
                        case WSP:
                        case NL:
                            numbers.add(numAcc);
                            if (numbers.size() == NUMS_TO_COLLECT) {
                                state = HeaderState.HEADER_CONSUMED;
                                break readLoop;
                            }
                            state = HeaderState.READING_WSP;
                            break;
                        default:
                            break readLoop;
                    }
                    break;
                case READING_WSP:
                    switch (chType) {
                        case WSP:
                        case NL:
                            break;
                        case NUMERIC:
                            numAcc = c - 48;
                            state = HeaderState.READING_NUMERIC;
                            break;
                        case NUM_SGN:
                            state = HeaderState.READING_COMMENT;
                            break;
                        default:
                            break readLoop;
                    }
                    break;
                case READING_COMMENT:
                    switch (chType) {
                        case NL:
                            state = HeaderState.READING_WSP;
                            break;
                        case R_EOF:
                            break readLoop;
                        default:
                            break;
                    }
                    break;
                default:
                    // impossible
                    break;
            }
        }

        if (state != HeaderState.HEADER_CONSUMED) {
            _e(String.format("error while reading ppm " +
                    "header. s=%s c=%s\n", state, chType));
        }

        Header header = new Header();
        header.typeId = numbers.get(0);
        header.w = numbers.get(1);
        header.h = numbers.get(2);

        return header;
    }

    public static class Header {
        int typeId;
        public int w;
        public int h;
    }

    enum HeaderState {
        INIT,
        CHARP_READ,
        READING_NUMERIC,
        READING_WSP,
        READING_COMMENT,
        HEADER_CONSUMED
    }

    enum HeaderCharType {
        R_EOF,
        CHAR_P,
        NUM_SGN,
        NUMERIC,
        WSP,
        NL,
        OTHER
    }

    public enum PNMType {
        PLAIN_COLOR_PPM_P_3(3),
        RAW_COLOR_PPM_P_6(6),
        RAW_GRAYSCALE_PGM_P_5(5),
        PLAIN_GRAYSCALE_PGM_P_2(2);

        int pId;

        PNMType(int id) {
            pId = id;
        }

        public static PNMType byPNMId(int id) {
            for(PNMType item : values()) {
                if(item.pId == id) {
                    return item;
                }
            }
            throw new IllegalArgumentException(String.format("no PNMType with %d id", id));
        }
    }
}
