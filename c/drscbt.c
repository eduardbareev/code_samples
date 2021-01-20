#include <stdlib.h>
#include <stdio.h>
#include <errno.h>
#include <math.h>
#include "drscbt.h"

void free_img(Img img) {
    free(img.pxs);
}

bool val_eq_err(struct pixel a, struct pixel b, int err_factor) {
    return ((abs(a.A - b.A) <= err_factor)
            && (abs(a.B - b.B) <= err_factor)
            && (abs(a.C - b.C) <= err_factor));
}

bool val_eq(struct pixel a, struct pixel b) {
    return (a.A == b.A)
            && (a.B == b.B)
            && (a.C == b.C);
}

uint32_t modpow(int num, uint32_t pow, int modulus) {
    int i = 0;
    for (; i < 32; i++) {
        if ((pow & (1u << 31)) != 0) {
            pow <<= 1;
            break;
        } else {
            pow <<= 1;
        }
    }
    i++;
    uint64_t s = num;
    for (; i < 32; i++) {
        s *= s;
        s %= modulus;
        if ((pow & (1u << 31)) != 0) {
            s *= num;
            s %= modulus;
        }
        pow <<= 1;
    }
    return s;
}

int num(char *str) {
    char *endptr;
    errno = 0;
    int i = strtol(str, &endptr, 10);
    if (str == endptr) {
        fprintf(stderr, "not a single character was converted\n");
        exit(EXIT_FAILURE);
    }
    if (errno) {
        perror("strtol");
        exit(EXIT_FAILURE);
    }
    return i;
}
