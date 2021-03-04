package com.henryxxiao.splash.ui.about;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.henryxxiao.splash.R;

public class BottomDialogWeChat extends BottomSheetDialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        //背景透明
        View view = View.inflate(getContext(), R.layout.about_bottom_dialog_wechat, null);
        bottomSheetDialog.setContentView(view);
        ((View) view.getParent()).setBackgroundColor(Color.TRANSPARENT);

        BottomSheetBehavior.from((View) view.getParent())
                .setState(BottomSheetBehavior.STATE_EXPANDED);

        return bottomSheetDialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.about_bottom_dialog_wechat, container, false);

        LottieAnimationView lottieAnimationView = view.findViewById(R.id.dialog_imageView_lottie);
        TextView textView = view.findViewById(R.id.dialog_textView_emoji);

        Animation animation = AnimationUtils.loadAnimation(getContext(), R.anim.about_dialog_wechat_emoji);

        textView.setAnimation(animation);
        lottieAnimationView.playAnimation();

        return view;
    }
}
