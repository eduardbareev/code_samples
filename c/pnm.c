#include <stdio.h>
#include <fcntl.h>
#include <stdlib.h>
#include <unistd.h>
#include "pnm.h"

Img read_ppm_file(char* file_name) {
    int fd = open(file_name, O_RDONLY);
    if (fd == -1) {
        perror("file open failure");
        exit(EXIT_FAILURE);
    }

    struct ppm_header header = read_ppm_header(fd);

    Img img;
    switch (header.type) {
        case 3:
            img = read_ppm_data_plain(header, fd);
            break;
        case 6:
            img = read_ppm_data_raw(header, fd);
            break;
        default:
            fprintf(stderr, "format P%d is not supported\n", header.type);
            exit(EXIT_FAILURE);
    }

    return img;
}

Img read_ppm_data_raw(struct ppm_header header, int fd) {
    int pix_len = header.width * header.height;
    int samples_len = pix_len * 3;
    int sample_num = 0;
    struct pixel* img = malloc(pix_len * sizeof(struct pixel));
    const int chunk = 64;
    unsigned char buff[chunk];
    int sample_chan_num = 0;
    uint32_t rgba = 0x000000FF;
    int pix_num = 0;
    ssize_t r;

    while ((r = read(fd, buff, chunk)) > 0) {
        for (ssize_t buff_idx = 0; buff_idx < r; buff_idx++) {
            if (sample_num == samples_len) {
                fprintf(stderr, "extra data\n");
                exit(EXIT_FAILURE);
            }

            rgba |= (unsigned)buff[buff_idx] << (unsigned)((3 - sample_chan_num) * 8);
            if (++sample_chan_num == 3) {
                ((uint32_t *)img)[pix_num] = rgba;
                rgba = 0x000000FF;
                sample_chan_num = 0;
                pix_num++;
            }
            sample_num++;
        }
    }

    if (r == -1) {
        perror("read error");
        exit(EXIT_FAILURE);
    }

    if (pix_num < pix_len) {
        fprintf(stderr, "unexpected EOF\n");
        exit(EXIT_FAILURE);
    }

    Img img_read;
    img_read.px_cnt = pix_num;

    img_read.pxs = img;
    img_read.width = header.width;
    img_read.height = header.height;

    return img_read;
}

Img read_ppm_data_plain(struct ppm_header header, int fd) {
    int pix_len = header.width * header.height;
    int samples_len = pix_len * 3;
    int sample_num = 0;
    struct pixel* img = malloc(pix_len * sizeof(struct pixel));
    int sample_chan_num = 0;
    int sample_value_accum = 0;
    uint32_t rgba = 0x000000FF;
    int pix_num = 0;
    enum PpmDCharType ch_type;
    enum PpmDState state = PPM_D_S_READING_WSP;
    const int chunk = 64;
    char buff[chunk];
    int buff_idx = 0;
    int f_rd_cnt = 0;

    for (;;) {
        if (buff_idx == f_rd_cnt) {
            f_rd_cnt = read(fd, buff, chunk);
            if (f_rd_cnt == -1) {
                perror("file read failure");
                exit(EXIT_FAILURE);
            }
            buff_idx = 0;
        }

        char c = buff[buff_idx];
        if (f_rd_cnt == 0) {
            ch_type = PPM_D_C_EOF;
        } else if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
            ch_type = PPM_D_C_WSP;
        } else if ((c - 48) >= 0 && (c - 48) <= 9) {
            ch_type = PPM_D_C_NUMERIC;
        } else {
            ch_type = PPM_D_C_OTHER;
        }

        switch (state) {
            case PPM_D_S_READING_WSP:
                switch (ch_type) {
                    case PPM_D_C_EOF:
                        state = PPM_D_S_DATA_CONSUMED;
                        goto read_stop;
                    case PPM_D_C_WSP:
                        break;
                    case PPM_D_C_NUMERIC:
                        sample_value_accum = c - 48;
                        state = PPM_D_S_READING_NUMERIC;
                        break;
                    default:
                        goto read_stop;
                }
                break;
            case PPM_D_S_READING_NUMERIC:
                switch (ch_type) {
                    case PPM_D_C_EOF:
                    case PPM_D_C_WSP:
                        rgba |= (unsigned)sample_value_accum << (unsigned)((3 - sample_chan_num) * 8);
                        if (++sample_chan_num == 3) {
                            ((uint32_t *)img)[pix_num] = rgba;
                            rgba = 0x000000FF;
                            sample_chan_num = 0;
                            pix_num++;
                        }
                        sample_num++;

                        if (ch_type == PPM_D_C_EOF) {
                            state = PPM_D_S_DATA_CONSUMED;
                            goto read_stop;
                        } else {
                            if (sample_num == samples_len) {
                                state = PPM_D_S_DATA_CONSUMED;
                            } else {
                                state = PPM_D_S_READING_WSP;
                            }
                        }
                        break;
                    case PPM_D_C_NUMERIC:
                        sample_value_accum *= 10;
                        sample_value_accum += c - 48;
                        break;
                    default:
                        goto read_stop;
                }
                break;
            case PPM_D_S_DATA_CONSUMED:
                switch (ch_type) {
                    case PPM_D_C_EOF:
                        goto read_stop;
                        break;
                    case PPM_D_C_WSP:
                        break;
                    default:
                        state = PPM_D_S_EXTRA_DATA;
                        goto read_stop;
                        break;
                }
                break;
            default:
                // impossible,
                // -Wswitch
                break;
        }
        buff_idx++;
    }

    read_stop:

    if (state == PPM_D_S_EXTRA_DATA) {
        fprintf(stderr, "extra data found");
        exit(EXIT_FAILURE);
    }

    if (state != PPM_D_S_DATA_CONSUMED) {
        fprintf(stderr, "error while reading pnm "
                        "data. s=%d c=%d\n", state, ch_type
        );
        exit(EXIT_FAILURE);
    }

    if (pix_num < pix_len) {
        fprintf(stderr, "insufficient number of samples read\n");
        exit(EXIT_FAILURE);
    }

    Img img_read;
    img_read.px_cnt = pix_num;
    img_read.pxs = img;
    img_read.width = header.width;
    img_read.height = header.height;

    return img_read;
}

