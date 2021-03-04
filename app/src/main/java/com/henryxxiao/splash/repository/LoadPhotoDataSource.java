package com.henryxxiao.splash.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.paging.PageKeyedDataSource;


import com.henryxxiao.splash.data.ApiService;
import com.henryxxiao.splash.data.SplashPhoto;

import java.net.UnknownHostException;
import java.util.List;

import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;
import retrofit2.Response;

public class LoadPhotoDataSource extends PageKeyedDataSource<Integer, SplashPhoto> {
    private static final String API_KEY = "4kwxH68cb2OKvAI4mR3jMzvr9Z-O_P0hKahtXIsqqu4";
    private String type;

    private MutableLiveData<LoadStatus> loadStatusMutable = new MutableLiveData<>();
    public LiveData<LoadStatus> getStatus(){
        return loadStatusMutable;
    }

    LoadPhotoDataSource(String type) {
        this.type = type;
    }

    private final ApiService mService = RetrofitClient.INSTANCE.getApiService();

    @Override
    public void loadInitial(@NonNull LoadInitialParams<Integer> params, @NonNull LoadInitialCallback<Integer, SplashPhoto> callback) {
        mService.loadPhotos(type,API_KEY,1,20)
                .subscribe(new Observer<Response<List<SplashPhoto>>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        //开始加载
                    }

                    @Override
                    public void onNext(Response<List<SplashPhoto>> listResponse) {
                        //加载成功
                        if(listResponse.body() != null && listResponse.isSuccessful()){
                            callback.onResult(listResponse.body(),null,2);
                            //postValue线程安全
                            loadStatusMutable.postValue(LoadStatus.SUCCESS);
                        }

                    }

                    @Override
                    public void onError(Throwable e) {
                        //没有网络
                        if (e instanceof UnknownHostException){
                            loadStatusMutable.postValue(LoadStatus.NONETWORK);
                        }else {
                            loadStatusMutable.postValue(LoadStatus.FAILURE);
                        }
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

        mService.loadPhotos(type,API_KEY,params.key,20)
                .subscribe(new Observer<Response<List<SplashPhoto>>>() {
                    @Override
                    public void onSubscribe(Disposable d) {
                        //开始加载
                    }

                    @Override
                    public void onNext(Response<List<SplashPhoto>> listResponse) {
                        //加载成功
                        if(listResponse.body() != null && listResponse.isSuccessful()){
                            callback.onResult(listResponse.body(),params.key+1);
                        }

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onComplete() {
                        //加载完成
                    }
                });
    }
}
