package com.henryxxiao.splash.utils.view

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.paging.LoadState
import androidx.paging.LoadStateAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textview.MaterialTextView
import com.henryxxiao.splash.R

class PagingLoadStateAdapter(
    private val retry: () -> Unit
) : LoadStateAdapter<PagingLoadStateAdapter.LoadStateViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, loadState: LoadState): LoadStateViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_paging_footer, parent, false)
        return LoadStateViewHolder(view, retry)
    }

    override fun onBindViewHolder(holder: LoadStateViewHolder, loadState: LoadState) {
        holder.bind(loadState)
    }

    class LoadStateViewHolder(view: View, retry: () -> Unit) : RecyclerView.ViewHolder(view) {
        private val progressBar: CircularProgressIndicator = view.findViewById(R.id.footer_progress)
        private val errorMsg: MaterialTextView = view.findViewById(R.id.footer_text)
        private val retryButton: MaterialButton = view.findViewById(R.id.footer_btn_retry)

        init {
            // 点击重试按钮，触发 Paging3 原生 retry()
            retryButton.setOnClickListener { retry.invoke() }
        }

        fun bind(loadState: LoadState) {
            // 🌟 黑科技：让 Footer 在瀑布流中独占一行 (Full Span)，否则它会被挤在左边一列！
            val layoutParams = itemView.layoutParams
            if (layoutParams is StaggeredGridLayoutManager.LayoutParams) {
                layoutParams.isFullSpan = true
            }

            // 根据状态控制 UI 可见性
            progressBar.isVisible = loadState is LoadState.Loading
            retryButton.isVisible = loadState is LoadState.Error
            errorMsg.isVisible = loadState is LoadState.Error

//            if (loadState is LoadState.Error) {
//                errorMsg.text = loadState.error.localizedMessage ?: "Load Failed"
//            }
        }
    }
}