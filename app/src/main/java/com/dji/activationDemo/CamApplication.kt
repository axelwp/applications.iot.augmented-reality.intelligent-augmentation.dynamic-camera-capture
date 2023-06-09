package com.dji.activationDemo

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraXConfig

/**
 * Set CameraX logging level to Log.ERROR to avoid excessive logcat messages.
 * Refer to https://developer.android.com/reference/androidx/camera/core/CameraXConfig.Builder#setMinimumLoggingLevel(int)
 * for details.
 */
class CamApplication : Application(), CameraXConfig.Provider {
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setMinimumLoggingLevel(Log.ERROR).build()
    }
}