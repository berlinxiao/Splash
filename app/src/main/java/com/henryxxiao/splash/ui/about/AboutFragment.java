package com.henryxxiao.splash.ui.about;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;
import com.henryxxiao.splash.R;
import com.henryxxiao.splash.databinding.FragmentAboutBinding;

public class AboutFragment extends Fragment {
    private FragmentAboutBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAboutBinding.inflate(inflater, container, false);
        setHasOptionsMenu(true);

        final Animation under = AnimationUtils.loadAnimation(getActivity(), R.anim.about_in_under);
        binding.aboutImageViewLogo.setAnimation(under);
        binding.aboutTextViewAppname.setAnimation(under);
        binding.aboutTextViewSlogan.setAnimation(under);

//        binding.aboutUnsplash.setAnimation(under);
//        binding.aboutVersion.setAnimation(under);
//        binding.aboutLog.setAnimation(under);
//        binding.aboutDeveloperName.setAnimation(under);
//        binding.aboutDeveloperWeb.setAnimation(under);
//        binding.aboutDeveloperEmail.setAnimation(under);
//        binding.aboutDeveloperReward.setAnimation(under);
//        binding.aboutLibraryGlide.setAnimation(under);
//        binding.aboutLibraryGson.setAnimation(under);
//        binding.aboutLibraryRetrofit.setAnimation(under);

        binding.aboutCardView1.setAnimation(under);
        binding.aboutCardView2.setAnimation(under);
        binding.aboutCardView3.setAnimation(under);

        return binding.getRoot();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        binding.aboutLog.setOnClickListener(v -> {
            AlertDialog alertDialog = new AlertDialog.Builder(binding.getRoot().getContext())
                    .setTitle(getString(R.string.about_log))//标题
                    .setMessage(getString(R.string.about_log_text))//内容
                    .create();
            alertDialog.show();
        });

        binding.aboutDeveloperEmail.setOnClickListener(v -> {
            /*
            部分机型启动邮箱会报错
            android.content.ActivityNotFoundException
            No Activity found to handle Intent { act=android.intent.action.SENDTO dat=mailto:xxxxxxx@xxxxxxx.xxx (has extras) }
            多发生在7.0版本
             */
            try {
                Intent email = new Intent(Intent.ACTION_SENDTO);
                email.setData(Uri.parse("mailto:nk11112@outlook.com"));
                email.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.about_send_email_title));
                email.putExtra(Intent.EXTRA_TEXT, getString(R.string.about_send_email_text));
                startActivity(email);
            }catch (Exception e){
                Snackbar.make(binding.aboutLinearLayout, getString(R.string.about_email_error), Snackbar.LENGTH_SHORT).show();
            }
        });

        binding.aboutDeveloperReward.setOnClickListener(v -> new BottomDialogWeChat().show(getActivity().getSupportFragmentManager(), null));
        //binding.aboutDeveloperReward.setOnClickListener(v -> new WeChatBottomShow().show(getActivity().getSupportFragmentManager(), null));

        binding.aboutUnsplash.setOnClickListener(v -> goToURL("https://unsplash.com/"));
        binding.aboutDeveloperWeb.setOnClickListener(v -> goToURL("https://henryxxiao.github.io/"));
        binding.aboutLibraryGlide.setOnClickListener(v -> goToURL("https://github.com/bumptech/glide"));
        binding.aboutLibraryGson.setOnClickListener(v -> goToURL("https://github.com/google/gson"));
        binding.aboutLibraryLottie.setOnClickListener(v -> goToURL("https://github.com/airbnb/lottie-android"));
        binding.aboutLibraryLB.setOnClickListener(v -> goToURL("https://github.com/leandroBorgesFerreira/LoadingButtonAndroid"));
        binding.aboutLibraryPhotoview.setOnClickListener(v -> goToURL("https://github.com/chrisbanes/PhotoView"));
        binding.aboutLibraryRetrofit.setOnClickListener(v -> goToURL("https://github.com/square/retrofit"));
        binding.aboutLibraryRxjava.setOnClickListener(v -> goToURL("https://github.com/ReactiveX/RxJava"));
        binding.aboutLibraryCd.setOnClickListener(v -> goToURL("https://github.com/zerochl/ClassicDownload"));
    }

    public void goToURL(String link) {
        try {
            Uri uri = Uri.parse(link);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        }catch (Exception e){
            Snackbar.make(binding.aboutLinearLayout, getString(R.string.about_browser_error), Snackbar.LENGTH_SHORT).show();
        }
    }

    //隐藏menu
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.clear();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
