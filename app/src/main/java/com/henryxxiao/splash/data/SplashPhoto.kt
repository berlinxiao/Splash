package com.henryxxiao.splash.data

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class SplashPhoto(
        val id: String,
        val created_at: String,
        val width: Double,
        val height: Double,
        val color: String? = "#000000",
        val likes: Int,
        val description: String?,
        val urls: SplashUrls,
        val links: SplashLinks,
        val user: SplashUser
) : Parcelable