package com.darsh.multipleimageselect.helpers;

import android.annotation.SuppressLint;

import com.bumptech.glide.request.RequestOptions;
import com.darsh.multipleimageselect.R;

public class GlideHelper {

    @SuppressLint("CheckResult")
    public static RequestOptions getDefaultRequestOptions() {
        RequestOptions requestOptions = new RequestOptions();
        requestOptions.placeholder(R.drawable.image_placeholder);
        requestOptions.error(android.R.drawable.stat_sys_warning);
        return requestOptions;
    }

}
