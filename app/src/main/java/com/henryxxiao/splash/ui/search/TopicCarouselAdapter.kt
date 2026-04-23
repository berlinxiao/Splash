package com.henryxxiao.splash.ui.search

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.henryxxiao.splash.R
import com.henryxxiao.splash.data.SplashTopic
import com.henryxxiao.splash.utils.BlurHashCache
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.toColorInt
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter

class TopicCarouselAdapter(
    private val onItemClick: (SplashTopic, View, Int) -> Unit
) : ListAdapter<SplashTopic, TopicCarouselAdapter.TopicViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TopicViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_carousel_topic, parent, false)
        return TopicViewHolder(view)
    }

    override fun onBindViewHolder(holder: TopicViewHolder, position: Int) {
        val topic = getItem(position)

        holder.titleView.text = topic.title

        val coverPhoto = topic.coverPhoto
        val coverUrl = coverPhoto?.urls?.small ?: coverPhoto?.urls?.regular

        // 成 Color 降级背景
        val fallbackColor = try {
            (coverPhoto?.color ?: "#333333").toColorInt()
        } catch (_: Exception) {
            "#333333".toColorInt()
        }
        val colorDrawable = fallbackColor.toDrawable()
        var placeholderDrawable: Drawable = colorDrawable

        // BlurHash 占位图
        val blurHash = coverPhoto?.blurHash
        if (!blurHash.isNullOrEmpty() && coverPhoto.width > 0 && coverPhoto.height > 0) {
            // 计算封面的真实宽高比
            val ratio = coverPhoto.height.toFloat() / coverPhoto.width.toFloat()
            // 依然只解码极小分辨率 (宽度 30px)，极大节省 CPU 性能
            val blurWidth = 30
            val blurHeight = (blurWidth * ratio).toInt().coerceAtLeast(1)

            // 从 LruCache 获取或解码 BlurHash
            val blurBitmap = BlurHashCache.getBitmap(blurHash, blurWidth, blurHeight)
            if (blurBitmap != null) {
                placeholderDrawable = blurBitmap.toDrawable(holder.itemView.resources)
            }
        }

        // 加载封面图 (使用 regular 尺寸保证画质，配合淡入效果)
        Glide.with(holder.itemView.context)
            .load(coverUrl)
            .placeholder(placeholderDrawable) // 占位：加载中显示 BlurHash 或纯色
            //.error(colorDrawable)             // 错误：如果断网加载失败，退回纯色
            .transition(DrawableTransitionOptions.withCrossFade(300)) // 300ms 淡入，显得高级
            .into(holder.imageView)

        // 给整个卡片根布局绑定唯一标识
        holder.itemView.transitionName = "topic_card_${topic.id}"

        // 点击事件回调
        holder.itemView.setOnClickListener {
            onItemClick(topic, holder.itemView, holder.bindingAdapterPosition)
        }
    }

    companion object {
        // DiffUtil 差异比对器，它会在后台线程计算新旧数据的差异，实现无损刷新
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SplashTopic>() {
            override fun areItemsTheSame(old: SplashTopic, new: SplashTopic) = old.id == new.id // ID 相同代表是同一个实体
            override fun areContentsTheSame(old: SplashTopic, new: SplashTopic) = old == new // Data Class 自带的 equals 比较所有字段
        }
    }

    class TopicViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imageView: ImageView = view.findViewById(R.id.carousel_image)
        val titleView: TextView = view.findViewById(R.id.carousel_title)
    }
}