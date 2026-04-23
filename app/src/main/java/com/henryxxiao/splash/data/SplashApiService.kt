package com.henryxxiao.splash.data
import retrofit2.Response;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.Url;

interface SplashApiService {
    /**
     * 获取最新、最热或推荐的照片列表 (发现页/首页)
     *
     * @param endpoint 动态路径，例如 "photos" 或 "collections/317099/photos"
     * 注意：必须加上 encoded = true，否则 "/" 会被错误地转义为 "%2F"
     * @param page 页码 (默认: 1)
     * @param perPage 每页数量 (默认: 10)
     * @param orderBy 排序方式: "latest"(最新), "oldest"(最老), "popular"(最热)
     */
    @GET("{endpoint}")
    suspend fun loadPhotos(
        @Path(value = "endpoint", encoded = true) endpoint: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 30,
        @Query("order_by") orderBy: String = "latest"
    ): List<SplashPhoto>
    /**
     * 获取指定单张照片的详细信息 (包含 Exif、位置、下载量等，点开大图时调用)
     *
     * @param id 照片的唯一ID
     */
    @GET("photos/{id}")
    suspend fun getPhotoDetail(
        @Path("id") id: String
    ): SplashPhoto


    /**
     * 搜索图片
     *
     * @param query 搜索关键词
     * @param page 页码
     * @param perPage 每页数量
     * @param orderBy 排序: "relevant"(相关性), "latest"(最新)，默认relevant
     * @param color 颜色筛选: black_and_white, black, white, yellow, orange, red, purple, magenta, green, teal, blue，帮用户找特定色调的壁纸
     * @param orientation 方向筛选，“landscape” “portrait” “squarish” 横向、纵向、方形
     */
    @GET("search/photos")
    suspend fun searchPhotos(
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 30,
        @Query("order_by") orderBy: String = "relevant",
        @Query("color") color: String? = null,
        @Query("orientation") orientation: String? = null
    ): SearchResponse
    /**
     * 搜索用户 (新增完整功能)
     */
    @GET("search/users")
    suspend fun searchUsers(
        @Query("query") query: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 30
    ): SearchUserResponse


    /**
     * 获取用户公开资料
     *
     * @param username 用户名
     */
    @GET("users/{username}")
    suspend fun getUserProfile(
        @Path("username") username: String
    ): SplashUser

    /**
     * 获取某位摄影师上传的照片列表
     */
    @GET("users/{username}/photos")
    suspend fun getUserPhotos(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 30
    ): List<SplashPhoto>

    /**
     * 获取某位摄影师点赞过的照片
     */
    @GET("users/{username}/likes")
    suspend fun getUserLikes(
        @Path("username") username: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 30
    ): List<SplashPhoto>


    /**
     * 获取官方精选主题 (Topics) 列表 (聚合接口)
     * 获取官方设定的壁纸主题 (如：Architecture, Experimental, Wallpapers)
     * @param page 页码
     * @param perPage 请求数量 (默认拿 10 个足够 Carousel 轮播了)
     * @param orderBy 排序方式: "featured"(精选), "latest"(最新), "oldest"(最老), "position"(默认排序)
     */
    @GET("topics")
    suspend fun getTopics(
        @Query("page") page: Int = 1,
        @Query("per_page") perPage: Int = 10,
        @Query("order_by") orderBy: String = "position"
    ): List<SplashTopic>

    /**
     * 获取某个特定主题下的所有图片 (例如 "Wallpapers" 主题下的图片)
     */
    @GET("topics/{id_or_slug}/photos")
    suspend fun getTopicPhotos(
        @Path("id_or_slug") topicId: String,
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 30
    ): List<SplashPhoto>


    /**
     * Unsplash要求必须调用此API进行下载统计
     * 追踪下载量 (合规要求：每当用户点击“设为壁纸”或“下载”时，必须默默调用一次该接口以给摄影师算收益，)
     *
     * @param url 这里是你从图片对象的 links.download_location 获取到的完整 url
     * 注意：使用 @Url 标签时，Retrofit 会忽略 BaseUrl 直接请求传入的链接
     */
    @GET
    suspend fun trackDownload(
        @Url url: String
    ): Response<Unit>
}