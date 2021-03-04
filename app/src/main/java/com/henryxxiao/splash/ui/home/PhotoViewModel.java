package com.henryxxiao.splash.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import com.henryxxiao.splash.utils.MySP;
import com.henryxxiao.splash.data.SplashPhoto;
import com.henryxxiao.splash.repository.LoadPhotoDataSource;
import com.henryxxiao.splash.repository.LoadPhotoDataSourceFactory;
import com.henryxxiao.splash.repository.LoadStatus;

/*
这里使用AndroidViewModel 而不是 ViewModel
因为需要getApplicationContext来访问SharedPreferences
 */

public class PhotoViewModel extends AndroidViewModel {
    public LiveData<PagedList<SplashPhoto>> photoList;
    public LiveData<LoadStatus> loadStatus;
    MySP mySP;
    public PhotoViewModel(@NonNull Application application) {
        super(application);

        mySP = new MySP(application.getApplicationContext());
    }

    public void load(){
        //刷新一下数据
        mySP.getData();
        //获取设置里的加载选项
        String type = "collections/317099/photos";
        switch (mySP.getmLoad()){
            case 1:
                type = "collections/1459961/photos";
                break;
            case 2:
                type = "photos";
                break;
        }

        LoadPhotoDataSourceFactory loadPhotoDataSourceFactory = new LoadPhotoDataSourceFactory();
        loadPhotoDataSourceFactory.setType(type);

        loadStatus = Transformations.switchMap(loadPhotoDataSourceFactory.liveData, LoadPhotoDataSource::getStatus);
        PagedList.Config config = (new PagedList.Config.Builder())
                .setEnablePlaceholders(false)
                .setPageSize(20)
                .build();

        photoList = (new LivePagedListBuilder(loadPhotoDataSourceFactory, config)).build();
    }
}
