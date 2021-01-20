#include <stdint.h>
#include <jni.h>
#include "search.h"
#include "bs_scroll.h"
#include "drscbtjni.h"
#include "color.h"
#include "bs_search.h"
#include "rk_search.h"

JNIEXPORT jint JNICALL
Java_com_drscbt_shared_piclocate_scrollfinder_ScrollFinderFuzzyNa_checkScrollBSFuzzyCall(
    JNIEnv *env, jobject this_obj,
    jintArray jarr_screen_a, jintArray jarr_screen_b, jint width, jint err_toler,
    jint clip_x, jint clip_y, jint clip_w, jint clip_h
) {
    jsize px_len = (*env)->GetArrayLength(env, jarr_screen_a);
    int32_t* screen_a = NULL;
    int32_t* screen_b = NULL;
    int32_t ret;
    
    if (px_len != (*env)->GetArrayLength(env, jarr_screen_a)) {
        ret = SCROLL_RES_ERR;
        goto f_return;
    }

    int height = px_len / width;

    screen_a = (*env)->GetIntArrayElements(env, jarr_screen_a, 0);
    screen_b = (*env)->GetIntArrayElements(env, jarr_screen_b, 0);

    Img screen_a_img;
    screen_a_img.px_cnt = px_len;
    screen_a_img.pxs = (struct pixel*)screen_a;
    screen_a_img.width = width;
    screen_a_img.height = height;
    
    Img screen_b_img;
    screen_b_img.px_cnt = px_len;
    screen_b_img.pxs = (struct pixel*)screen_b;
    screen_b_img.width = width;
    screen_b_img.height = height;

    ret = bs_find_scroll(screen_a_img, screen_b_img, clip_x, clip_y, clip_w, clip_h, err_toler);
    
    f_return:
    
    (*env)->ReleaseIntArrayElements(env, jarr_screen_a, screen_a, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, jarr_screen_b, screen_b, JNI_ABORT);
        
    return ret;
}

JNIEXPORT void JNICALL Java_com_drscbt_shared_color_ColorConv_rgb2hsvArrNaCall(
    JNIEnv *env, jclass kls, jintArray jpix_arr, jintArray jhsv_arr
) {
    jint* pixa_arr = (*env)->GetIntArrayElements(env, jpix_arr, 0);
    jsize pixa_arr_len = (*env)->GetArrayLength(env, jpix_arr);

    jint* hsva_arr = (*env)->GetIntArrayElements(env, jhsv_arr, 0);

    for (int i = 0; i < pixa_arr_len; i++) {
        struct pixel clr_in;
        struct pixel clr_out;
        clr_in = ((struct pixel*)pixa_arr)[i];
        clr_out = to_hsv(clr_in);
        hsva_arr[i] = *((int32_t*)(&clr_out)) | 0xFF;
    }

    (*env)->ReleaseIntArrayElements(env, jpix_arr, pixa_arr, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, jhsv_arr, hsva_arr, JNI_COMMIT_AND_FREE);
}

JNIEXPORT jint JNICALL Java_com_drscbt_shared_color_ColorConv_rgb2hsvOnePxNaCall(
    JNIEnv *env, jclass kls, jint rgba
) {
    struct pixel clr;
    struct pixel clr_out;
    clr = *((struct pixel*)(&rgba));
    clr_out = to_hsv(clr);
    return *((int32_t*)(&clr_out)) | 0xFF;
}

JNIEXPORT void JNICALL Java_com_drscbt_shared_color_ColorCondense_condenseNaCall(
    JNIEnv *env, jclass kls, jintArray jrgb_arr, jbyteArray jpic8_arr,
    jint bits_1, jint bits_2, jint bits_3
) {
    jint* rgb_arr = (*env)->GetIntArrayElements(env, jrgb_arr, 0);
    jsize rgb_arr_len = (*env)->GetArrayLength(env, jrgb_arr);

    jbyte* pic8_arr = (*env)->GetByteArrayElements(env, jpic8_arr, 0);

    struct pixel clr_in;
    struct pixel clr_out;
    for (int i = 0; i < rgb_arr_len; i++) {
        clr_in = ((struct pixel*)rgb_arr)[i];
        clr_out = to_hsv(clr_in);
        pic8_arr[i] = trunc_to_byte(clr_out.A, clr_out.B, clr_out.C, bits_1, bits_2, bits_3);
    }

    (*env)->ReleaseIntArrayElements(env, jrgb_arr, rgb_arr, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, jpic8_arr, pic8_arr, JNI_COMMIT_AND_FREE);
}

JNIEXPORT jbyte JNICALL Java_com_drscbt_shared_color_ColorCondense_truncToByteNaCall(
    JNIEnv *env, jclass kls,
    jbyte ci1, jbyte ci2, jbyte ci3, jint bits_1, jint bits_2, jint bits_3
) {
    return trunc_to_byte(ci1, ci2, ci3, bits_1, bits_2, bits_3);
}

JNIEXPORT jint JNICALL Java_com_drscbt_shared_utils_Utils_modPowNaCall(
    JNIEnv *env, jclass kls, jint num, jint power, jint modulus
) {
    return modpow(num, power, modulus);
}

