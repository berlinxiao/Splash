package com.henryxxiao.splash.ui.search;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityOptionsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.henryxxiao.splash.MainActivity;
import com.henryxxiao.splash.R;
import com.henryxxiao.splash.databinding.ActivitySearchBinding;
import com.henryxxiao.splash.repository.SearchPhotoDataSource;
import com.henryxxiao.splash.ui.show.ShowActivity;
import com.henryxxiao.splash.utils.PhotoAdapter;

import java.util.Objects;

public class SearchActivity extends AppCompatActivity {
    private ActivitySearchBinding binding;
    private PhotoAdapter photoAdapter;
    private SearchViewModel searchViewModel;
    private String sort;
    private static int sortID = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (MainActivity.style == 1){
            setTheme(R.style.AppTheme_Dark);
        }

        super.onCreate(savedInstanceState);
        binding = ActivitySearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.searchToolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle("");

        photoAdapter = new PhotoAdapter();
        searchViewModel = new ViewModelProvider(this).get(SearchViewModel.class);

        binding.searchRecyclerView.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        binding.searchRecyclerView.setHasFixedSize(true);
        binding.searchRecyclerView.setItemAnimator(null);

        //弹出键盘
        binding.searchEditText.requestFocus();
//        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        Objects.requireNonNull(imm).toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);

        binding.searchEditText.setOnEditorActionListener((v, actionId, event) -> {
            if (binding.searchEditText.getText().toString().equals("")){
                return false;
            }else {
                search(binding.searchEditText.getText().toString());
                binding.searchEditText.clearFocus();
                //关闭键盘
                Objects.requireNonNull(imm).hideSoftInputFromWindow(binding.searchEditText.getWindowToken(),0);
                //需要return true，不然返回两次结果
                return true;
            }
        });

        //播放Lottie动画
        binding.searchTipsEmoji.playAnimation();
    }

    private void search(String query) {
        searchViewModel.SearchView(query,sort);

        searchViewModel.photoList.observe(this, photoAdapter::submitList);

        searchViewModel.statusLiveData.observe(this, searchStatus -> {
            switch (searchStatus) {
                case LOADING:
                    binding.searchRecyclerView.setVisibility(View.GONE);
                    binding.searchLottieEmoji.setVisibility(View.GONE);
                    binding.searchTextView.setVisibility(View.GONE);
                    binding.searchProgressBar.setVisibility(View.VISIBLE);
                    binding.searchTabs.setVisibility(View.GONE);
                    binding.searchAppbar.setElevation(0);
                    break;
                case SUCCESS:
                    //appbar附加阴影
                    binding.searchAppbar.setElevation(15);
                    //改变tabText标题，为搜索结果。此处String为动态字符串
                    Objects.requireNonNull(binding.searchTabs.getTabAt(0))
                            .setText(String.format(getString(R.string.search_result),SearchPhotoDataSource.result_total));
                    binding.searchTabs.setVisibility(View.VISIBLE);
                    binding.searchProgressBar.setVisibility(View.GONE);
                    binding.searchRecyclerView.setVisibility(View.VISIBLE);
                    //0条结果
                    if (SearchPhotoDataSource.result_total == 0){
                        binding.searchTextView.setText(getString(R.string.search_no_result));
                        binding.searchLottieEmoji.setVisibility(View.VISIBLE);
                        binding.searchTextView.setVisibility(View.VISIBLE);
                        binding.searchLottieEmoji.playAnimation();
                    }
                    //观察结束，移除节省资源
                    searchViewModel.statusLiveData.removeObservers(this);
                    break;
                case FAILURE:
                    binding.searchTextView.setText(getString(R.string.search_fail));
                    binding.searchProgressBar.setVisibility(View.GONE);
                    binding.searchLottieEmoji.setVisibility(View.VISIBLE);
                    binding.searchTextView.setVisibility(View.VISIBLE);
                    binding.searchTabs.setVisibility(View.GONE);
                    binding.searchLottieEmoji.playAnimation();
                    binding.searchAppbar.setElevation(0);
                    //观察结束，移除节省资源
                    searchViewModel.statusLiveData.removeObservers(this);
                    break;
            }
        });
        binding.searchTipsEmoji.setVisibility(View.GONE);
        binding.searchTextViewTips.setVisibility(View.GONE);

        photoAdapter.notifyDataSetChanged();
        binding.searchRecyclerView.setAdapter(photoAdapter);

        photoAdapter.setOnItemClickListener((view, splashPhoto, imageView) -> {
            Bundle bundle = new Bundle();
            bundle.putParcelable("Photo", splashPhoto);
            Intent intent = new Intent(this, ShowActivity.class);
            intent.putExtras(bundle);//把数据通过Intent传到下一个Activity显示
            //共享元素过渡动画
            ActivityOptionsCompat optionsCompat = ActivityOptionsCompat.makeSceneTransitionAnimation(this, imageView, "image");
            startActivity(intent, optionsCompat.toBundle());
            //startActivity(intent);
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);
        MenuItem relevant = menu.findItem(R.id.search_relevant);
        MenuItem latest = menu.findItem(R.id.search_latest);

        //静态变量记录设置的排序方式，让这个使用周期不用每次打开搜索重新设置
        if (sortID == 1){
            relevant.setChecked(false);
            latest.setChecked(true);
            sort = "latest";
        }else {
            sort = "relevant";
        }

        relevant.setOnMenuItemClickListener(item -> {
            relevant.setChecked(true);
            latest.setChecked(false);
            sort = "relevant";
            sortID = 0;
            return false;
        });
        latest.setOnMenuItemClickListener(item -> {
            relevant.setChecked(false);
            latest.setChecked(true);
            sort = "latest";
            sortID = 1;
            return false;
        });
        return true;
    }

    //Toolbar的事件---返回
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if(item.getItemId() == android.R.id.home){
            //关闭键盘
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            Objects.requireNonNull(imm).hideSoftInputFromWindow(binding.searchEditText.getWindowToken(),0);
            finish();
        }
        return super.onOptionsItemSelected(item);
    }
}