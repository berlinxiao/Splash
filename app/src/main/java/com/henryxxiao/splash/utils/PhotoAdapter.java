package com.henryxxiao.splash.utils;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.henryxxiao.splash.R;
import com.henryxxiao.splash.data.SplashPhoto;

public class PhotoAdapter extends PagedListAdapter<SplashPhoto, PhotoAdapter.PhotoViewHolder> {
    public PhotoAdapter() { super(DIFF_CALLBACK); }
    private OnItemClickListener onItemClickListener;

    private static DiffUtil.ItemCallback<SplashPhoto> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<SplashPhoto>() {
                @Override
                public boolean areItemsTheSame(@NonNull SplashPhoto oldItem, @NonNull SplashPhoto newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @SuppressLint("DiffUtilEquals")
                @Override
                public boolean areContentsTheSame(@NonNull SplashPhoto oldItem, @NonNull SplashPhoto newItem) {
                    return oldItem.equals(newItem);
                }
            };

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_splash_photo, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        SplashPhoto photoItem = getItem(position);
        if(photoItem != null){
            //设置图片长宽比
            holder.ImageView.setAspectRatio(photoItem.getHeight()/photoItem.getWidth());
            //设置图片背景颜色
            holder.ImageView.setBackgroundColor(Color.parseColor(photoItem.getColor()));
            //设置作者名
            holder.textView.setText(photoItem.getUser().getName());
            //加载图片
            GlideApp.with(holder.itemView.getContext())
                    .load(photoItem.getUrls().getSmall())
                    .into(holder.ImageView);

            holder.ImageView.setOnClickListener(v -> onItemClickListener.onItemClick(v,photoItem,holder.ImageView));
        }
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder{

        AspectRatioImageView ImageView;
        TextView textView;

        private PhotoViewHolder(@NonNull View itemView) {
            super(itemView);

            ImageView = itemView.findViewById(R.id.item_splash_photo_image);
            textView = itemView.findViewById(R.id.item_splash_photo_text);
        }
    }

    public interface OnItemClickListener {
        void onItemClick(View view,SplashPhoto splashPhoto,AspectRatioImageView imageView);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }
}
