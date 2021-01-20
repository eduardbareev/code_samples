#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include "rk_search.h"
#include "color.h"
#include "drscbt.h"

struct search_results rk_match(Img canvas_img, Img pat_img) {
    uint8_t *canvas8 = malloc(canvas_img.px_cnt);
    condensed_hsv_a(canvas_img.pxs, canvas8, canvas_img.px_cnt, H_BITS, S_BITS, V_BITS);
    uint8_t *pat8 = malloc(pat_img.px_cnt);
    condensed_hsv_a(pat_img.pxs, pat8, pat_img.px_cnt, H_BITS, S_BITS, V_BITS);

    struct rk_sums_h_pass canv_hpass_sums = rk_calc_hpass_sums(canvas8, canvas_img.width, canvas_img.height, pat_img.width);
    struct rk_sums_h_pass pat_hpass_sums = rk_calc_hpass_sums(pat8, pat_img.width, pat_img.height, pat_img.width);

    int pat_vpass_sum = 0;
    for (int pat_h_s_row = 0; pat_h_s_row < pat_hpass_sums.height; pat_h_s_row++) {
        pat_vpass_sum = rk_in(pat_vpass_sum, *(pat_hpass_sums.sums + pat_h_s_row));
    }

    struct search_results results;
    results.occurrences = malloc(SEARCH_MAX_RESULTS * sizeof(struct match_item));
    results.res_count = 0;
    results.collisions = 0;

    int clear_factor_vpass = modpow(1 + 0xFF, pat_hpass_sums.height - 1, Q);
    for (int c_s_col = 0; c_s_col < canv_hpass_sums.width; c_s_col++) {
        int canv_vpass_sum = 0;
        for (int c_s_row = 0; c_s_row < canv_hpass_sums.height; c_s_row++) {
            uint8_t canv_sum = rk_sum_at(canv_hpass_sums, c_s_col, c_s_row);
            if (c_s_row >= pat_hpass_sums.height) {
                uint8_t leaving = rk_sum_at(canv_hpass_sums, c_s_col,
                    c_s_row - pat_hpass_sums.height);
                canv_vpass_sum = rk_out(canv_vpass_sum, leaving, clear_factor_vpass);
            }

            canv_vpass_sum = rk_in(canv_vpass_sum, canv_sum);

            if (c_s_row < (pat_hpass_sums.height - 1)) {
                continue;
            }

            int inp_img_row = c_s_row - pat_hpass_sums.height + 1;
            int inp_img_col = c_s_col;
            if (canv_vpass_sum == pat_vpass_sum) {
                int confirm_y, confirm_x;
                for (confirm_y = 0; confirm_y < pat_img.height; confirm_y++) {
                    for (confirm_x = 0; confirm_x < pat_img.width; confirm_x++) {
                        struct pixel c_px = val_at(canvas_img, inp_img_col + confirm_x, inp_img_row + confirm_y);
                        struct pixel p_px = val_at(pat_img, confirm_x, confirm_y);
                        if (!val_eq(c_px, p_px)) {
                            goto confirm_stop;
                        }
                    }
                }
                confirm_stop:

                if ((confirm_y == pat_img.height) && (confirm_x == pat_img.width)) {
                    (results.occurrences + results.res_count)->x = inp_img_col;
                    (results.occurrences + results.res_count)->y = inp_img_row;
                    results.res_count++;
                    if (results.res_count == SEARCH_MAX_RESULTS) {
                        goto search_stop;
                    }
                } else {
                    results.collisions++;
                }
            }
        }
    }
    search_stop:

    rk_free_sums(canv_hpass_sums);
    rk_free_sums(pat_hpass_sums);
    free(canvas8);
    free(pat8);

    return results;
}

uint32_t rk_in(uint32_t value, int in) {
    value *= 256;
    value %= Q;
    value += in;
    value %= Q;
    return value;
}

int rk_out(uint32_t state, uint32_t value, uint32_t clear_factor) {
    state = (state + Q) - ((clear_factor * value) % Q);
    state %= Q;
    return state;
}

int rk_advance_hpass(rk_state *state, rk_sum_hpass_value *sum,
     uint8_t *stride_s, uint8_t *px, int win, int clear_factor
) {
    uintptr_t win_start_off = px - stride_s;

    if (win_start_off == 0) {
        *state = 0;
    }

    if (win_start_off >= win) {
        *state = rk_out(*state, *(px - win), clear_factor);
    }

    *state = rk_in(*state, *px);

    if (win_start_off >= (win - 1)) {
        *sum = *state;
        return win_start_off;
    }

    return -1;
}

struct rk_sums_h_pass rk_calc_hpass_sums(uint8_t *img_channel, int img_width, int img_height, int win) {
    rk_state state;
    rk_sum_hpass_value sum;

    uint8_t *in_row_start = img_channel;
    int sums_stride = (img_width - win + 1);
    size_t sums_mem_size = sizeof(rk_sum_hpass_value) * sums_stride * img_height;

    rk_sum_hpass_value *sums_start = malloc(sums_mem_size);

    int clear_factor = modpow(256, win - 1, Q);

    for (int y = 0; y < img_height; y++) {
        uint8_t *in_current = in_row_start;
        uint8_t *in_row_end = in_row_start + img_width;
        rk_sum_hpass_value *sums_row_start = sums_start + (y * sums_stride);

        while (in_current < in_row_end) {
            int sum_write_x;
            sum_write_x = rk_advance_hpass(&state, &sum, in_row_start,
                                           in_current, win, clear_factor);

            if (-1 != sum_write_x) {
                int sums_row_x_offset = (sum_write_x - win + 1);
                *(sums_row_start + sums_row_x_offset) = sum;
            }
            in_current++;
        }
        in_row_start += img_width;
    }

    struct rk_sums_h_pass sums_s;
    sums_s.sums = sums_start;
    sums_s.width = sums_stride;
    sums_s.height = img_height;
    sums_s.cnt = sums_stride * img_height;

    return sums_s;
}

void rk_free_sums(struct rk_sums_h_pass s) {
    free(s.sums);
}
