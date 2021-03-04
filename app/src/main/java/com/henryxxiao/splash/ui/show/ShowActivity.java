package com.henryxxiao.splash.ui.show;

import android.Manifest;
import android.app.ActivityOptions;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.bumptech.glide.load.resource.bitmap.CircleCrop;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.snackbar.Snackbar;
import com.henryxxiao.splash.BuildConfig;
import com.henryxxiao.splash.MainActivity;
import com.henryxxiao.splash.R;
import com.henryxxiao.splash.cdownload.CDownload;
import com.henryxxiao.splash.cdownload.config.CDownloadConfig;
import com.henryxxiao.splash.cdownload.config.ConnectConfig;
import com.henryxxiao.splash.cdownload.config.ThreadPoolConfig;
import com.henryxxiao.splash.cdownload.listener.CDownloadListener;
import com.henryxxiao.splash.data.SplashPhoto;
import com.henryxxiao.splash.databinding.ActivityShowPhotoBinding;
import com.henryxxiao.splash.repository.RetrofitClient;
import com.henryxxiao.splash.ui.user.UserActivity;
import com.henryxxiao.splash.utils.GlideApp;
import com.henryxxiao.splash.utils.MySP;
import com.henryxxiao.splash.utils.RefreshPicture;

import java.io.File;
import java.util.Objects;

