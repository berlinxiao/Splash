package com.henryxxiao.splash.utils;

import android.content.Context;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.os.Environment;

import java.io.File;

/*
通知相册刷新图片
下载任务已完成时，用户可能已经退出了当时的ShowActivity，
所以导致广播无法正常发送到系统。这里新建一个类用来给下载器下载完成时调用
 */

public class RefreshPicture {
    public RefreshPicture(Context context) {
        File file = new File(Environment.getExternalStorageDirectory().getAbsolutePath(), "/Pictures/Splash");
        MediaScannerConnection.scanFile(context, new String[]{file.getAbsolutePath()}, null,
                (path, uri) -> {
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(uri);
                    context.sendBroadcast(mediaScanIntent);
                });
    }
}
