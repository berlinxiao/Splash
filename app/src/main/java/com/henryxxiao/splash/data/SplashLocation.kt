package com.henryxxiao.splash.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
@Serializable
@Parcelize
data class SplashLocation(
    val city: String? = null,
    val country: String? = null
) : Parcelable
