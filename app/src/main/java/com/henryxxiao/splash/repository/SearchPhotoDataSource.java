package com.henryxxiao.splash.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PageKeyedDataSource;

import com.henryxxiao.splash.data.ApiService;
import com.henryxxiao.splash.data.SearchResponse;
import com.henryxxiao.splash.data.SplashPhoto;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import retrofit2.Response;

public class SearchPhotoDataSource extends PageKeyedDataSource<Integer, SplashPhoto> {
    private static final String API_KEY = "4kwxH68cb2OKvAI4mR3jMzvr9Z-O_P0hKahtXIsqqu4";
    private String query,sort;
    public static int result_total = 0;

    private final ApiService mService = RetrofitClient.INSTANCE.getApiService();

    private MutableLiveData<SearchStatus> searchStatusMutable = new MutableLiveData<>();
    public LiveData<SearchStatus> searchStatus(){
        return searchStatusMutable;
    }

    public SearchPhotoDataSource(String query, String sort) {
        this.query = query;
        this.sort = sort;
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams<Integer> params, @NonNull LoadInitialCallback<Integer, SplashPhoto> callback) {

        mService.searchPhotos(API_KEY,query,1,10,sort)
                .subscribe(new Observer<Response<SearchResponse>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        //开始加载
                        searchStatusMutable.postValue(SearchStatus.LOADING);
                    }

                    @Override
                    public void onNext(Response<SearchResponse> listResponse) {
                        //加载成功
                        if(listResponse.body() != null && listResponse.isSuccessful()){
                            callback.onResult(listResponse.body().getResults(),null,2);
                            result_total = listResponse.body().getTotal();
                            searchStatusMutable.postValue(SearchStatus.SUCCESS);
                        }

                    }

                    @Override
                    public void onError(Throwable e) {
                        searchStatusMutable.postValue(SearchStatus.FAILURE);
                    }

                    @Override
                    public void onComplete() {
                        //加载完成
                    }
                });

    }

    @Override
    public void loadBefore(@NonNull LoadParams<Integer> params, @NonNull LoadCallback<Integer, SplashPhoto> callback) {
        //不需要之前
    }

    @Override
    public void loadAfter(@NonNull LoadParams<Integer> params, @NonNull LoadCallback<Integer, SplashPhoto> callback) {

        mService.searchPhotos(API_KEY,query,params.key,10,sort)
                .subscribe(new Observer<Response<SearchResponse>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        //开始加载
                    }

                    @Override
                    public void onNext(Response<SearchResponse> listResponse) {
                        //加载成功
                        if(listResponse.body() != null && listResponse.isSuccessful()){
                            callback.onResult(listResponse.body().getResults(),params.key+1);
                        }

                    }

                    @Override
                    public void onError(Throwable e) {
                        //java.net.SocketTimeoutException: timeout
                    }

                    @Override
                    public void onComplete() {
                        //加载完成
                    }
                });
    }
}
