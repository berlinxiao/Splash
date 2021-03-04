package com.henryxxiao.splash.data;

import java.util.List;

import io.reactivex.Completable;
import io.reactivex.Observable;
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Query;
import retrofit2.http.Url;


/**
 * Api 接口
 * -------------------
 * Path URL地址的缺省值
 * client_id:Access Key
 * page:要检索的页码
 * per_page:每页的项目数
 * query:搜索词
 * -------------------
 * order_by	如何对照片进行排序。
 * （可选；默认值：）relevant。有效值为latest和relevant。
 */
public interface ApiService {

    @GET //&page=x&per_page=x
    Observable<Response<List<SplashPhoto>>> loadPhotos(@Url String url,
                                                       @Query("client_id") String clientId,
                                                       @Query("page") int page,
                                                       @Query("per_page") int pageSize);

    @GET("search/photos") //&query=x&page=x&per_page=x
    Observable<Response<SearchResponse>> searchPhotos(@Query("client_id") String clientId,
                                                      @Query("query") String query,
                                                      @Query("page") int page,
                                                      @Query("per_page") int pageSize,
                                                      @Query("order_by") String sort);

    @GET
    Completable trackDownload(@Url String var);

    @GET
    Observable<Response<List<SplashPhoto>>> userPhotos(@Url String url,
                                                       @Query("client_id") String clientId,
                                                       @Query("page") int page,
                                                       @Query("per_page") int pageSize);
}