JNIEXPORT jobject JNICALL
Java_com_drscbt_shared_piclocate_twodmatcher_TwoDBasicSumCrossNa_bsMatchCall(
    JNIEnv *env,
    jobject this_obj,
    jintArray jarr_text,
    jintArray jarr_pat,
    jint jtext_w,
    jint jpat_w,
    jint chan_err_toler
) {
    jsize jarr_text_l = (*env)->GetArrayLength(env, jarr_text);
    jsize jarr_pat_l = (*env)->GetArrayLength(env, jarr_pat);

    int text_h = jarr_text_l / jtext_w;
    int pat_h = jarr_pat_l / jpat_w;

    int32_t* text = (*env)->GetIntArrayElements(env, jarr_text, 0);
    int32_t* pat = (*env)->GetIntArrayElements(env, jarr_pat, 0);

    Img img_text;
    img_text.px_cnt = jarr_text_l;
    img_text.pxs = (struct pixel*)text;
    img_text.width = jtext_w;
    img_text.height = text_h;

    Img img_pat;
    img_pat.px_cnt = jarr_pat_l;
    img_pat.pxs = (struct pixel*)pat;
    img_pat.width = jpat_w;
    img_pat.height = pat_h;

    struct search_results results = bs_match(img_text, img_pat, chan_err_toler);

    jobject results_j_instance = j_matchresults_from_struct(env, results);

    free_search_results(results);

    (*env)->ReleaseIntArrayElements(env, jarr_text, text, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, jarr_pat, pat, JNI_ABORT);

    return results_j_instance;
}

JNIEXPORT jobject JNICALL
Java_com_drscbt_shared_piclocate_twodmatcher_RK2DCrossNa_rkMatchCall(
    JNIEnv *env,
    jobject this_obj,
    jintArray jarr_text,
    jintArray jarr_pat,
    jint jtext_w,
    jint jpat_w
) {
    jsize jarr_text_l = (*env)->GetArrayLength(env, jarr_text);
    jsize jarr_pat_l = (*env)->GetArrayLength(env, jarr_pat);

    int text_h = jarr_text_l / jtext_w;
    int pat_h = jarr_pat_l / jpat_w;

    int32_t* text = (*env)->GetIntArrayElements(env, jarr_text, 0);
    int32_t* pat = (*env)->GetIntArrayElements(env, jarr_pat, 0);

    Img img_text;
    img_text.px_cnt = jarr_text_l;
    img_text.pxs = (struct pixel*)text;
    img_text.width = jtext_w;
    img_text.height = text_h;

    Img img_pat;
    img_pat.px_cnt = jarr_pat_l;
    img_pat.pxs = (struct pixel*)pat;
    img_pat.width = jpat_w;
    img_pat.height = pat_h;

    struct search_results results = rk_match(img_text, img_pat);

    jobject results_j_instance = j_matchresults_from_struct(env, results);

    free_search_results(results);

    (*env)->ReleaseIntArrayElements(env, jarr_text, text, JNI_ABORT);
    (*env)->ReleaseIntArrayElements(env, jarr_pat, pat, JNI_ABORT);

    return results_j_instance;
}

jobject j_matchresults_from_struct(JNIEnv *env, struct search_results results) {
    jclass hset_cls = (*env)->FindClass(env, "java/util/HashSet");
    jmethodID hset_cls_constructor = (*env)->GetMethodID(env, hset_cls, "<init>", "()V");

    jobject set = (*env)->NewObject(env, hset_cls, hset_cls_constructor);
    jmethodID hset_cls_add = (*env)->GetMethodID(env, hset_cls, "add", "(Ljava/lang/Object;)Z");

    for (int ridx = 0; ridx < results.res_count; ridx++) {
        struct match_item occurrence = *(results.occurrences + ridx);
        jobject pnt = mk_pnt(env, occurrence.x, occurrence.y);
        (*env)->CallBooleanMethod(env, set, hset_cls_add, pnt);
    }

    jclass results_jclass = (*env)->FindClass(env, "com/drscbt/shared/piclocate/twodmatcher/NativePicSearchResults");
    jobject results_j_instance = (*env)->AllocObject(env, results_jclass);

    jfieldID coll_cnt_fld = (*env)->GetFieldID(env, results_jclass, "collisionsCnt", "I");
    jfieldID matches_fld = (*env)->GetFieldID(env, results_jclass, "matches", "Ljava/util/Set;");

    (*env)->SetIntField(env, results_j_instance, coll_cnt_fld, results.collisions);
    (*env)->SetObjectField(env, results_j_instance, matches_fld, set);

    return results_j_instance;
}

jobject mk_pnt(JNIEnv *env, int x, int y) {
    jclass pnt_cls = (*env)->FindClass(env, "com/drscbt/shared/piclocate/Point");
    jmethodID pnt_cls_constructor = (*env)->GetMethodID(env, pnt_cls, "<init>", "(II)V");
    jobject new_pnt_obj = (*env)->NewObject(env, pnt_cls, pnt_cls_constructor, x, y);
    return new_pnt_obj;
}
