package com.henryxxiao.splash.ui.set;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.henryxxiao.splash.R;

import java.util.List;

public class SetAdapter extends RecyclerView.Adapter<SetAdapter.SetViewHolder> {
    private List<SetItem> mList;
    private OnItemClickListener onItemClickListener;

    public SetAdapter (List<SetItem> list){
        this.mList = list;
    }

    @NonNull
    @Override
    public SetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_set, parent, false);
        return new SetViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SetViewHolder holder, int position) {
        SetItem setItem = mList.get(position);
        holder.textView.setText(setItem.getItemName());
        holder.textView_Status.setText(setItem.getItemStatus());
        holder.imageViewIcon.setImageResource(setItem.getImageId());
        holder.imageViewIcon.setColorFilter(setItem.getImageColor());
        holder.imageViewBack.setColorFilter(setItem.getBackColor());

        holder.itemView.setOnClickListener(v -> onItemClickListener.onItemClick(position, holder.textView_Status));
    }

    public interface OnItemClickListener {
        void onItemClick(int position, TextView textView_Status);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    @Override
    public int getItemCount() {
        return mList == null ? 0 : mList.size();
    }


    public static class SetViewHolder extends RecyclerView.ViewHolder {

        ImageView imageViewIcon,imageViewBack;
        TextView textView, textView_Status;

        public SetViewHolder(View itemView) {
            super(itemView);
            imageViewIcon = itemView.findViewById(R.id.item_set_icon);
            imageViewBack = itemView.findViewById(R.id.item_set_icon_back);
            textView = itemView.findViewById(R.id.item_set_textView);
            textView_Status = itemView.findViewById(R.id.item_set_textView_status);
        }
    }
}
