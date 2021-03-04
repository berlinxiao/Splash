package com.henryxxiao.splash;

import android.content.Intent;
import android.os.Bundle;
import android.transition.Fade;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.henryxxiao.splash.ui.search.SearchActivity;
import com.henryxxiao.splash.utils.LocalUtils;
import com.henryxxiao.splash.utils.MySP;

import java.util.Locale;

import io.reactivex.plugins.RxJavaPlugins;

import static io.reactivex.internal.functions.Functions.emptyConsumer;

public class MainActivity extends AppCompatActivity {
    private AppBarConfiguration mAppBarConfiguration;
    private NavController navController;
    public static int style;
    public static Locale defaultLocale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        MySP mySP = new MySP(getApplicationContext());
        style = mySP.getmStyle();
        //设置主题
        if (style == 1) {
            setTheme(R.style.AppTheme_Dark);
        }
        //设置语言
        switch (mySP.getmLanguage()) {
            case 0:
                LocalUtils.setLanguage(this, Locale.getDefault());
                //用于设置内的语言实时切换，如果设置里直接getDefault则返回的是当前设置的语言，而不是跟随系统
                defaultLocale = Locale.getDefault();
                break;
            case 1:
                LocalUtils.setLanguage(this, Locale.ENGLISH);
                break;
            case 2:
                LocalUtils.setLanguage(this, Locale.JAPANESE);
                break;
            case 3:
                LocalUtils.setLanguage(this, Locale.SIMPLIFIED_CHINESE);
                break;
            case 4:
                LocalUtils.setLanguage(this, Locale.TRADITIONAL_CHINESE);
                break;
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
//        DrawerLayout drawer = findViewById(R.id.drawer_layout);
        //NavigationView navigationView = findViewById(R.id.nav_view);

        //将每个菜单ID作为一组ID传递，因为每个菜单都应被视为顶级目标。
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_home)//如果将其他添加，那么就不会出现返回的箭头
                //.setDrawerLayout(drawer)
                .build();
        navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        //NavigationUI.setupWithNavController(navigationView, navController);

        //设置RxJava异常处理，详看下面
        setRxJavaErrorHandler();

        /*为HomeFragment的ShareElement设置淡出淡入,
          除指定的ShareElement外，
          其它所有View都会执行这个Transition动画*/
        getWindow().setEnterTransition(new Fade());
        getWindow().setExitTransition(new Fade());

//        TextView textView = (TextView)toolbar.getChildAt(0);//主标题
//        textView.getLayoutParams().width = LinearLayout.LayoutParams.MATCH_PARENT;//填充父类
//        textView.setGravity(Gravity.CENTER);//设置标题居中
    }

    //设置菜单和点击事件
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        MenuItem searchItem = menu.findItem(R.id.app_bar_search);
        searchItem.setOnMenuItemClickListener(item -> {
            Intent intent = new Intent(this, SearchActivity.class);
            startActivity(intent);
            return false;
        });
        MenuItem settingItem = menu.findItem(R.id.app_bar_setting);
        settingItem.setOnMenuItemClickListener(item -> {
            navController.navigate(R.id.nav_set);
            return false;
        });
        return true;
    }
//    这个点击事件只有在不显示toolbar上的菜单才有效果，即menu里设置的不是always和ifRoom
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        if (item.getItemId() == R.id.app_bar_search) {
//            Toast.makeText(this,"这里是菜单",Toast.LENGTH_SHORT).show();
//        }
//        return super.onOptionsItemSelected(item);
//    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration) || super.onSupportNavigateUp();
    }

    /*
    用于处理ShowActivity中下载追踪的异常，需要在App启动时初始化
    RxJava2 取消订阅后，抛出的异常无法捕获，导致程序崩溃：io.reactivex.exceptions.UndeliverableException
    抛出此异常唯一地方是 RxJavaPlugins 类。
    RxJava2 取消订阅后(dispose())，抛出的异常后续无法接收(此时后台线程仍在跑，可能会抛出IO等异常),
    全部由 RxJavaPlugin 接收，需要提前设置 ErrorHandler,这里设置成了空。
     */
    private void setRxJavaErrorHandler() {
        RxJavaPlugins.setErrorHandler(emptyConsumer());
    }
}
