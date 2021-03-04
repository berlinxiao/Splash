package com.henryxxiao.splash.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.DataSource;

public class SearchPhotoDataSourceFactory extends DataSource.Factory {
    private MutableLiveData<SearchPhotoDataSource> liveDataSource = new MutableLiveData<>();
    public LiveData<SearchPhotoDataSource> liveData = liveDataSource;
    private String query,sort;

    @NonNull
    @Override
    public DataSource create() {
        SearchPhotoDataSource searchPhotoDataSource = new SearchPhotoDataSource(query,sort);
        liveDataSource.postValue(searchPhotoDataSource);
        return searchPhotoDataSource;
    }

    public void setQuery (String query, String sort){
        this.query = query;
        this.sort = sort;
    }
}
