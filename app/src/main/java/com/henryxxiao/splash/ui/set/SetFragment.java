package com.henryxxiao.splash.ui.set;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.android.material.snackbar.Snackbar;
import com.henryxxiao.splash.MainActivity;
import com.henryxxiao.splash.R;
import com.henryxxiao.splash.databinding.FragmentSetBinding;
import com.henryxxiao.splash.ui.home.HomeFragment;
import com.henryxxiao.splash.utils.LocalUtils;
import com.henryxxiao.splash.utils.MySP;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SetFragment extends Fragment {
    private FragmentSetBinding binding;
    private List<SetItem> setItemList = new ArrayList<>();
    private MySP sp;
    int preview;
    int download;
    int load;
    int language;
    int style;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSetBinding.inflate(inflater, container, false);

        sp = new MySP(getActivity());
        setHasOptionsMenu(true);

        preview = sp.getmPreview();
        download = sp.getmDownload();
        load = sp.getmLoad();
        language = sp.getmLanguage();
        style = sp.getmStyle();

        String previewStatus = getString(R.string.set_fluent);
        String downloadStatus = getString(R.string.set_regular);
        String loadStatus = getString(R.string.set_collection);
        String languageStatus = getString(R.string.set_follow);
        String styleStatus = getString(R.string.set_light);
        int previewColor = 0xFF6fd1f7;
        int downloadColor = 0xFF7540ee;
        int loadColor = 0xFF44dc94;
        int aboutColor = 0xFFff9441;
        int languageColor = 0xFFea4637;
        int styleColor = 0xFF9fa1a5;
        int previewBackColor = 0xFFebf7ff;
        int downloadBackColor = 0xFFf1ecfd;
        int loadBackColor = 0xFFedfbf3;
        int aboutBackColor = 0xFFfff7f1;
        int languageBackColor = 0xFFfce3e2;
        int styleBackColor = 0xFFf1f1f3;
        if (preview == 0) {
            previewStatus = getString(R.string.set_hd);
        }
        switch (download) {
            case 0:
                downloadStatus = getString(R.string.set_raw);
                break;
            case 1:
                downloadStatus = getString(R.string.set_full);
                break;
        }
        switch (load){
            case 1:
                loadStatus = getString(R.string.set_popular);
                break;
            case 2:
                loadStatus = getString(R.string.set_newest);
                break;
        }
        switch (language) {
            case 1:
                languageStatus = getString(R.string.set_english);
                break;
            case 2:
                languageStatus = getString(R.string.set_japanese);
                break;
            case 3:
                languageStatus = getString(R.string.set_chinese_simple);
                break;
            case 4:
                languageStatus = getString(R.string.set_chinese_tw);
                break;
        }
        if (style == 1) {
            styleStatus = getString(R.string.set_dark);
        }
        //暗黑主题下图标的颜色
        if (MainActivity.style == 1) {
            previewColor = 0xFF0094e0;
            downloadColor = 0xFF4309e4;
            loadColor = 0xFF00b85c;
            aboutColor = 0xFFf88615;
            languageColor = 0xFFc93330;
            styleColor = 0xFF333333;
            previewBackColor = 0xFF86d4ff;
            downloadBackColor = 0xFFaa91ef;
            loadBackColor = 0xFF8fedb8;
            aboutBackColor = 0xFFeacebd;
            languageBackColor = 0xFFf1bbb6;
            styleBackColor = 0xFF9fa1a5;
        }

        //八进制颜色0xFF
        SetItem previewItem = new SetItem(getString(R.string.set_preview), previewStatus, R.drawable.ic_set_preview, previewColor, previewBackColor);
        setItemList.add(previewItem);
        SetItem downloadItem = new SetItem(getString(R.string.set_download), downloadStatus, R.drawable.ic_set_download, downloadColor, downloadBackColor);
        setItemList.add(downloadItem);
        SetItem loadItem = new SetItem(getString(R.string.set_load), loadStatus, R.drawable.ic_set_photo_home, loadColor, loadBackColor);
        setItemList.add(loadItem);
        SetItem languageItem = new SetItem(getString(R.string.set_language), languageStatus, R.drawable.ic_set_language, languageColor, languageBackColor);
        setItemList.add(languageItem);
        SetItem styleItem = new SetItem(getString(R.string.set_style), styleStatus, R.drawable.ic_set_style, styleColor, styleBackColor);
        setItemList.add(styleItem);
        SetItem aboutItem = new SetItem(getString(R.string.menu_about), "", R.drawable.ic_set_about, aboutColor, aboutBackColor);
        setItemList.add(aboutItem);

        return binding.getRoot();
    }

    /*
    分段处理，不然界面会出现卡顿
     */
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        GridLayoutManager managerGrid = new GridLayoutManager(getActivity(), 2);
        binding.setRecyclerView.setLayoutManager(managerGrid);
        binding.setRecyclerView.setHasFixedSize(true);


        SetAdapter setAdapter = new SetAdapter(setItemList);
        binding.setRecyclerView.setAdapter(setAdapter);

        setAdapter.setOnItemClickListener((position, textView) -> {
            switch (position) {
                case 0:
                    showPreviewDialog(textView);
                    break;
                case 1:
                    showDownloadDialog(textView);
                    break;
                case 2:
                    showLoadDialog(textView);
                    break;
                case 3:
                    showLanguageDialog(textView);
                    break;
                case 4:
                    showStyleDialog(textView);
                    break;
                case 5:
                    if (getView() != null)
                    Navigation.findNavController(getView()).navigate(R.id.nav_about);
                    break;
            }
        });
    }

    //预览对话框
    int previewChoice;

    private void showPreviewDialog(TextView textView) {
        final String[] items = {getString(R.string.set_hd), getString(R.string.set_fluent)};
        AlertDialog.Builder previewDialog = new AlertDialog.Builder(binding.getRoot().getContext());
        previewDialog.setTitle(getString(R.string.set_preview));
        //第二个参数是显示时选中的选项
        previewDialog.setSingleChoiceItems(items, preview,
                (dialog, which) -> previewChoice = which);
        previewDialog.setPositiveButton(getString(R.string.set_ok), (dialog, which) -> {
            switch (previewChoice) {
                case 0:
                    sp.setmPreview(0);
                    preview = 0;
                    textView.setText(getString(R.string.set_hd));
                    break;
                case 1:
                    sp.setmPreview(1);
                    preview = 1;
                    textView.setText(getString(R.string.set_fluent));
                    break;
            }
        });
        previewDialog.show();
    }

    //下载对话框
    int downloadChoice;

    private void showDownloadDialog(TextView textView) {
        final String[] items = {getString(R.string.set_raw), getString(R.string.set_full), getString(R.string.set_regular)};
        AlertDialog.Builder downloadDialog = new AlertDialog.Builder(binding.getRoot().getContext());
        downloadDialog.setTitle(getString(R.string.set_download));
        //第二个参数是显示时选中的选项
        downloadDialog.setSingleChoiceItems(items, download,
                (dialog, which) -> downloadChoice = which);
        downloadDialog.setPositiveButton(getString(R.string.set_ok), (dialog, which) -> {
            switch (downloadChoice) {
                case 0:
                    sp.setmDownload(0);
                    download = 0;
                    textView.setText(getString(R.string.set_raw));
                    break;
                case 1:
                    sp.setmDownload(1);
                    download = 1;
                    textView.setText(getString(R.string.set_full));
                    break;
                case 2:
                    sp.setmDownload(2);
                    download = 2;
                    textView.setText(getString(R.string.set_regular));
                    break;
            }
        });
        downloadDialog.show();
    }

    //预载对话框
    int loadChoice;

    private void showLoadDialog(TextView textView) {
        int tempLoad = load;//用于判断有没有改变
        final String[] items = {getString(R.string.set_collection), getString(R.string.set_popular), getString(R.string.set_newest)};
        AlertDialog.Builder loadDialog = new AlertDialog.Builder(binding.getRoot().getContext());
        loadDialog.setTitle(getString(R.string.set_load));
        //第二个参数是显示时选中的选项
        loadDialog.setSingleChoiceItems(items, load,
                (dialog, which) -> loadChoice = which);
        loadDialog.setPositiveButton(getString(R.string.set_ok), (dialog, which) -> {
            switch (loadChoice) {
                case 0:
                    sp.setmLoad(0);
                    load = 0;
                    textView.setText(getString(R.string.set_collection));
                    break;
                case 1:
                    sp.setmLoad(1);
                    load = 1;
                    textView.setText(getString(R.string.set_popular));
                    break;
                case 2:
                    sp.setmLoad(2);
                    load = 2;
                    textView.setText(getString(R.string.set_newest));
                    break;
            }
            if (tempLoad != load){
                HomeFragment.isChange = true;
                showSnackbar(getString(R.string.set_already));
            }
        });
        loadDialog.show();
    }

    //语言对话框
    int languageChoice;

    private void showLanguageDialog(TextView textView) {
        final String[] items = {getString(R.string.set_follow), getString(R.string.set_english),getString(R.string.set_japanese), getString(R.string.set_chinese_simple),getString(R.string.set_chinese_tw)};
        AlertDialog.Builder languageDialog = new AlertDialog.Builder(binding.getRoot().getContext());
        languageDialog.setTitle(getString(R.string.set_language));
        //第二个参数是显示时选中的选项
        languageDialog.setSingleChoiceItems(items, language,
                (dialog, which) -> languageChoice = which);
        languageDialog.setPositiveButton(getString(R.string.set_ok), (dialog, which) -> {
            switch (languageChoice) {
                case 0:
                    sp.setmLanguage(0);
                    language = 0;
                    textView.setText(getString(R.string.set_follow));
                    //部分机型出现java.lang.NullPointerException: Can't set default locale to NULL
                    if (MainActivity.defaultLocale != null){
                        LocalUtils.setLanguage(getActivity(), MainActivity.defaultLocale);
                    }else {
                        LocalUtils.setLanguage(getActivity(), Locale.SIMPLIFIED_CHINESE);
                    }
                    break;
                case 1:
                    sp.setmLanguage(1);
                    language = 1;
                    textView.setText(getString(R.string.set_english));
                    LocalUtils.setLanguage(getActivity(), Locale.ENGLISH);
                    break;
                case 2:
                    sp.setmLanguage(2);
                    language = 2;
                    textView.setText(getString(R.string.set_japanese));
                    LocalUtils.setLanguage(getActivity(), Locale.JAPANESE);
                    break;
                case 3:
                    sp.setmLanguage(3);
                    language = 3;
                    textView.setText(getString(R.string.set_chinese_simple));
                    LocalUtils.setLanguage(getActivity(), Locale.SIMPLIFIED_CHINESE);
                    break;
                case 4:
                    sp.setmLanguage(4);
                    language = 4;
                    textView.setText(getString(R.string.set_chinese_tw));
                    LocalUtils.setLanguage(getActivity(), Locale.TRADITIONAL_CHINESE);
                    break;
            }
            showSnackbar(getString(R.string.set_already));
        });
        languageDialog.show();
    }

    //主题对话框
    int styleChoice;

    private void showStyleDialog(TextView textView) {
        final String[] items = {getString(R.string.set_light), getString(R.string.set_dark)};
        AlertDialog.Builder styleDialog = new AlertDialog.Builder(binding.getRoot().getContext());
        styleDialog.setTitle(getString(R.string.set_style));
        //第二个参数是显示时选中的选项
        styleDialog.setSingleChoiceItems(items, style,
                (dialog, which) -> styleChoice = which);
        styleDialog.setPositiveButton(getString(R.string.set_ok), (dialog, which) -> {
            switch (styleChoice) {
                case 0:
                    sp.setmStyle(0);
                    style = 0;
                    textView.setText(getString(R.string.set_light));
                    break;
                case 1:
                    sp.setmStyle(1);
                    style = 1;
                    textView.setText(getString(R.string.set_dark));
                    break;
            }
            showSnackbar(getString(R.string.set_restart));
        });
        styleDialog.show();
    }

    private void showSnackbar (String str){
        Snackbar snackbar = Snackbar.make(binding.setRecyclerView, str, Snackbar.LENGTH_SHORT);
        if (MainActivity.style == 1){
            snackbar.getView().setBackgroundColor(0xFF2c2c2e);
            snackbar.setTextColor(0xFFe5e5ea);
        }else {
            snackbar.getView().setBackgroundColor(0xFFf4f5f6);
            snackbar.setTextColor(0xFF1c1c1e);
        }
        snackbar.show();
        sp.saveData();
    }
    /*
    在Pause中保存数据
    */
    @Override
    public void onPause() {
        super.onPause();
        sp.saveData();
    }

    //隐藏menu
    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        menu.clear();
    }

    /*
     fragment 的生命周期与 activity 的生命周期不同，
     并且该fragment可以超出其视图的生命周期，
     因此如果不将其设置为null，则可能会发生内存泄漏。
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        //需要清空List,否则关于页面返回时RecyclerView会重复叠加
        setItemList.clear();
        binding = null;
    }
}
