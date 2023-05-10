package com.dji.activationDemo.utils;

import android.util.Log;

import com.dji.activationDemo.GimbalAnimation;

import dji.common.error.DJIError;
import dji.common.util.CommonCallbacks;

public class CallbackHandlers {
    public static class CallbackCameraHandler implements CommonCallbacks.CompletionCallback {

        @Override
        public void onResult(DJIError djiError) {
            String message = "Success";
            if (djiError != null) {
                message = djiError.getDescription();
            }
            //GimbalAnimation.Photo(); // Take a photo
            Log.d("CallbackHandlers", message);
        }
    }
}
