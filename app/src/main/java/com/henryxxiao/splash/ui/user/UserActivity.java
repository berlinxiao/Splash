package com.henryxxiao.splash.ui.user;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.henryxxiao.splash.MainActivity;
import com.henryxxiao.splash.R;
import com.henryxxiao.splash.databinding.ActivityUserBinding;
import com.henryxxiao.splash.ui.show.ShowActivity;
import com.henryxxiao.splash.ui.show.ShowPhotoView;
import com.henryxxiao.splash.data.SplashPhoto;
import com.henryxxiao.splash.utils.GlideApp;

/*
获取的Splash:bio(可能空),location(可能空)，total_photos
 */
public class UserActivity extends AppCompatActivity {
    ActivityUserBinding binding;
    SplashPhoto splashPhoto;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (MainActivity.style == 1) {
            setTheme(R.style.AppTheme_Dark);
            getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.colorMyBlack));
        }
        super.onCreate(savedInstanceState);
        binding = ActivityUserBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Bundle bundle = this.getIntent().getExtras();
        splashPhoto = bundle.getParcelable("Photo");

        binding.userTextViewUsername.setText(splashPhoto.getUser().getName());
        GlideApp.with(this)
                .load(splashPhoto.getUser().getProfile_image().getLarge())
                //圆形头像
                .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                .into(binding.userImageViewHead);

        //设置动画
        //final Animation under = AnimationUtils.loadAnimation(this, R.anim.user_in_under);
        final Animation top = AnimationUtils.loadAnimation(this, R.anim.user_in_top);
        binding.userTextViewUsername.setAnimation(top);
        if (splashPhoto.getUser().getLocation() != null) {
            binding.userTextViewLocation.setText(splashPhoto.getUser().getLocation());
        } else {
            binding.userLinearLocation.setVisibility(View.GONE);
            binding.userViewLine.setVisibility(View.GONE);
        }
        if (splashPhoto.getUser().getBio() != null) {
            binding.userTextViewBio.setText(splashPhoto.getUser().getBio());
            binding.userTextViewBio.setAnimation(top);
        } else {
            binding.userTextViewBio.setVisibility(View.GONE);
        }
        /*设置setText或者类似的方法中，如果引用了int类型数据Android系统就会主动去资源文件当中寻找，
        但是它不是一个资源文件ID， 所以就会报出bug。 将int型业务数据，转换成String类型(String.valueOf())即可。*/
        //String photos = binding.userTextViewPhotos.getText() + String.valueOf(splashPhoto.getUser().getTotal_photos());
        binding.userTextViewPhotos
                .setText(String.format(getString(R.string.user_photos), splashPhoto.getUser().getTotal_photos()));
        binding.userLinearLayoutLine.setAnimation(top);

        //判断是不是只有当前查看的这一张照片
        if (splashPhoto.getUser().getTotal_photos() < 2) {
            binding.userProgressBar.setVisibility(View.GONE);
            binding.userOnlyEmoji.setVisibility(View.VISIBLE);
            binding.userTextViewOnly.setVisibility(View.VISIBLE);
            binding.userOnlyEmoji.playAnimation();
        } else {
            loading();
        }

        //头像预览
        binding.userImageViewHead.setOnClickListener(v -> {
            Bundle bundleURL = new Bundle();
            //获得图片链接
            bundleURL.putString("URL", splashPhoto.getUser().getProfile_image().getLarge());
            Intent intent = new Intent(this, ShowPhotoView.class);
            intent.putExtras(bundleURL);//把数据通过Intent传到下一个Activity显示
            startActivity(intent);
        });
    }

    public void loading() {
        binding.userRecyclerview.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        binding.userRecyclerview.setHasFixedSize(true);
        binding.userRecyclerview.setItemAnimator(null);
        //NestedScrolling嵌套Recyclerview，防止卡顿
        //binding.userRecyclerview.setNestedScrollingEnabled(false);

        UserAdapter userAdapter = new UserAdapter();
        UserViewModel userViewModel = new UserViewModel();
        userViewModel.userViewModel(splashPhoto.getUser().getLinks().getPhotos());
        userViewModel.photoList.observe(this, userAdapter::submitList);
        userViewModel.loadStatus.observe(this, loadStatus -> {
            switch (loadStatus) {
                case SUCCESS:
                    binding.userProgressBar.setVisibility(View.GONE);
                    break;
                case NONETWORK:
                case FAILURE:
                    binding.userProgressBar.setVisibility(View.GONE);
                    binding.userTextViewOnly.setVisibility(View.VISIBLE);
                    binding.userTextViewOnly.setText(getString(R.string.home_network_error_title));
                    break;
            }
            //结束观察，移除节省资源
            userViewModel.loadStatus.removeObservers(this);
        });

        userAdapter.notifyDataSetChanged();
        binding.userRecyclerview.setAdapter(userAdapter);

        userAdapter.setOnItemClickListener((view, splashPhoto) -> {
            Bundle bundle = new Bundle();
            bundle.putParcelable("Photo", splashPhoto);
            Intent intent = new Intent(this, ShowActivity.class);
            intent.putExtras(bundle);//把数据通过Intent传到下一个Activity显示
            startActivity(intent);
        });
    }

}