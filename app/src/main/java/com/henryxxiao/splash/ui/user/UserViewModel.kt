package com.henryxxiao.splash.ui.user;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import com.henryxxiao.splash.data.SplashPhoto;
import com.henryxxiao.splash.repository.LoadPhotoDataSource;
import com.henryxxiao.splash.repository.LoadPhotoDataSourceFactory;
import com.henryxxiao.splash.repository.LoadStatus;

/*
这里使用AndroidViewModel 而不是 ViewModel
因为需要getApplicationContext来访问SharedPreferences
 */

public class UserViewModel extends ViewModel {
    public LiveData<PagedList<SplashPhoto>> photoList;
    public LiveData<LoadStatus> loadStatus;

    public void userViewModel(String type){
        LoadPhotoDataSourceFactory loadPhotoDataSourceFactory = new LoadPhotoDataSourceFactory();
        loadPhotoDataSourceFactory.setType(type);

        loadStatus = Transformations.switchMap(loadPhotoDataSourceFactory.liveData, LoadPhotoDataSource::getStatus);
        PagedList.Config config = (new PagedList.Config.Builder())
                .setEnablePlaceholders(false)
                .setPageSize(10)
                .build();

        photoList = (new LivePagedListBuilder(loadPhotoDataSourceFactory, config)).build();
    }

}
