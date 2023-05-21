/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dji.activationDemo

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.view.View
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.dji.activationDemo.databinding.ActivityMainBinding
import com.dji.activationDemo.fragments.GalleryFragment
import com.dji.activationDemo.fragments.LoginFragment
import org.json.JSONArray
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import java.io.*


/**
 * Main entry point into our app. This app follows the single-activity pattern, and all
 * functionality is implemented in the form of fragments.
 */
class MainActivity : AppCompatActivity() {
    private val KEY_EVENT_ACTION = "key_event_action"
    private val KEY_EVENT_EXTRA = "key_event_extra"
    private val IMMERSIVE_FLAG_TIMEOUT = 500L
    private var mModule: Module? = null

    private lateinit var activityMainBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityMainBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(activityMainBinding.root)

    }

    override fun onResume() {
        super.onResume()
        // Before setting full screen flags, we must wait a bit to let UI settle; otherwise, we may
        // be trying to set app to immersive mode before it's ready and the flags do not stick
        activityMainBinding.fragmentContainer.postDelayed({
            hideSystemUI()
        }, IMMERSIVE_FLAG_TIMEOUT)
    }

    /** When key down event is triggered, relay it via local broadcast so fragments can handle it */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                val intent = Intent(KEY_EVENT_ACTION).apply { putExtra(KEY_EVENT_EXTRA, keyCode) }
                LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            // Workaround for Android Q memory leak issue in IRequestFinishCallback$Stub.
            // (https://issuetracker.google.com/issues/139738913)
            finishAfterTransition()
        } else {
            super.onBackPressed()
        }
    }
    fun onRadioButtonClicked(view: View) {
        if (view is RadioButton) {
            val checked = view.isChecked
            // Check which radio button was clicked
            when (view.getId()) {
                R.id.radio_1k ->
                    if (checked) {
                        setResolution(1)
                    }
                R.id.radio_2k ->
                    if (checked) {
                        setResolution(2)
                    }
                R.id.radio_4k ->
                    if (checked) {
                        setResolution(4)
                    }
            }
        }

    }

    companion object {
        /** Tool to upload photos to cloud */
        private var Uploader: APIUploader = com.dji.activationDemo.APIUploader()
        private var numImages: Int = 10
        private var ips: Float = 2.0f
        private var horAngle: Float = 45.0f
        private var verAngle: Float = 15.0f
        private var rows: Int = 3
        private var columns: Int = 10
        private var buffer: Float = 0.2f
        private var inHor: Boolean = false
        private var inVer: Boolean = false
        private var resolution: Int = 1
        private var currentTaskId: String = ""
        private var currentJobId: String = ""


        /** Use external media if it is available, our app's file directory otherwise */
        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            //val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
            //    File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() } }
            return appContext.filesDir
        }

        @Throws(IOException::class)
        fun assetFilePath(context: Context, assetName: String?): String? {
            val file = File(context.filesDir, assetName)
            if (file.exists() && file.length() > 0) {
                return file.absolutePath
            }
            return context.assets.open(assetName!!).use { `is` ->
                FileOutputStream(file).use { os ->
                    val buffer = ByteArray(4 * 1024)
                    var read: Int
                    while (`is`.read(buffer).also { read = it } != -1) {
                        os.write(buffer, 0, read)
                    }
                    os.flush()
                }
                file.absolutePath
            }
        }

        /** Log a user into the server */
        fun userLogin(username: String, password: String, loginFragment: LoginFragment) {
            Uploader.sendLoginInfo(username, password, loginFragment)
        }

        /** Upload images from the gallery */
        fun uploadImages(images: ArrayList<File>, context: Context?, gallery: GalleryFragment) {
            Uploader.sendImages(images, context, gallery, currentTaskId, currentJobId)
        }

        /** Get tasklist from the server */
        fun getTaskList() : JSONArray {
            var test = JSONArray("[{\"taskId\":\"test\",\"weldJobName\":\"test\",\"weldJobLocation\":\"test\",\"weldJobId\":\"test\",\"scanMember\":\"test\",\"pieceMark\":\"test\",\"location\":\"test\",\"elevation\":\"test\",\"weldType\":\"test\",\"weldSize\":\"test\",\"weldLength\":\"test\",\"weldInterMarkerDistance\":\"test\",\"status\":0,\"statusMessage\":\"test\"}]")
            return test
            //return Uploader.generateTasksList()
        }

        /** Set the variables that will be used to animate the gimbal */
        fun setAnimation(Images: Int, imgPerSec: Float, horizontalAngle: Float, verticalAngle: Float, row: Int, col: Int, buff: Float, inverHor: Boolean, inverVer: Boolean) {
            numImages = Images
            ips = imgPerSec
            horAngle = horizontalAngle
            verAngle = verticalAngle
            rows = row
            columns = col
            buffer = buff
            inHor = inverHor
            inVer = inverVer
        }

        fun setResolution(res: Int){
            Log.d("Main", "set resolution: " + res + "K")
            resolution = res
        }

        fun getResolution() : Size {
            if (resolution == 1)
                return Size(1920, 1080)
            if (resolution == 2)
                return Size(2560, 1440)
            if (resolution == 4)
                return Size(3840, 2160)

            return Size(1920, 1080)
        }
        /** Get the number of images that will be taken when the shutter button is pressed */
        fun getImgNum() : Int {
            return numImages
        }
        /** Get the number of images that will me taken per second */
        fun getIps() : Float {
            return ips
        }
        /** Get the angle that the gimbal will rotate to (From -x to x) */
        fun getHorAngle() : Float {
            return horAngle
        }

        /** Get the angle that the gimbal will rotate to (From -x to x) */
        fun getVerAngle() : Float {
            return verAngle
        }

        /** Get the number of rows in the grid */
        fun getRows() : Int {
            return rows
        }

        /** Get the number of columns in the grid */
        fun getColumns() : Int {
            return columns
        }

        /** Get the amount of extra time to pause between gimbal photos */
        fun getBuff() : Float {
            return buffer
        }

        fun getHorInvert() : Boolean {
            return inHor
        }

        fun getVerInvert() : Boolean {
            return inVer
        }

        fun getCurrentTaskId() : String {
            return currentTaskId
        }

        fun setCurrentTaskId(taskId: String?) {
            if (taskId != null) {
                currentTaskId = taskId
            }
        }

        fun setCurrentJobtId(jobId: String?) {
            if (jobId != null) {
                currentJobId = jobId
            }
        }
    }

    private fun hideSystemUI() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, activityMainBinding.fragmentContainer).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
