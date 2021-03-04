package com.henryxxiao.splash.utils;

import android.content.Context;
import android.content.SharedPreferences;

/*
SharedPreferences存储和读取设置的内容
 */

public class MySP {
    private Context context;
    private int mPreview;
    private int mDownload;
    private int mLoad;
    private int mStyle;
    private int mLanguage;

    public MySP(Context context) {
        this.context = context;
        getData();
    }

    public void getData (){
        SharedPreferences sp = context.getSharedPreferences("Set_Data", Context.MODE_PRIVATE);
        //第二个是缺省值（默认值）
        mPreview = sp.getInt("preview",1);
        mDownload = sp.getInt("download",2);
        mLoad = sp.getInt("load",0);
        mStyle = sp.getInt("style",0);
        mLanguage = sp.getInt("language",0);
    }

    public void saveData (){
        SharedPreferences sp = context.getSharedPreferences("Set_Data", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt("preview",mPreview);
        editor.putInt("download",mDownload);
        editor.putInt("load",mLoad);
        editor.putInt("style",mStyle);
        editor.putInt("language",mLanguage);
        //异步保存
        editor.apply();
    }

    public int getmPreview() {
        return mPreview;
    }

    public void setmPreview(int mPreview) {
        this.mPreview = mPreview;
    }

    public int getmDownload() {
        return mDownload;
    }

    public void setmDownload(int mDownload) {
        this.mDownload = mDownload;
    }

    public int getmLoad() {
        return mLoad;
    }

    public void setmLoad(int mLoad) {
        this.mLoad = mLoad;
    }

    public int getmStyle() {
        return mStyle;
    }

    public void setmStyle(int mStyle) {
        this.mStyle = mStyle;
    }

    public int getmLanguage() {
        return mLanguage;
    }

    public void setmLanguage(int mLanguage) {
        this.mLanguage = mLanguage;
    }
}
