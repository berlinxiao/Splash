package com.henryxxiao.splash.ui.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.henryxxiao.splash.R
import com.henryxxiao.splash.data.SplashUser

class SearchUserAdapter : PagingDataAdapter<SplashUser, SearchUserAdapter.UserViewHolder>(DIFF_CALLBACK) {

    private var onItemClickListener: ((user: SplashUser, imageView: ImageView) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_search_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = getItem(position) ?: return

        holder.tvUsername.text = user.name

        Glide.with(holder.itemView.context)
            .load(user.profileImage?.large ?: user.profileImage?.small)
            .placeholder(R.drawable.ic_account_circle)
            .transform(CircleCrop())
            .into(holder.ivAvatar)

        // 提前设置唯一 transitionName
        holder.ivAvatar.transitionName = "avatar_${user.id}"

        holder.itemView.setOnClickListener { onItemClickListener?.invoke(user,holder.ivAvatar) }
    }

    fun setOnItemClickListener(listener: (SplashUser, ImageView) -> Unit) { this.onItemClickListener = listener }

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivAvatar: ImageView = view.findViewById(R.id.item_search_user_imageView)
        val tvUsername: TextView = view.findViewById(R.id.item_search_user_text)
    }

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<SplashUser>() {
            override fun areItemsTheSame(old: SplashUser, new: SplashUser) = old.id == new.id
            override fun areContentsTheSame(old: SplashUser, new: SplashUser) = old == new
        }
    }
}