import io.reactivex.CompletableObserver;
import io.reactivex.annotations.Nullable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class ShowActivity extends AppCompatActivity {
    private ActivityShowPhotoBinding binding;
    private String downloadUrl;
    private String trackUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (MainActivity.style == 1) {
            setTheme(R.style.AppTheme_Show_Dark);
        }
        super.onCreate(savedInstanceState);
        binding = ActivityShowPhotoBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Bundle bundle = this.getIntent().getExtras();
        SplashPhoto splashPhoto = bundle.getParcelable("Photo");

//        getWindow().setSharedElementEnterTransition(new ChangeBounds());
//        getWindow().setSharedElementEnterTransition(new ChangeClipBounds());

        MySP mySP = new MySP(getApplicationContext());
        //获取设置中预览的选项
        String previewURL;
        if (mySP.getmPreview() == 0) {
            previewURL = splashPhoto.getUrls().getRegular();
        } else {
            previewURL = splashPhoto.getUrls().getSmall();
        }
        //获取设置中下载的选项
        int download = mySP.getmDownload();
        switch (download) {
            case 0:
                downloadUrl = splashPhoto.getUrls().getRaw() + ".jpg";
                break;
            case 1:
                downloadUrl = splashPhoto.getUrls().getFull() + ".jpg";
                break;
            default:
                downloadUrl = splashPhoto.getUrls().getRegular() + ".jpg";
        }

        binding.showImageView.setBackgroundColor(Color.parseColor(splashPhoto.getColor()));
        binding.showTextViewName.setText(splashPhoto.getUser().getName());
        //部分照片没有拍摄地点，需要做判断。
        if (splashPhoto.getUser().getLocation() != null) {
            binding.showTextViewLocation.setText(splashPhoto.getUser().getLocation());
        }
        //设置下载追踪的链接
        trackUrl = splashPhoto.getLinks().getDownload_location();

        GlideApp.with(this)
                .load(splashPhoto.getUser().getProfile_image().getLarge())
                //圆形头像
                .apply(RequestOptions.bitmapTransform(new CircleCrop()))
                .into(binding.showImageViewHead);
        GlideApp.with(this)
                .load(previewURL)
                //居中裁剪
                //.centerCrop()
                .into(binding.showImageView);

        //设置动画
        final Animation under = AnimationUtils.loadAnimation(this, R.anim.show_in_under);
        final Animation underMax = AnimationUtils.loadAnimation(this, R.anim.show_in_under_max);
        binding.showBackground.setAnimation(under);
//        binding.showImageViewHead.setAnimation(underMax);
//        binding.showTextViewName.setAnimation(underMax);
//        binding.showTextViewLocation.setAnimation(underMax);
        binding.linearLayout.setAnimation(underMax);
        binding.showDownload.setAnimation(underMax);

        //下载流程：存储权限检查 - 通知权限检查 - 连接TrackDownload - 下载
        binding.showDownload.setOnClickListener(v -> checkStorage());
        //跳转图片预览
        binding.showImageView.setOnClickListener(v -> {
            Bundle bundle1 = new Bundle();
            //获得图片链接
            bundle1.putString("URL", previewURL);
            Intent intent = new Intent(ShowActivity.this, ShowPhotoView.class);
            intent.putExtras(bundle1);//把数据通过Intent传到下一个Activity显示
            startActivity(intent);
        });

        //跳转到摄影师主页
        binding.showImageViewHead.setOnClickListener(v -> {
            Intent intent = new Intent(ShowActivity.this, UserActivity.class);
            intent.putExtras(bundle);
            //共享元素动画
//            ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(ShowActivity.this,
//                    Pair.create(binding.showImageViewHead, "user_head"),
//                    Pair.create(binding.showTextViewName, "user_name"));
            ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(ShowActivity.this,
                    binding.showImageViewHead, "user_head");
            startActivity(intent, options.toBundle());
        });
    }

    //检查存储权限
    public void checkStorage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        } else {
            checkNotify();
        }
    }

    //处理存储权限回调
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        /*
        需要先判断grantResults数组是否位空，不然会报ArrayIndexOutOfBoundsException错误
        部分情况下权限的数组可能为空，这是因为用户还没有对弹出的权限对话框做出选择，但该事件已经被触发，此时的grantResult是一个空数组
         */
        //这里的requestCode=1要和上面的一样
        if (grantResults.length > 0 && requestCode == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            checkNotify();
        } else {
            showSnackbar(getResources().getString(R.string.show_per_storage));
            //Toast.makeText(this, getResources().getString(R.string.show_per_storage), Toast.LENGTH_SHORT).show();
        }
    }

    //检查通知权限
    public void checkNotify() {
         /* areNotificationsEnabled方法的有效性官方只最低支持到API 19，
        低于19的仍可调用此方法不过只会返回true，即默认为用户已经开启了通知。*/
        boolean isOpened;
        NotificationManagerCompat manager = NotificationManagerCompat.from(this);
        isOpened = manager.areNotificationsEnabled();

        if (isOpened) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                //8.0以上设置按钮动画
                animation = true;
                //8.0以上需要检查通知渠道是否关闭
                checkNotifyChannel();
            } else {
                trackDown(trackUrl);
            }
        } else {
            showSnackbar(getResources().getString(R.string.show_per_notify));
            //Toast.makeText(this, getResources().getString(R.string.show_per_notify), Toast.LENGTH_SHORT).show();
        }
    }

    //8.0以上的系统需要检查通知渠道有没有关闭
    @RequiresApi(api = Build.VERSION_CODES.O)
    public void checkNotifyChannel() {
        boolean isOpened;
        try {
            NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = manager.getNotificationChannel("Download notification");
            //判断如果通知渠道的importance等于IMPORTANCE_NONE，就说明用户将该渠道的通知给关闭了
            isOpened = channel.getImportance() != NotificationManager.IMPORTANCE_NONE;
        } catch (Exception e) {
            //新安装首次下载还没有创建渠道，会抛空异常，没有创建就没法关闭，所以默认True
            isOpened = true;
        }

        if (isOpened) {
            trackDown(trackUrl);
        } else {
            showSnackbar(getResources().getString(R.string.show_per_channel));
            //Toast.makeText(this, getResources().getString(R.string.show_per_channel), Toast.LENGTH_SHORT).show();
        }
    }

    private void showSnackbar(String str) {
        Snackbar snackbar = Snackbar.make(binding.showBackground, str, Snackbar.LENGTH_SHORT);
        snackbar.setAction(getString(R.string.show_snackbar_permission), v -> {
            //跳转到 APP信息界面
            Intent intent = new Intent();
            intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            Uri uri = Uri.fromParts("package", getPackageName(), null);
            intent.setData(uri);
            startActivity(intent);
        });
        snackbar.setActionTextColor(getColor(R.color.colorMyBlue));
        if (MainActivity.style == 1) {
            snackbar.getView().setBackgroundColor(getColor(R.color.colorAppleDarkGray5));
            snackbar.setTextColor(getColor(R.color.colorAppleLightGray5));
        } else {
            snackbar.getView().setBackgroundColor(getColor(R.color.colorMaterialLightGray9));
            snackbar.setTextColor(getColor(R.color.colorAppleDarkGray6));
        }
        snackbar.show();
    }

    /*
    由于官方要求下载需要使用热点地址，方便API进行追踪和数据统计。
    但热点地址无法选择下载的分辨率，只有原图，且原图通常非常大。
    这对于网络状况较差的用户使用体验不佳，所以使用这种方法来解决。
    只像热点地址发送请求，不接受任何数据，这样API能记录到。
     */
    //按钮动画需要8.0及以上，不然会一直转圈
    boolean animation;
    public void trackDown(String url) {
        if (url != null) {
            if (animation) {
                //去掉Button阴影，不然动画带过去阴影是正方形的
                binding.showDownload.setStateListAnimator(null);
                binding.showDownload.startAnimation();
            } else {
                binding.showDownload.setEnabled(false);
                binding.showDownload.setText(getString(R.string.show_per_track));
            }
            String authUrl = url + "?client_id=4kwxH68cb2OKvAI4mR3jMzvr9Z-O_P0hKahtXIsqqu4";
            RetrofitClient.INSTANCE.getDownService().trackDownload(authUrl)
                    .observeOn(Schedulers.io())
                    .subscribeOn(Schedulers.io())
                    .subscribe(new CompletableObserver() {
                        public void onComplete() {
                            savePhoto();
                            //使用Looper无法更新showDownload.setText，会直接卡住
                            runOnUiThread(() -> {
                                if (animation) {
                                    binding.showDownload.doneLoadingAnimation(getColor(R.color.colorShow_done),
                                            BitmapFactory.decodeResource(getResources(), R.drawable.ic_done_white_48dp));
                                } else {
                                    binding.showDownload.setText(getString(R.string.show_per_start));
                                }
                                Toast.makeText(ShowActivity.this, getString(R.string.show_per_start), Toast.LENGTH_SHORT).show();
                            });
                        }

                        public void onSubscribe(@Nullable Disposable d) {
                            //Toast.makeText(ShowActivity.this, getResources().getString(R.string.show_per_track), Toast.LENGTH_SHORT).show();
                        }

                        public void onError(@Nullable Throwable e) {
                            runOnUiThread(() -> {
                                if (animation) {
                                    binding.showDownload.doneLoadingAnimation(getColor(R.color.colorShow_error),
                                            BitmapFactory.decodeResource(getResources(), R.drawable.ic_done_exclamation_white_48dp));
                                } else {
                                    binding.showDownload.setText(getString(R.string.notify_error_title));
                                }
                                Toast.makeText(ShowActivity.this, getString(R.string.notify_error_title), Toast.LENGTH_SHORT).show();
                            });
                        }
                    });
        } else {
            Toast.makeText(this, getResources().getString(R.string.show_url_error), Toast.LENGTH_SHORT).show();
        }
    }

    //下载图片
    public void savePhoto() {
        NotificationManager manager = (NotificationManager) this.getSystemService(NOTIFICATION_SERVICE);
        Notification.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("Download notification", "下载通知", NotificationManager.IMPORTANCE_HIGH);
            //channel.enableLights(true);//是否在桌面icon右上角展示小红点
            //channel.setLightColor(Color.GREEN);//小红点颜色
            //channel.setShowBadge(false); //是否在久按桌面图标时显示此渠道的通知
            Objects.requireNonNull(manager).createNotificationChannel(channel);

            builder = new Notification.Builder(this, "Download notification");
        } else {
            builder = new Notification.Builder(this);
        }
        //设置标题，“正在下载”
        builder.setContentTitle(getResources().getString(R.string.notify_down_title));
        //设置内容，获得摄影师名字
        builder.setContentText(binding.showTextViewName.getText() + getResources().getString(R.string.notify_down_name));
        //设置状态栏显示的图标，建议图标颜色透明
        builder.setSmallIcon(R.drawable.ic_set_download);
