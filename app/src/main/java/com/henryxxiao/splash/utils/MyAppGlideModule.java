package com.henryxxiao.splash.utils;

import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.module.AppGlideModule;
/*
 设置清单解析，设置为false，避免添加相同的modules两次，防止Glide打包错误
  */
@GlideModule
public final class MyAppGlideModule extends AppGlideModule {

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
