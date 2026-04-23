package com.henryxxiao.splash.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
@Serializable
@Parcelize
data class SplashExif(
    val make: String? = null,
    val model: String? = null,
    val name: String? = null,
    @SerialName("exposure_time") val exposureTime: String? = null,
    val aperture: String? = null,
    @SerialName("focal_length") val focalLength: String? = null,
    val iso: Int? = null
) : Parcelable
