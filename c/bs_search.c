#include <stdlib.h>
#include <stdio.h>
#include "bs_search.h"
#include "color.h"
#include "drscbt.h"

struct search_results bs_match(Img canvas_img, Img pat_img, int chan_err_toler) {
    uint8_t *canvas_grayscale = malloc(canvas_img.px_cnt);
    pic_to_grayscale(canvas_img, canvas_grayscale);

    uint8_t *pat_grayscale = malloc(pat_img.px_cnt);
    pic_to_grayscale(pat_img, pat_grayscale);

    struct bs_sums canv_sums = bs_calc_sums(canvas_grayscale, canvas_img.width, canvas_img.height, pat_img.width);

    struct bs_sums pat_sums = bs_calc_sums(pat_grayscale, pat_img.width, pat_img.height, pat_img.width);

    struct match_item *occurrences = malloc(SEARCH_MAX_RESULTS * sizeof(struct match_item));
    struct search_results results;
    results.occurrences = occurrences;
    results.res_count = 0;
    results.collisions = 0;

    int max_possible_pat_start_row = canv_sums.height - pat_sums.height;

    for (int canv_sum_col_i = 0; canv_sum_col_i < canv_sums.width; canv_sum_col_i++) {
        for (int canv_sum_row_i = 0; canv_sum_row_i <= max_possible_pat_start_row; canv_sum_row_i++) {
            for (int cmp_row_i = 0; cmp_row_i < pat_sums.height; cmp_row_i++) {
                bs_sum_value c_sum = bs_sum_at(canv_sums, canv_sum_col_i, canv_sum_row_i + cmp_row_i);
                bs_sum_value p_sum = *(pat_sums.sums + cmp_row_i);
                if (!bs_candidate(c_sum, p_sum, chan_err_toler)) {
                    goto next_start_row;
                }
            }

            int confirm_y, confirm_x;
            for (confirm_y = 0; confirm_y < pat_img.height; confirm_y++) {
                for (confirm_x = 0; confirm_x < pat_img.width; confirm_x++) {
                    struct pixel c_px = val_at(canvas_img, canv_sum_col_i + confirm_x, canv_sum_row_i + confirm_y);
                    struct pixel p_px = val_at(pat_img, confirm_x, confirm_y);
                    c_px.filler = 0;
                    p_px.filler = 0;
                    if (!val_eq_err(c_px, p_px, chan_err_toler)) {
                        goto confirm_stop;
                    }
                }
            }
            confirm_stop:

            if ((confirm_y == pat_img.height) && (confirm_x == pat_img.width)) {
                (occurrences + results.res_count)->x = canv_sum_col_i;
                (occurrences + results.res_count)->y = canv_sum_row_i;
                results.res_count++;
                if (results.res_count == SEARCH_MAX_RESULTS) {
                    goto search_stop;
                }
            } else {
                results.collisions++;
            }

            next_start_row:
            continue;
        }

    }
    search_stop:

    bs_free_sums(canv_sums);
    bs_free_sums(pat_sums);
    free(canvas_grayscale);
    free(pat_grayscale);

    return results;
}

bool bs_candidate(bs_sum_value a, bs_sum_value b, int chan_err_toler) {
    return abs(a - b) <= chan_err_toler;
}

int bs_adv(bs_state *state, bs_sum_value *sum, const uint8_t *stride_s, const uint8_t *px, int win) {
    uintptr_t ptrdiff_t_diff = px - stride_s;

    if (ptrdiff_t_diff == 0) {
        *state = 0;
    }

    if (ptrdiff_t_diff >= win) {
        *state -= *(px - win);
    }

    *state += *px;

    if (ptrdiff_t_diff >= (win - 1)) {
        *sum = *state / win;
        return ptrdiff_t_diff;
    }

    return -1;
}

struct bs_sums bs_calc_sums(uint8_t *img_channel, int img_width, int img_height, int win) {
    bs_state state;
    bs_sum_value sum;

    uint8_t *in_row_start = img_channel;
    int sums_stride = (img_width - win + 1);
    size_t sums_size = sizeof(bs_sum_value) * sums_stride * img_height;
    bs_sum_value *sums_start = malloc(sums_size);

    for (int y = 0; y < img_height; y++) {
        uint8_t *in_current = in_row_start;
        uint8_t *in_row_end = in_row_start + img_width;
        bs_sum_value *sums_row_start = sums_start + (y * sums_stride);
        while (in_current < in_row_end) {
            int sum_write_x;
            sum_write_x = bs_adv(&state, &sum, in_row_start, in_current, win);
            if (-1 != sum_write_x) {
                int sums_row_x_offset = (sum_write_x - win + 1);
                *(sums_row_start + sums_row_x_offset) = sum;
            }
            in_current++;
        }
        in_row_start += img_width;
    }

    struct bs_sums sums_s;
    sums_s.sums = sums_start;
    sums_s.width = sums_stride;
    sums_s.height = img_height;
    sums_s.cnt = sums_stride * img_height;

    return sums_s;
}

void bs_free_sums(struct bs_sums s) {
    free(s.sums);
}
