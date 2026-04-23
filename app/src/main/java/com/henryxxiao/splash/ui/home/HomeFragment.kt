package com.henryxxiao.splash.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityOptionsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.henryxxiao.splash.R;
import com.henryxxiao.splash.databinding.FragmentHomeBinding;
import com.henryxxiao.splash.ui.show.ShowActivity;
import com.henryxxiao.splash.utils.PhotoAdapter;

import java.util.Objects;

public class HomeFragment extends Fragment {
    private FragmentHomeBinding binding;
    private PhotoAdapter photoAdapter;
    private PhotoViewModel photoViewModel;
    private boolean swipeEnable = false;
    public static boolean isChange = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //保持生命周期
        setRetainInstance(true);
        photoAdapter = new PhotoAdapter();
        photoViewModel = new ViewModelProvider(this).get(PhotoViewModel.class);
        photoViewModel.load();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        loadData();
        binding.SwipeRefreshLayout.setOnRefreshListener(() -> {
            photoViewModel.load();
            loadData();
        });

        //当设置改变了预载选项时，返回到主界面自动刷新
        if (isChange) {
            /*
            当返回到界面时调用setRefreshing(true)，并不会出现转圈的进度条。
            这时SwipeRefreshLayout并没有初始化完成，需要使用线程。
            这个方法中的Runnable是在当前线程执行，所以不需要在使用Handler去更新UI
             */
            binding.SwipeRefreshLayout.post(() -> {
                binding.SwipeRefreshLayout.setRefreshing(true);
                photoViewModel.load();
                isChange = false;
                //防止无网络下设置后返回时点击到，产生冲突
                binding.homeButtonRetry.setEnabled(false);
                //延迟执行，让Swipe出现转动一会
                Handler handler = new Handler();
                handler.postDelayed(this::loadData,500);
            });
        }
        return binding.getRoot();
    }


    private void loadData(){
        //进入页面加载时不要开启下拉刷新，不然会起冲突
        binding.SwipeRefreshLayout.setEnabled(swipeEnable);
        binding.homeRecyclerview.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        //item的改变不会影响RecyclerView宽高的时候，可以设置为true让RecyclerView避免重新计算大小。
        binding.homeRecyclerview.setHasFixedSize(true);
        //防止item出现重新排列
        binding.homeRecyclerview.setItemAnimator(null);

        //photoViewModel.photoList.observe(getViewLifecycleOwner(), photoAdapter::submitList);
        photoViewModel.photoList.observe(getViewLifecycleOwner(), splashPhotos -> {
            photoAdapter.submitList(splashPhotos);
            binding.SwipeRefreshLayout.setRefreshing(false);
        });

        photoViewModel.loadStatus.observe(getViewLifecycleOwner(), loadStatus -> {
            switch (loadStatus) {
                case SUCCESS:
                    binding.progressBar.setVisibility(View.GONE);
                    binding.homeLottieEmoji.setVisibility(View.GONE);
                    binding.homeTextView.setVisibility(View.GONE);
                    binding.homeTextViewTitle.setVisibility(View.GONE);
                    binding.homeButtonRetry.setVisibility(View.GONE);
                    binding.homeRecyclerview.setVisibility(View.VISIBLE);
                    //结束观察，移除节省资源
                    //photoViewModel.loadStatus.removeObservers(getViewLifecycleOwner());
                    //加载成功就可以开启下拉刷新了
                    swipeEnable = true;
                    binding.SwipeRefreshLayout.setEnabled(true);
                    break;
                case NONETWORK:
                    if (swipeEnable){
                        binding.SwipeRefreshLayout.setRefreshing(false);
                    }
                    //恢复点击
                    binding.homeButtonRetry.setEnabled(true);
                    //有重试按钮，禁止下滑刷新
                    binding.SwipeRefreshLayout.setEnabled(false);
                    binding.homeRecyclerview.setVisibility(View.GONE);
                    binding.homeTextViewTitle.setText(getString(R.string.home_network_no_title));
                    binding.homeTextView.setText(getString(R.string.home_network_no));
                case FAILURE:
                    binding.progressBar.setVisibility(View.GONE);
                    binding.homeLottieEmoji.setVisibility(View.VISIBLE);
                    binding.homeTextViewTitle.setVisibility(View.VISIBLE);
                    binding.homeTextView.setVisibility(View.VISIBLE);
                    binding.homeButtonRetry.setVisibility(View.VISIBLE);
                    //播放Lottie动画
                    binding.homeLottieEmoji.playAnimation();
                    binding.homeButtonRetry.setOnClickListener(v -> {
                        //重试
                        binding.progressBar.setVisibility(View.VISIBLE);
                        binding.homeLottieEmoji.setVisibility(View.GONE);
                        binding.homeTextViewTitle.setVisibility(View.GONE);
                        binding.homeTextView.setVisibility(View.GONE);
                        binding.homeButtonRetry.setVisibility(View.GONE);
                        binding.homeTextViewTitle.setText(getString(R.string.home_network_error_title));
                        binding.homeTextView.setText(getString(R.string.home_network_error));
                        //延迟执行刷新，让progress转一会
                        Handler handler = new Handler();
                        handler.postDelayed(() -> Objects.requireNonNull(photoViewModel.photoList.getValue()).getDataSource().invalidate(),500);
                    });
                    break;
            }
        });

        photoAdapter.notifyDataSetChanged();
        binding.homeRecyclerview.setAdapter(photoAdapter);

        photoAdapter.setOnItemClickListener((view, splashPhoto, imageView) -> {
            Bundle bundle = new Bundle();
            bundle.putParcelable("Photo", splashPhoto);
            Intent intent = new Intent(getActivity(), ShowActivity.class);
            intent.putExtras(bundle);//把数据通过Intent传到下一个Activity显示
            //共享元素过渡动画
            ActivityOptionsCompat optionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(), imageView, "image");
            startActivity(intent, optionsCompat.toBundle());
            //startActivity(intent);
        });
    }
    /*
    fragment 的生命周期与 activity 的生命周期不同，
    并且该fragment可以超出其视图的生命周期，
    因此如果不将其设置为null，则可能会发生内存泄漏。
    */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
//    @Override
//    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
//        super.onActivityCreated(savedInstanceState);
//        photoAdapter.setOnItemClickListener(new PhotoAdapter.OnItemClickListener() {
//            @Override
//            public void onItemClick(View view, SplashPhoto splashPhoto) {
//                Bundle bundle = new Bundle();
//                bundle.putParcelable("Photo",splashPhoto);
//                Intent intent = new Intent(WaterfallActivity.this,ItemActivity.class);
//                intent.putExtras(bundle);//把数据通过Intent传到下一个Activitiy显示
//                startActivity(intent);
//            }
//        });
//    }
}
