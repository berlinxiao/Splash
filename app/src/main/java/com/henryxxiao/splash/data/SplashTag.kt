package com.henryxxiao.splash.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class SplashTag(
    val title: String? = null
) : Parcelable
