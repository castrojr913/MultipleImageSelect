package com.darsh.multipleimageselect.activities;

import static android.provider.MediaStore.MediaColumns.BUCKET_DISPLAY_NAME;
import static android.provider.MediaStore.MediaColumns.BUCKET_ID;
import static android.provider.MediaStore.MediaColumns.DATA;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android. database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.provider.MediaStore;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import androidx.appcompat.app.ActionBar;

import android.util.DisplayMetrics;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.darsh.multipleimageselect.R;
import com.darsh.multipleimageselect.adapters.CustomAlbumSelectAdapter;
import com.darsh.multipleimageselect.helpers.Constants;
import com.darsh.multipleimageselect.models.Album;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;

/**
 * Created by Darshan on 4/14/2015.
 */
public class AlbumSelectActivity extends BaseEdgeToEdgeActivity implements OnFileReadListener {

    private final String[] projection = new String[]{BUCKET_ID, BUCKET_DISPLAY_NAME, DATA};

    private ArrayList<Album> albums;
    private TextView errorDisplay;
    private ProgressBar progressBar;
    private GridView gridView;
    private CustomAlbumSelectAdapter adapter;
    private ActionBar actionBar;
    private ContentObserver observer;
    private ActivityResultLauncher<Intent> intentLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_select);
        setView(findViewById(R.id.layout_album_select));
        applySystemBarInsets(findViewById(R.id.root_view));
        setSupportActionBar(findViewById(R.id.toolbar));
        actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_back);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setTitle(R.string.album_view);
        }
        Intent intent = getIntent();
        if (intent == null) finish();
        Constants.limit = intent != null ? intent.getIntExtra(Constants.INTENT_EXTRA_LIMIT, Constants.DEFAULT_LIMIT) : 0;
        errorDisplay = findViewById(R.id.text_view_error);
        errorDisplay.setVisibility(View.INVISIBLE);
        progressBar = findViewById(R.id.progress_bar_album_select);
        gridView = findViewById(R.id.grid_view_album_select);

        // Listeners
        intentLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Intent data = result.getData();
                setResult(RESULT_OK, data);
                finish();
            }
        });
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent1 = new Intent(getApplicationContext(), ImageSelectActivity.class);
            intent1.putExtra(Constants.INTENT_EXTRA_ALBUM, albums.get(position).name);
            intentLauncher.launch(intent1);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        observer = new ContentObserver(uiHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                loadAlbums();
            }
        };
        getContentResolver().registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, false, observer);
        checkPermission();
    }

    @Override
    protected void onStop() {
        super.onStop();
        getContentResolver().unregisterContentObserver(observer);
        observer = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (actionBar != null) actionBar.setHomeAsUpIndicator(null);
        albums = null;
        if (adapter != null) adapter.releaseResources();
        gridView.setOnItemClickListener(null);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        orientationBasedUI(newConfig.orientation);
    }

    private void orientationBasedUI(int orientation) {
        final WindowManager windowManager = (WindowManager) getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        if (adapter != null) {
            int size = orientation == Configuration.ORIENTATION_PORTRAIT ? metrics.widthPixels / 2 : metrics.widthPixels / 4;
            adapter.setLayoutParams(size);
        }
        gridView.setNumColumns(orientation == Configuration.ORIENTATION_PORTRAIT ? 2 : 4);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        setResult(RESULT_CANCELED);
        finish();
    }

    @SuppressLint("UnsafeIntentLaunch")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            setResult(RESULT_OK, data);
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onPermissionGranted() {
        loadAlbums();
    }

    @Override
    public void onFetchStarted() {
        uiHandler.post(() -> {
            progressBar.setVisibility(View.VISIBLE);
            gridView.setVisibility(View.INVISIBLE);
        });
    }

    @Override
    public void onFetchCompleted(int value) {
        uiHandler.post(() -> {
            if (adapter == null) {
                adapter = new CustomAlbumSelectAdapter(getApplicationContext(), albums);
                gridView.setAdapter(adapter);
                progressBar.setVisibility(View.INVISIBLE);
                gridView.setVisibility(View.VISIBLE);
                orientationBasedUI(getResources().getConfiguration().orientation);
            } else {
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onError() {
        uiHandler.post(() -> {
            progressBar.setVisibility(View.INVISIBLE);
            errorDisplay.setVisibility(View.VISIBLE);
        });
    }

    private void loadAlbums() {
        bgExecutor.execute(new AlbumLoaderRunnable());
    }

    @SuppressLint("Range")
    private class AlbumLoaderRunnable implements Runnable {
        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            if (adapter == null) onFetchStarted();
            Cursor cursor = getApplicationContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
                    null, null, MediaStore.Images.Media.DATE_ADDED);
            if (cursor == null) {
                onError();
                return;
            }
            ArrayList<Album> temp = new ArrayList<>(cursor.getCount());
            HashSet<Long> albumSet = new HashSet<>();
            File file;
            if (cursor.moveToLast()) {
                do {
                    if (Thread.interrupted()) return;
                    long albumId = cursor.getLong(cursor.getColumnIndex(projection[0]));
                    String album = cursor.getString(cursor.getColumnIndex(projection[1]));
                    String image = cursor.getString(cursor.getColumnIndex(projection[2]));
                    if (!albumSet.contains(albumId)) {
                        /*
                        It may happen that some image file paths are still present in cache,
                        though image file does not exist. These last as long as media
                        scanner is not run again. To avoid get such image file paths, check
                        if image file exists.
                         */
                        file = new File(image);
                        if (file.exists()) {
                            temp.add(new Album(album, image));
                            albumSet.add(albumId);
                        }
                    }
                } while (cursor.moveToPrevious());
            }
            cursor.close();
            if (albums == null) albums = new ArrayList<>();
            albums.clear();
            albums.addAll(temp);
            onFetchCompleted(0);
        }
    }

    @Override
    protected void permissionGranted() {
        onPermissionGranted();
    }

    @Override
    protected void hideViews() {
        progressBar.setVisibility(View.INVISIBLE);
        gridView.setVisibility(View.INVISIBLE);
    }

}
