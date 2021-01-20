#include <stdlib.h>
#include <math.h>
#include "color.h"

void pic_to_grayscale(Img img, uint8_t* out) {
    for (int i = 0; i < img.px_cnt; i++) {
        struct pixel rgb_px = *(img.pxs + i);
        *(out + i) = desaturate(rgb_px);
    }
}

uint8_t desaturate(struct pixel rgb_px) {
    return round((rgb_px.A + rgb_px.B + rgb_px.C) / 3.0);
}

void condensed_hsv_a(struct pixel* in, uint8_t* out, int len, int bits_1, int bits_2, int bits_3) {
    for (int i = 0; i < len; i++) {
        struct pixel rgb_px = *(in + i);
        *(out + i) = condensed_hsv(rgb_px, bits_1, bits_2, bits_3);
    }
}

uint8_t condensed_hsv(struct pixel in, int bits_1, int bits_2, int bits_3) {
    struct pixel hsv_px = to_hsv(in);
    return trunc_to_byte(hsv_px.A, hsv_px.B, hsv_px.C, bits_1, bits_2, bits_3);
}

struct pixel to_hsv(struct pixel clr_in) {
    int highest_rgb = MAX3(clr_in.A, clr_in.B, clr_in.C);
    int lowest_rgb = MIN3(clr_in.A, clr_in.B, clr_in.C);
    int hi_lo_rgb_range = highest_rgb - lowest_rgb;

    int sat;
    if (highest_rgb == 0) {
        sat = 0;
    } else {
        sat = round((HSV_MAX_SAT * hi_lo_rgb_range) / (float)highest_rgb);
    }

    int hue;
    if (sat == 0) {
        hue = 0;
    } else {
        if (clr_in.A == highest_rgb) {
            hue = round((HUE_ONE_SIXTH * (clr_in.B - clr_in.C)) / hi_lo_rgb_range);
        } else if (clr_in.B == highest_rgb) {
            hue = HUE_ONE_THIRD +
                  round((HUE_ONE_SIXTH * (clr_in.C - clr_in.A)) / hi_lo_rgb_range);
        } else {
            hue = HUE_TWO_THIRDS +
                  round((HUE_ONE_SIXTH * (clr_in.A - clr_in.B)) / hi_lo_rgb_range);
        }

        if (hue < 0) {
            hue += HUE_FULL;
        }

        if (hue > HUE_FULL) {
            hue -= HUE_FULL;
        }

        if (hue == HUE_FULL) {
            hue = 0;
        }
    }
    int v = round(((float) highest_rgb / 255.0f) * HSV_MAX_V);

    struct pixel clr;
    clr.A = hue;
    clr.B = sat;
    clr.C = v;
    return clr;
}

uint8_t trunc_to_byte(uint8_t ci1, uint8_t ci2, uint8_t ci3, int bits_1, int bits_2, int bits_3) {
    uint8_t co1 = ci1 & (((1 << bits_1) - 1) << (8 - bits_1));
    uint8_t co2 = (ci2 >> bits_1) & (((1 << bits_2) - 1) << (8 - bits_1 - bits_2));
    uint8_t co3 = (ci3 >> (bits_1 + bits_2)) & ((1 << bits_3) - 1);
    return (co1 + co2 + co3);
}