struct ppm_header read_ppm_header(int fp) {
    char c;
    int r;

    enum PpmHState state = PPM_H_S_INIT;
    enum PpmHCharType ch_type;

    int num_accum = 0;
    int num_read_cnt = 0;

    const int NUMS_TO_COLLECT = 4;
    int numbers[NUMS_TO_COLLECT];

    for (;;) {
        r = read(fp, &c, 1);
        if (r == -1) {
            perror("read error");
            exit(EXIT_FAILURE);
        }

        if (r == 0) {
            ch_type = PPM_H_C_EOF;
        } else if (c == '\r' || c == ' ' || c == '\t') {
            ch_type = PPM_H_C_WSP;
        } else if (c == '\n') {
            ch_type = PPM_H_C_NL;
        } else if (c == 'P') {
            ch_type = PPM_H_C_CHAR_P;
        } else if (c == '#') {
            ch_type = PPM_H_C_NUM_SGN;
        } else if ((c - 48) >= 0 && (c - 48) <= 9) {
            ch_type = PPM_H_C_NUMERIC;
        } else {
            ch_type = PPM_H_C_OTHER;
        }

        switch (state) {
            case PPM_H_S_INIT:
                switch (ch_type) {
                    case PPM_H_C_WSP:
                        break;
                    case PPM_H_C_CHAR_P:
                        state = PPM_H_S_CHARP_READ;
                        break;
                    default:
                        goto parse_stop;
                }
                break;
            case PPM_H_S_CHARP_READ:
                switch (ch_type) {
                    case PPM_H_C_NUMERIC:
                        num_accum = c - 48;
                        state = PPM_H_S_READING_NUMERIC;
                        break;
                    default:
                        goto parse_stop;
                }
                break;
            case PPM_H_S_READING_NUMERIC:
                switch (ch_type) {
                    case PPM_H_C_NUMERIC:
                        num_accum *= 10;
                        num_accum += c - 48;
                        break;
                    case PPM_H_C_WSP:
                    case PPM_H_C_NL:
                        numbers[num_read_cnt] = num_accum;
                        if (++num_read_cnt == NUMS_TO_COLLECT) {
                            state = PPM_H_S_HEADER_CONSUMED;
                            goto parse_stop;
                        }
                        state = PPM_H_S_READING_WSP;
                        break;
                    default:
                        goto parse_stop;
                }
                break;
            case PPM_H_S_READING_WSP:
                switch (ch_type) {
                    case PPM_H_C_WSP:
                    case PPM_H_C_NL:
                        break;
                    case PPM_H_C_NUMERIC:
                        num_accum = c - 48;
                        state = PPM_H_S_READING_NUMERIC;
                        break;
                    case PPM_H_C_NUM_SGN:
                        state = PPM_H_S_READING_COMMENT;
                        break;
                    default:
                        goto parse_stop;
                }
                break;
            case PPM_H_S_READING_COMMENT:
                switch (ch_type) {
                    case PPM_H_C_NL:
                        state = PPM_H_S_READING_WSP;
                        break;
                    case PPM_H_C_EOF:
                        goto parse_stop;
                    default:
                        break;
                }
                break;
            default:
                // impossible,
                // -Wswitch
                break;
        }
    }

    parse_stop:
    if (state != PPM_H_S_HEADER_CONSUMED) {
        fprintf(stderr, "error while reading pnm "
                        "header. s=%d c=%d\n", state, ch_type
        );
        exit(EXIT_FAILURE);
    }

    struct ppm_header header;
    header.type = numbers[0];
    header.width = numbers[1];
    header.height = numbers[2];

    return header;
}
