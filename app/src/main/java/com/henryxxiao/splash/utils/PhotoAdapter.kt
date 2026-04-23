package com.henryxxiao.splash.utils

import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.bumptech.glide.request.RequestOptions
import com.henryxxiao.splash.R
import com.henryxxiao.splash.data.SplashPhoto
import androidx.core.graphics.toColorInt
import androidx.core.view.ViewCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.card.MaterialCardView
import androidx.core.graphics.drawable.toDrawable
import com.henryxxiao.splash.utils.view.SquircleImageView

/**
 * 全局统一的瀑布流图片适配器
 * @param showUserInfo 是否显示底部的用户头像和名字（首页为 true，个人主页为 false）
 */
class PhotoAdapter(
    private val showUserInfo: Boolean = true
) : PagingDataAdapter<SplashPhoto, PhotoAdapter.PhotoViewHolder>(DIFF_CALLBACK) {

    private var onItemClickListener: ((photo: SplashPhoto, imageView: MaterialCardView) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_splash_photo, parent, false)
        return PhotoViewHolder(view)
    }

    override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
        val photoItem = getItem(position) ?: return

        // 设置图片宽高比
        val ratio = photoItem.height / photoItem.width
        holder.mainImageView.aspectRatio = ratio

        // 先准备好 Color 降级方案 (ColorDrawable)
        val fallbackColor = try {
            (photoItem.color ?: "#333333").toColorInt()
        } catch (_: Exception) {
            "#333333".toColorInt()
        }
        val colorDrawable = fallbackColor.toDrawable()
        // 尝试生成 BlurHash (BitmapDrawable)
        var placeholderDrawable: Drawable = colorDrawable // 默认指向纯色
        if (!photoItem.blurHash.isNullOrEmpty()) {
            // 性能秘诀：BlurHash 只需要解码成极小分辨率 (比如宽度 30px)，然后让 ImageView 自动拉伸模糊它
            val blurWidth = 30
            // 确保高度不为 0 (使用 coerceAtLeast)
            val blurHeight = (blurWidth * ratio).toInt().coerceAtLeast(1)

            val blurBitmap = BlurHashCache.getBitmap(photoItem.blurHash, blurWidth, blurHeight)
            if (blurBitmap != null) {
                // 解码成功，将 Bitmap 包装成 Drawable
                placeholderDrawable = blurBitmap.toDrawable(holder.itemView.resources)
            }
        }

        // 根据场景动态控制用户信息显示
        if (showUserInfo) {
            holder.userAvatarView.visibility = View.VISIBLE
            holder.textView.visibility = View.VISIBLE

            holder.textView.text = photoItem.user.name
            // 加载头像
            Glide.with(holder.itemView.context)
                .load(photoItem.user.profileImage?.large ?: photoItem.user.profileImage?.small)
                .apply(RequestOptions.bitmapTransform(CircleCrop()))
                .placeholder(colorDrawable) // 颜色占位
                .into(holder.userAvatarView)
        } else {
            // 个人主页不需要显示头像和名字，直接隐藏
            holder.userAvatarView.visibility = View.GONE
            holder.textView.visibility = View.GONE
        }

        // 加载主图
        Glide.with(holder.itemView.context)
            .load(photoItem.urls.small)
            .placeholder(placeholderDrawable) // 占位：加载中显示 BlurHash 或纯色
            //.error(colorDrawable)             // 错误：如果断网加载失败，退回纯色
            //.transition(DrawableTransitionOptions.withCrossFade(300)) // 淡入：从模糊慢慢对焦变清晰！
            .into(holder.mainImageView)

        // 提前设置唯一 transitionName
        holder.cardView.transitionName = "photo_${photoItem.id}"

        // 点击事件回调 (个人主页的代码中，忽略 View 参数即可)
        holder.cardView.setOnClickListener { _ ->
            onItemClickListener?.invoke( photoItem, holder.cardView)
        }
    }

    fun setOnItemClickListener(listener: (SplashPhoto, MaterialCardView) -> Unit) {
        this.onItemClickListener = listener
    }

    class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val cardView: MaterialCardView =  itemView.findViewById(R.id.item_splash_photo_card)
        val mainImageView: SquircleImageView = itemView.findViewById(R.id.item_splash_photo_image)
        val userAvatarView: ImageView = itemView.findViewById(R.id.item_splash_photo_user_imageView)
        val textView: TextView = itemView.findViewById(R.id.item_splash_photo_text)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SplashPhoto>() {
            override fun areItemsTheSame(oldItem: SplashPhoto, newItem: SplashPhoto): Boolean = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: SplashPhoto, newItem: SplashPhoto): Boolean = oldItem == newItem
        }
    }
}