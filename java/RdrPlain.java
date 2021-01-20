package com.drscbt.shared.piclib.pnm;

import java.io.IOException;
import java.io.InputStream;

abstract public class RdrPlain extends PNMReader {
    abstract protected void _processSample(int value);

    protected int _readPixCnt;

    public void _read(int expSampleNum) {
        int readSampleCnt = 0;
        int chunk = 64;
        byte[] buff = new byte[chunk];
        int buffIdx = 0;
        int sampleValueAccum = 0;
        int fRdCnt = 0;

        PnmDataCharType chType;
        PnmDataReadState state = PnmDataReadState.READING_WSP;

        charRead: for (;;) {
            if (buffIdx == fRdCnt) {
                try {
                    fRdCnt = this._is.read(buff, 0, chunk);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                buffIdx = 0;
            }

            char c = (char) buff[buffIdx];

            if (fRdCnt == -1) {
                chType = PnmDataCharType.EOF;
            } else if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
                chType = PnmDataCharType.WSP;
            } else if ((c - 48) >= 0 && (c - 48) <= 9) {
                chType = PnmDataCharType.NUMERIC;
            } else {
                chType = PnmDataCharType.OTHER;
            }

            switch (state) {
                case READING_WSP:
                    switch (chType) {
                        case EOF:
                            state = PnmDataReadState.DATA_CONSUMED;
                            break charRead;
                        case WSP:
                            break;
                        case NUMERIC:
                            sampleValueAccum = c - 48;
                            state = PnmDataReadState.READING_NUMERIC;
                            break;
                        default:
                            break charRead;
                    }
                    break;
                case READING_NUMERIC:
                    switch (chType) {
                        case EOF:
                        case WSP:
                            this._processSample(sampleValueAccum);
                            readSampleCnt++;

                            if (chType == PnmDataCharType.EOF) {
                                state = PnmDataReadState.DATA_CONSUMED;
                                break charRead;
                            } else {
                                if (readSampleCnt == expSampleNum) {
                                    state = PnmDataReadState.DATA_CONSUMED;
                                } else {
                                    state = PnmDataReadState.READING_WSP;
                                }
                            }
                            break;
                        case NUMERIC:
                            sampleValueAccum *= 10;
                            sampleValueAccum += c - 48;
                            break;
                        default:
                            break charRead;
                    }
                    break;
                case DATA_CONSUMED:
                    switch (chType) {
                        case EOF:
                            break charRead;
                        case WSP:
                            break;
                        default:
                            state = PnmDataReadState.EXTRA_DATA;
                            break charRead;
                    }
                    break;
                default:
                    // impossible,
                    // -Wswitch
                    break;
            }
            buffIdx++;
        }

        if (state == PnmDataReadState.EXTRA_DATA) {
            PNM._e("extra data found");
        }

        if (state != PnmDataReadState.DATA_CONSUMED) {
            PNM._e(String.format("error while reading pnm " +
                    "data. s=%s c=%s", state, chType
            ));
        }

        if (readSampleCnt < expSampleNum) {
            PNM._e("insufficient number of samples read");
        }
    }

    enum PnmDataReadState {
        READING_WSP,
        READING_NUMERIC,
        EXTRA_DATA,
        DATA_CONSUMED
    }

    enum PnmDataCharType {
        EOF,
        WSP,
        NUMERIC,
        OTHER,
    }

    RdrPlain(PNM.Header header, InputStream is) {
        super(header, is);
    }
}
