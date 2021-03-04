package com.henryxxiao.splash.ui.search;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;

import com.henryxxiao.splash.data.SplashPhoto;
import com.henryxxiao.splash.repository.SearchPhotoDataSource;
import com.henryxxiao.splash.repository.SearchPhotoDataSourceFactory;
import com.henryxxiao.splash.repository.SearchStatus;

public class SearchViewModel extends ViewModel {
    public LiveData<PagedList<SplashPhoto>> photoList;
    public LiveData<SearchStatus> statusLiveData;

    public void SearchView(String query,String sort) {
        SearchPhotoDataSourceFactory searchPhotoDataSourceFactory = new SearchPhotoDataSourceFactory();
        searchPhotoDataSourceFactory.setQuery(query,sort);
        statusLiveData = Transformations.switchMap(searchPhotoDataSourceFactory.liveData, SearchPhotoDataSource::searchStatus);

        PagedList.Config config = (new PagedList.Config.Builder())
                .setEnablePlaceholders(false)
                .setPageSize(10)
                .build();

        photoList = (new LivePagedListBuilder(searchPhotoDataSourceFactory, config)).build();
    }
}
