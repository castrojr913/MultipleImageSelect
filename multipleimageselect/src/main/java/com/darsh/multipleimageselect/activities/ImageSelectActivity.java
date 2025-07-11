package com.darsh.multipleimageselect.activities;

import static android.provider.BaseColumns._ID;
import static android.provider.MediaStore.MediaColumns.DATA;
import static android.provider.MediaStore.MediaColumns.DISPLAY_NAME;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Process;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.darsh.multipleimageselect.R;
import com.darsh.multipleimageselect.adapters.CustomImageSelectAdapter;
import com.darsh.multipleimageselect.helpers.Constants;
import com.darsh.multipleimageselect.models.Image;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;

/**
 * Created by Darshan on 4/18/2015.
 */
public class ImageSelectActivity extends BaseEdgeToEdgeActivity implements OnFileReadListener {

    private final String[] projection = new String[]{_ID, DISPLAY_NAME, DATA};

    private ArrayList<Image> images;
    private String album;
    private TextView errorDisplay;
    private ProgressBar progressBar;
    private GridView gridView;
    private CustomImageSelectAdapter adapter;
    private ActionBar actionBar;
    private ActionMode actionMode;
    private int countSelected;
    private ContentObserver observer;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image_select);
        setView(findViewById(R.id.layout_image_select));
        applySystemBarInsets(findViewById(R.id.root_view));
        setSupportActionBar(findViewById(R.id.toolbar));
        actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeAsUpIndicator(R.drawable.ic_arrow_back);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setTitle(R.string.image_view);
        }
        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }
        album = intent.getStringExtra(Constants.INTENT_EXTRA_ALBUM);
        errorDisplay = findViewById(R.id.text_view_error);
        errorDisplay.setVisibility(View.INVISIBLE);
        progressBar = findViewById(R.id.progress_bar_image_select);
        gridView = findViewById(R.id.grid_view_image_select);

        //Listeners
        gridView.setOnItemClickListener((parent, view, position, id) -> {
            if (actionMode == null) actionMode = ImageSelectActivity.this.startActionMode(callback);
            toggleSelection(position);
            actionMode.setTitle(String.format(Locale.getDefault(), "%d %s", countSelected, getString(R.string.selected)));
            if (countSelected == 0) actionMode.finish();
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        observer = new ContentObserver(uiHandler) {
            @Override
            public void onChange(boolean selfChange) {
                loadImages();
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
        images = null;
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
            int size = orientation == Configuration.ORIENTATION_PORTRAIT ? metrics.widthPixels / 3 : metrics.widthPixels / 5;
            adapter.setLayoutParams(size);
        }
        gridView.setNumColumns(orientation == Configuration.ORIENTATION_PORTRAIT ? 3 : 5);
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
        loadImages();
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
                /*
                If adapter is null, this implies that the loaded images will be shown
                for the first time, hence send FETCH_COMPLETED message.
                However, if adapter has been initialised, this thread was run either
                due to the activity being restarted or content being changed.
                 */
            if (adapter == null) {
                adapter = new CustomImageSelectAdapter(getApplicationContext(), images);
                gridView.setAdapter(adapter);
                progressBar.setVisibility(View.INVISIBLE);
                gridView.setVisibility(View.VISIBLE);
                orientationBasedUI(getResources().getConfiguration().orientation);
            } else {
                adapter.notifyDataSetChanged();
                /*
                Some selected images may have been deleted
                hence update action mode title
                 */
                if (actionMode != null) {
                    countSelected = value;
                    actionMode.setTitle(String.format(Locale.getDefault(), "%d %s", countSelected, getString(R.string.selected)));
                }
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

    private final ActionMode.Callback callback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            MenuInflater menuInflater = mode.getMenuInflater();
            menuInflater.inflate(R.menu.menu_contextual_action_bar, menu);
            actionMode = mode;
            countSelected = 0;
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            int i = item.getItemId();
            if (i == R.id.menu_item_add_image) {
                sendIntent();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            if (countSelected > 0) deselectAll();
            actionMode = null;
        }
    };

    private void toggleSelection(int position) {
        if (!images.get(position).isSelected && countSelected >= Constants.limit) {
            Toast.makeText(getApplicationContext(),
                    String.format(getString(R.string.limit_exceeded), Constants.limit),
                    Toast.LENGTH_SHORT).show();
            return;
        }
        images.get(position).isSelected = !images.get(position).isSelected;
        if (images.get(position).isSelected) {
            countSelected++;
        } else {
            countSelected--;
        }
        adapter.notifyDataSetChanged();
    }

    private void deselectAll() {
        for (int i = 0, l = images.size(); i < l; i++) {
            images.get(i).isSelected = false;
        }
        countSelected = 0;
        adapter.notifyDataSetChanged();
    }

    private ArrayList<Image> getSelected() {
        ArrayList<Image> selectedImages = new ArrayList<>();
        for (int i = 0, l = images.size(); i < l; i++) {
            if (images.get(i).isSelected) {
                selectedImages.add(images.get(i));
            }
        }
        return selectedImages;
    }

    private void sendIntent() {
        Intent intent = new Intent();
        intent.putParcelableArrayListExtra(Constants.INTENT_EXTRA_IMAGES, getSelected());
        setResult(RESULT_OK, intent);
        finish();
    }

    private void loadImages() {
        bgExecutor.execute(new ImageLoaderRunnable());
    }

    private class ImageLoaderRunnable implements Runnable {
        @Override
        public void run() {
            android.os.Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            /*
            If the adapter is null, this is first time this activity's view is
            being shown, hence send FETCH_STARTED message to show progress bar
            while images are loaded from phone
             */
            if (adapter == null) onFetchStarted();

            try {
                File file;
                HashSet<Long> selectedImages = new HashSet<>();
                if (images != null) {
                    Image image;
                    for (int i = 0, l = images.size(); i < l; i++) {
                        image = images.get(i);
                        file = new File(image.path);
                        if (file.exists() && image.isSelected) {
                            selectedImages.add(image.id);
                        }
                    }
                }
                Cursor cursor = getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection,
                        MediaStore.Images.Media.BUCKET_DISPLAY_NAME + " =?", new String[]{album}, MediaStore.Images.Media.DATE_ADDED);
                if (cursor == null) {
                    onError();
                    return;
                }

                /*
                In case this runnable is executed to onChange calling loadImages,
                using countSelected variable can result in a race condition. To avoid that,
                tempCountSelected keeps track of number of selected images. On handling
                FETCH_COMPLETED message, countSelected is assigned value of tempCountSelected.
                 */
                int tempCountSelected = 0;
                ArrayList<Image> temp = new ArrayList<>(cursor.getCount());
                if (cursor.moveToLast()) {
                    do {
                        if (Thread.interrupted()) return;
                        final int idIndex = cursor.getColumnIndex(projection[0]);
                        final int nameIndex = cursor.getColumnIndex(projection[1]);
                        final int pathIndex = cursor.getColumnIndex(projection[2]);
                        final long id = idIndex > -1 ? cursor.getLong(idIndex) : 0;
                        final String name = nameIndex > -1 ? cursor.getString(nameIndex) : "";
                        final String path = pathIndex > -1 ? cursor.getString(pathIndex) : "";
                        boolean isSelected = selectedImages.contains(id);
                        if (isSelected) {
                            tempCountSelected++;
                        }
                        file = new File(path);
                        if (file.exists()) {
                            temp.add(new Image(id, name, path, isSelected));
                        }
                    } while (cursor.moveToPrevious());
                }
                cursor.close();
                if (images == null) images = new ArrayList<>();
                images.clear();
                images.addAll(temp);
                onFetchCompleted(tempCountSelected);
            } catch (Exception e) {
                final String err = e.getMessage();
                Log.e("ImageSelectActivity", err != null ? e.getMessage() : "null");
            }
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
