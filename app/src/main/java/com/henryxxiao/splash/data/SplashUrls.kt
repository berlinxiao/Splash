package com.henryxxiao.splash.data

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class SplashUrls(
        val thumb: String?,
        val small: String,
        val medium: String?,
        val regular: String?,
        val large: String?,
        val full: String?,
        val raw: String?
) : Parcelable