//        通知栏点击后自动消失，需要和pendingIntent一起才有效果
//        builder.setAutoCancel(true);
//        builder.setContentIntent(pendingIntent);
        //builder.setPriority(Notification.PRIORITY_MAX);
        builder.setProgress(100, 0, true);
        //设置为一个正在进行的通知
        builder.setOngoing(true);

        //这个int id防止通知被覆盖，这样就可以出现多个通知
        int id = (int) System.currentTimeMillis();
        Objects.requireNonNull(manager).notify(id, builder.build());

        //初始化下载器
        CDownloadConfig downloadConfig = CDownloadConfig.build()
                .setDiskCachePath(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pictures/Splash")
                .setConnectConfig(ConnectConfig.build().setConnectTimeOut(10000).setReadTimeOut(20000))
                .setIoThreadPoolConfig(ThreadPoolConfig.build().setCorePoolSize(4).setMaximumPoolSize(100).setKeepAliveTime(60));
        CDownload.getInstance().init(downloadConfig);

        //下载
        CDownload.getInstance().create(downloadUrl, new CDownloadListener() {
            @Override
            public void onPreStart() {

            }

            @Override
            public void onProgress(long maxSIze, long currentSize) {
//                int currentNum = (int) (100*currentSize/maxSIze);
//                System.out.println("进度："+currentNum);
//                builder.setProgress(100,currentNum,false);
            }

            @Override
            public void onComplete(String localFilePath) {
                //设置点击通知栏时跳转到相册
                Intent intent = new Intent(Intent.ACTION_VIEW);
                /*不做处理会提示异常：
                android.os.FileUriExposedException: file:///storage/emulated.. exposed beyond app through Intent.getData()
                需要在AndroidManifest设置provider，以及创建一个fileprovider.xml*/
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    Uri contentUri = FileProvider.getUriForFile(ShowActivity.this, BuildConfig.APPLICATION_ID + ".fileprovider", new File(localFilePath));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.setDataAndType(contentUri, "image/*");
                } else {
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.setDataAndType(Uri.parse("file://" + localFilePath), "image/*");
                }
                PendingIntent pendingIntent = PendingIntent.getActivity(ShowActivity.this, 0, intent, 0);
                //设置点击后自动划掉
                builder.setAutoCancel(true);
                builder.setContentIntent(pendingIntent);
                //设置"下载完成"标题
                builder.setContentTitle(getResources().getString(R.string.notify_over_title));
                //setProgress的两个变量设置为0，progress就会消失
                builder.setProgress(0, 0, false);
                builder.setSmallIcon(R.drawable.ic_show_done);
                //取消下载状态，让次状态可以划走消失
                builder.setOngoing(false);
                manager.notify(id, builder.build());
                //通知相册扫描图片
                new RefreshPicture(getApplicationContext());
            }

            @Override
            public void onError(String errorMessage) {
                //"下载失败，网络连接错误"
                builder.setContentTitle(getResources().getString(R.string.notify_error_title));
                builder.setProgress(0, 0, false);
                builder.setSmallIcon(R.drawable.ic_show_close);
                builder.setOngoing(false);
                manager.notify(id, builder.build());
            }

            @Override
            public void onCancel() {
                //下载被取消
                builder.setContentTitle(getResources().getString(R.string.notify_cancel_title));
                builder.setProgress(0, 0, false);
                builder.setOngoing(false);
                manager.notify(id, builder.build());
            }
        });
        CDownload.getInstance().start(downloadUrl);
    }

    //通知相册刷新图片
//    public void refreshPicture() {
//        File file = new File(Environment.getExternalStorageDirectory().getPath(), "/Pictures/Splash");
//        MediaScannerConnection.scanFile(this, new String[]{file.getAbsolutePath()}, null,
//                (path, uri) -> {
//                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
//                    mediaScanIntent.setData(uri);
//                    sendBroadcast(mediaScanIntent);
//                });
//    }

    //返回时过渡动画会调用，这时需要设置一下背景，不然图片会覆盖圆角
    @Override
    public void finishAfterTransition() {
        final Animation out = AnimationUtils.loadAnimation(this, R.anim.show_out_down);
        binding.showBackground.startAnimation(out);
        binding.showBackground.setVisibility(View.GONE);
        super.finishAfterTransition();
    }

}
