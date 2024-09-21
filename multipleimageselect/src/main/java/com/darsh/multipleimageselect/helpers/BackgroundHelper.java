package com.darsh.multipleimageselect.helpers;

import java.util.concurrent.CompletableFuture;

public class BackgroundHelper {

    public static void execute(Runnable async) {
        CompletableFuture.runAsync(async);
    }

}
