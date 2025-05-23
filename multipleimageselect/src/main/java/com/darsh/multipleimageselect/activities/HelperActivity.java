package com.darsh.multipleimageselect.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.os.HandlerCompat;

import com.darsh.multipleimageselect.R;
import com.darsh.multipleimageselect.helpers.Constants;
import com.google.android.material.snackbar.Snackbar;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by darshan on 26/9/16.
 */
public class HelperActivity extends AppCompatActivity {

    // Manifest.permission.WRITE_EXTERNAL_STORAGE is deprecated since sdkVersion >= 33

    protected final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    protected final Handler uiHandler = HandlerCompat.createAsync(Looper.getMainLooper());

    protected View view;
    private final int maxLines = 4;

    private String[] getPermissions() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ?
                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE} :
                new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES};
    }

    protected void checkPermission() {
        if (checkPermissions()) {
            permissionGranted();
        } else {
            ActivityCompat.requestPermissions(this, getPermissions(), Constants.PERMISSION_REQUEST_CODE);
        }
    }

    protected boolean checkPermissions() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ?
                ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED :
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermission() {
        if (shouldShowRequestPermissionsRationale()) {
            showRequestPermissionRationale();
        } else {
            showAppPermissionSettings();
        }
    }

    private boolean shouldShowRequestPermissionsRationale() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ?
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) :
                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_IMAGES) ||
                        ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_MEDIA_VIDEO);
    }

    private void showRequestPermissionRationale() {
        Snackbar snackbar = Snackbar.make(
                        view,
                        getString(R.string.permission_info),
                        Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(R.string.permission_ok), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        ActivityCompat.requestPermissions(
                                HelperActivity.this,
                                getPermissions(),
                                Constants.PERMISSION_REQUEST_CODE);
                    }
                });

        snackbar.show();
    }

    private void showAppPermissionSettings() {
        Snackbar snackbar = Snackbar.make(
                        view,
                        getString(R.string.permission_force),
                        Snackbar.LENGTH_INDEFINITE)
                .setAction(getString(R.string.permission_settings), new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Uri uri = Uri.fromParts(
                                getString(R.string.permission_package),
                                HelperActivity.this.getPackageName(),
                                null);

                        Intent intent = new Intent();
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                        intent.setData(uri);
                        startActivityForResult(intent, Constants.PERMISSION_REQUEST_CODE);
                    }
                });

        /*((TextView) snackbar.getView()
                .findViewById(android.support.design.R.id.snackbar_text)).setMaxLines(maxLines);*/
        snackbar.show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != Constants.PERMISSION_REQUEST_CODE
                || grantResults.length == 0
                || grantResults[0] == PackageManager.PERMISSION_DENIED) {
            permissionDenied();
        } else {
            permissionGranted();
        }
    }

    protected void permissionGranted() {
    }

    private void permissionDenied() {
        hideViews();
        requestPermission();
    }

    protected void hideViews() {
    }

    protected void setView(View view) {
        this.view = view;
    }
}
