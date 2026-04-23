package com.henryxxiao.splash.data

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Parcelize
data class SplashLinks(
        val self: String,
        val html: String,
        val photos: String? = null,
        val likes: String? = "0",
        val portfolio: String? = null,
        val download: String? = null,
        @SerialName("download_location") val downloadLocation: String? = null
) : Parcelable
