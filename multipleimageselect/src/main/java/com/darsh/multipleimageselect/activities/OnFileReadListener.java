package com.darsh.multipleimageselect.activities;

public interface OnFileReadListener {

    void onPermissionGranted();

    void onFetchStarted();

    void onFetchCompleted(int value);

    void onError();


}
