package com.henryxxiao.splash.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.DataSource;

public class LoadPhotoDataSourceFactory extends DataSource.Factory {
    private MutableLiveData<LoadPhotoDataSource> liveDataSource = new MutableLiveData<>();
    public LiveData<LoadPhotoDataSource> liveData = liveDataSource;
    private String type;

    @NonNull
    @Override
    public DataSource create() {
        LoadPhotoDataSource loadPhotoDataSource = new LoadPhotoDataSource(type);
        liveDataSource.postValue(loadPhotoDataSource);
        return loadPhotoDataSource;
    }

    public void setType(String type) {
        this.type = type;
    }

}
