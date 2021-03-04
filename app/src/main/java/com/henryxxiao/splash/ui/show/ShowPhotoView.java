package com.henryxxiao.splash.ui.show;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;

import com.henryxxiao.splash.MainActivity;
import com.henryxxiao.splash.R;

import com.henryxxiao.splash.databinding.ActivityShowPhotoviewBinding;
import com.henryxxiao.splash.utils.GlideApp;

public class ShowPhotoView extends AppCompatActivity {
    ActivityShowPhotoviewBinding binding;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityShowPhotoviewBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        //判断主题，设置背景色
        if (MainActivity.style == 1){
            binding.PhotoViewImageView.setBackgroundColor(getColor(R.color.colorMyBlack));
        }

        Bundle bundle = this.getIntent().getExtras();
        if (bundle != null) {
            GlideApp.with(this).load(bundle.getString("URL")).into(binding.PhotoViewImageView);
        }

        //全屏化，隐藏状态栏和导航栏
        View decorView = this.getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }
}