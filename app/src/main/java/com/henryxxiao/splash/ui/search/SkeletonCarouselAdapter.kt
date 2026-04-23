package com.henryxxiao.splash.ui.search

import android.graphics.drawable.Animatable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.henryxxiao.splash.R

class SkeletonCarouselAdapter(private val count: Int) : RecyclerView.Adapter<SkeletonCarouselAdapter.SkeletonViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SkeletonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_carousel_skeleton, parent, false)
        return SkeletonViewHolder(view)
    }

    override fun onBindViewHolder(holder: SkeletonViewHolder, position: Int) {
        // 🌟 核心：获取 ImageView 的 background 并强转为 Animatable，然后 start()
        val drawable = holder.imageView.background
        if (drawable is Animatable) {
            drawable.start()
        }
    }

    override fun getItemCount(): Int = count

    class SkeletonViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.skeleton_image)
    }
}