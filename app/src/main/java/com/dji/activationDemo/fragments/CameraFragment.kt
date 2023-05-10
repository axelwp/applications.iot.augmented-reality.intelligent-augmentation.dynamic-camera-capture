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

package com.dji.activationDemo.fragments

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.hardware.display.DisplayManager
import android.media.Image
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.*
import android.webkit.MimeTypeMap
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture.Metadata
import androidx.camera.core.impl.utils.ContextUtil.getApplicationContext
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import androidx.core.view.setPadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.Navigation
import androidx.window.WindowManager
import com.android.example.cameraxbasic.utils.ANIMATION_FAST_MILLIS
import com.android.example.cameraxbasic.utils.ANIMATION_SLOW_MILLIS
import com.android.example.cameraxbasic.utils.simulateClick
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.dji.activationDemo.*
import com.dji.activationDemo.MainActivity.Companion.assetFilePath
import com.dji.activationDemo.MainActivity.Companion.getResolution
import com.dji.activationDemo.R
import com.dji.activationDemo.databinding.CameraUiContainerBinding
import com.dji.activationDemo.databinding.FragmentCameraBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.*
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min


/** Helper type alias used for analysis use case callbacks */
typealias LumaListener = (luma: Double) -> Unit

typealias OBJListener = (obj: ArrayList<Result>) -> Unit

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class CameraFragment : Fragment() {
    private val KEY_EVENT_ACTION = "key_event_action"
    private val KEY_EVENT_EXTRA = "key_event_extra"

    private var mModule: Module? = null
    private var mResultView: ResultView? = null


    private var mLastAnalysisResultTime: Long = 0

    private var _fragmentCameraBinding: FragmentCameraBinding? = null

    private val fragmentCameraBinding get() = _fragmentCameraBinding!!

    private var cameraUiContainerBinding: CameraUiContainerBinding? = null

    private lateinit var outputDirectory: File
    private lateinit var broadcastManager: LocalBroadcastManager

    private var displayId: Int = -1
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var zoomRatio: Float = 1.0f
    private var cameraProvider: ProcessCameraProvider? = null
    private var Animation: GimbalAnimation = GimbalAnimation()
    private var meterAction: FocusMeteringAction? = null
    private lateinit var windowManager: WindowManager
    private lateinit var cropLocation: Rect


    //val handler: Handler = Handler()

    private val displayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    /** Volume down button receiver used to trigger shutter */
    private val volumeDownReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(KEY_EVENT_EXTRA, KeyEvent.KEYCODE_UNKNOWN)) {
                // When the volume down button is pressed, simulate a shutter button click
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    cameraUiContainerBinding?.cameraCaptureButton?.simulateClick()
                }
            }
        }
    }

    /**
     * We need a display listener for orientation changes that do not trigger a configuration
     * change, for example if we choose to override config change in manifest or for 180-degree
     * orientation changes.
     */
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                Log.d(TAG, "Rotation changed: ${view.display.rotation}")
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionsFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                    CameraFragmentDirections.actionCameraFragmentToPermissionsFragment()
            )
        }
        Animation.Construct(this)
        Animation.initGimbal()
        val rect = windowManager.getCurrentWindowMetrics().bounds
        val factory = fragmentCameraBinding.viewFinder.meteringPointFactory
        val meterAction = factory.createPoint(rect.exactCenterX(), rect.exactCenterY())
    }

    override fun onDestroyView() {
        _fragmentCameraBinding = null
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()

        // Unregister the broadcast receivers and listeners
        broadcastManager.unregisterReceiver(volumeDownReceiver)
        displayManager.unregisterDisplayListener(displayListener)
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View {
        _fragmentCameraBinding = FragmentCameraBinding.inflate(inflater, container, false)
        return fragmentCameraBinding.root
    }

    private fun setGalleryThumbnail(uri: Uri) {
        // Run the operations in the view's thread
        cameraUiContainerBinding?.photoViewButton?.let { photoViewButton ->
            photoViewButton.post {
                // Remove thumbnail padding
                photoViewButton.setPadding(resources.getDimension(R.dimen.stroke_small).toInt())

                // Load thumbnail into circular button using Glide
                Glide.with(photoViewButton)
                        .load(uri)
                        .apply(RequestOptions.circleCropTransform())
                        .into(photoViewButton)
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mResultView = view?.findViewById(R.id.resultView)


        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        broadcastManager = LocalBroadcastManager.getInstance(view.context)

        // Set up the intent filter that will receive events from our main activity
        val filter = IntentFilter().apply { addAction(KEY_EVENT_ACTION) }
        broadcastManager.registerReceiver(volumeDownReceiver, filter)

        // Every time the orientation of device changes, update rotation for use cases
        displayManager.registerDisplayListener(displayListener, null)

        //Initialize WindowManager to retrieve display metrics
        windowManager = WindowManager(view.context)

        // Determine the output directory
        outputDirectory = MainActivity.getOutputDirectory(requireContext())

        //maybe load model and classes here?

        // Wait for the views to be properly laid out
        fragmentCameraBinding.viewFinder.post {

            // Keep track of the display in which this view is attached
            displayId = fragmentCameraBinding.viewFinder.display.displayId

            // Build UI controls
            updateCameraUi()

            // Set up the camera and its use cases
            setUpCamera()
        }
    }

    /**
     * Inflate camera controls and update the UI manually upon config changes to avoid removing
     * and re-adding the view finder from the view hierarchy; this provides a seamless rotation
     * transition on devices that support it.
     *
     * NOTE: The flag is supported starting in Android 8 but there still is a small flash on the
     * screen for devices that run Android 9 or below.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // Rebind the camera with the updated display metrics
        bindCameraUseCases()

        // Enable or disable switching between cameras
        updateCameraSwitchButton()
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener(Runnable {

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            lensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            // Enable or disable switching between cameras
            updateCameraSwitchButton()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution
        val metrics = windowManager.getCurrentWindowMetrics().bounds
        Log.d(TAG, "Screen metrics: ${metrics.width()} x ${metrics.height()}")

        val screenAspectRatio = aspectRatio(metrics.width(), metrics.height())
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = fragmentCameraBinding.viewFinder.display.rotation

        Log.d(TAG, "ROTATION: ${rotation}")

        // CameraProvider
        val cameraProvider = cameraProvider
                ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        // Preview
        preview = Preview.Builder()
                // We request aspect ratio but no resolution
                .setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation
                .setTargetRotation(rotation)
                .build()

        //get views
        val mResultView: ResultView? = view?.findViewById(R.id.resultView)
        //val textureView: TextureView? = (view?.findViewById(R.id.object_detection_view_stub) as ViewStub).inflate()
         //       .findViewById(R.id.object_detection_texture_view)
        val rect = windowManager.getCurrentWindowMetrics().bounds
        val factory = fragmentCameraBinding.viewFinder.meteringPointFactory
        var meteringPoint: MeteringPoint = factory.createPoint(rect.exactCenterX(), rect.exactCenterY());

        //  This line sets a callback function to be called when the preview output is updated.
        // The callback function takes a SurfaceProvider object as input and updates the SurfaceTexture
        // of the TextureView with the new preview frame.
        // The output -> textureView.setSurfaceTexture(output.getSurfaceTexture()) lambda function is
        // passed as a parameter to the setOnPreviewOutputUpdateListener() method to set this callback.
        // This allows the preview frames to be displayed on the TextureView.
        //preview.setOnPreviewOutputUpdateListener { output -> textureView!!.setSurfaceTexture(output.getSurfaceTexture()) }



        // ImageCapture
        imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                // We request aspect ratio but no resolution to match preview config, but letting
                // CameraX optimize for whatever specific resolution best fits our use cases
                //.setTargetAspectRatio(screenAspectRatio)
                .setTargetResolution(getResolution())
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation)
                .build()

        // ImageAnalysis
        imageAnalyzer = ImageAnalysis.Builder()
                // We request aspect ratio but no resolution
                .setTargetResolution(getResolution())
                //.setTargetAspectRatio(screenAspectRatio)
                // Set initial target rotation, we will have to call this again if rotation changes
                // during the lifecycle of this use case
                .setTargetRotation(rotation)
                .build()
                // The analyzer can then be assigned to the instance
                .also {
                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer( { obj ->
                        Log.d(TAG, "obj: ${obj.size}")


                        //prevent analysis from happening more than once in 500 time units
                        if (SystemClock.elapsedRealtime() - mLastAnalysisResultTime < 500) {
                            return@LuminosityAnalyzer
                        }

                        //need to check when the last result was changed?
                        //set results in resultview
                        if (mResultView != null) {
                            //set counter for last analysis
                            mLastAnalysisResultTime = SystemClock.elapsedRealtime()
                            mResultView.setResults(obj)
                            //invalidate
                            mResultView.invalidate()
                            Log.d(TAG, "got result")
                        } else {
                            Log.d(TAG, "no result")
                        }

                        // Values returned from our analyzer are passed to the attached listener
                        // We log image analysis results here - you should do something useful
                        // instead!)
                        //Log.d(TAG, "Average luminosity: $luma")
                        if(mResultView?.highestConfidence != null){
                            Log.d(TAG, "highest confidence: ${mResultView.highestConfidence.rect.exactCenterX()}")
                            cropLocation = mResultView.highestConfidence.rect
                            //get the bounding box with the highest confidence score from the Analyzer and create a MeteringPoint in the center of that box
                            meteringPoint = factory.createPoint((mResultView.highestConfidence.rect.left.toFloat() + mResultView.highestConfidence.rect.width() / 2),
                                (mResultView.highestConfidence.rect.top.toFloat() + mResultView.highestConfidence.rect.height() / 2))
                        } else {
                            Log.d(TAG, "no confidence:")
                        }
                        val focusAction = FocusMeteringAction.Builder(meteringPoint).build();
                        meterAction?.let { camera?.cameraControl?.startFocusAndMetering(focusAction) }
                    }, requireContext()))
                }

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture, imageAnalyzer)

            // Attach the viewfinder's surface provider to preview use case
            preview?.setSurfaceProvider(fragmentCameraBinding.viewFinder.surfaceProvider)
            observeCameraState(camera?.cameraInfo!!)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun observeCameraState(cameraInfo: CameraInfo) {
        cameraInfo.cameraState.observe(viewLifecycleOwner) { cameraState ->
            /*
            run {
                when (cameraState.type) {
                    CameraState.Type.PENDING_OPEN -> {
                        // Ask the user to close other camera apps
                        Toast.makeText(context,
                                "CameraState: Pending Open",
                                Toast.LENGTH_SHORT).show()
                    }
                    CameraState.Type.OPENING -> {
                        // Show the Camera UI
                        Toast.makeText(context,
                                "CameraState: Opening",
                                Toast.LENGTH_SHORT).show()
                    }
                    CameraState.Type.OPEN -> {
                        // Setup Camera resources and begin processing
                        Toast.makeText(context,
                                "CameraState: Open",
                                Toast.LENGTH_SHORT).show()
                    }
                    CameraState.Type.CLOSING -> {
                        // Close camera UI
                        Toast.makeText(context,
                                "CameraState: Closing",
                                Toast.LENGTH_SHORT).show()
                    }
                    CameraState.Type.CLOSED -> {
                        // Free camera resources
                        Toast.makeText(context,
                                "CameraState: Closed",
                                Toast.LENGTH_SHORT).show()
                    }
                }
            }
            */

            cameraState.error?.let { error ->
                when (error.code) {
                    // Open errors
                    CameraState.ERROR_STREAM_CONFIG -> {
                        // Make sure to setup the use cases properly
                        Toast.makeText(context,
                                "Stream config error",
                                Toast.LENGTH_SHORT).show()
                    }
                    // Opening errors
                    CameraState.ERROR_CAMERA_IN_USE -> {
                        // Close the camera or ask user to close another camera app that's using the
                        // camera
                        Toast.makeText(context,
                                "Camera in use",
                                Toast.LENGTH_SHORT).show()
                    }
                    CameraState.ERROR_MAX_CAMERAS_IN_USE -> {
                        // Close another open camera in the app, or ask the user to close another
                        // camera app that's using the camera
                        Toast.makeText(context,
                                "Max cameras in use",
                                Toast.LENGTH_SHORT).show()
                    }
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> {
                        Toast.makeText(context,
                                "Other recoverable error",
                                Toast.LENGTH_SHORT).show()
                    }
                    // Closing errors
                    CameraState.ERROR_CAMERA_DISABLED -> {
                        // Ask the user to enable the device's cameras
                        Toast.makeText(context,
                                "Camera disabled",
                                Toast.LENGTH_SHORT).show()
                    }
                    CameraState.ERROR_CAMERA_FATAL_ERROR -> {
                        // Ask the user to reboot the device to restore camera function
                        Toast.makeText(context,
                                "Fatal error",
                                Toast.LENGTH_SHORT).show()
                    }
                    // Closed errors
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED -> {
                        // Ask the user to disable the "Do Not Disturb" mode, then reopen the camera
                        Toast.makeText(context,
                                "Do not disturb mode enabled",
                                Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     *  [androidx.camera.core.ImageAnalysis.Builder] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    /** Method used to re-draw the camera UI controls, called every time configuration changes. */
    private fun updateCameraUi() {

        // Remove previous UI if any
        cameraUiContainerBinding?.root?.let {
            fragmentCameraBinding.root.removeView(it)
        }

        cameraUiContainerBinding = CameraUiContainerBinding.inflate(
                LayoutInflater.from(requireContext()),
                fragmentCameraBinding.root,
                true
        )

        // In the background, load latest photo taken (if any) for gallery thumbnail
        lifecycleScope.launch(Dispatchers.IO) {
            outputDirectory.listFiles { file ->
                EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
            }?.maxOrNull()?.let {
                setGalleryThumbnail(Uri.fromFile(it))
            }
        }
        cameraUiContainerBinding?.btnBack?.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp()
        }
        // Listener for button used to capture photo
        cameraUiContainerBinding?.cameraCaptureButton?.setOnClickListener {
            Animation.updateAnimation()
            Animation.Step()
            takePhotos() // Begin gimbal animation and take several photos
        }


        // Setup for button used to switch cameras
        cameraUiContainerBinding?.cameraSwitchButton?.let {
            // Disable the button until the camera is set up
            //it.isEnabled = false
            /*
            // Listener for button used to switch cameras. Only called if the button is enabled
            it.setOnClickListener {

                lensFacing = if (CameraSelector.LENS_FACING_FRONT == lensFacing) {
                    CameraSelector.LENS_FACING_BACK
                } else {
                    CameraSelector.LENS_FACING_FRONT
                }
                // Re-bind use cases to update selected camera
                bindCameraUseCases()
            }
            */

        }

        // Listener for button used to view the most recent photo
        cameraUiContainerBinding?.photoViewButton?.setOnClickListener {
            // Only navigate when the gallery has photos
            if (true == outputDirectory.listFiles()?.isNotEmpty()) {
                Navigation.findNavController(
                        requireActivity(), R.id.fragment_container
                ).navigate(CameraFragmentDirections
                        .actionCameraToGallery(outputDirectory.absolutePath))
            }
        }
    }
    /*
    //if this doesnt work, try the logic shown on this example https://www.codeproject.com/Articles/1276135/Crop-Image-from-Camera-on-Android
    private fun cropPhoto(
        photoUri: Uri,
        cropRect: Rect,
        callback: (Uri?) -> Unit
    ) {
        val intent = Intent("com.android.camera.action.CROP")

        intent.data = photoUri // location of the image
        intent.putExtra("crop", "true") // Enable cropping
        intent.putExtra("scale", true) // Enable scaling of the cropped image
        intent.putExtra("aspectX", cropRect.width()) // Set the width of the crop rectangle
        intent.putExtra("aspectY", cropRect.height()) // Set the height of the crop rectangle
        intent.putExtra("outputX", cropRect.width()) // Set the desired output width of the cropped image
        intent.putExtra("outputY", cropRect.height()) // Set the desired output height of the cropped image

        // Create the activity result launcher for the crop intent
        val cropActivityResultLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // Handle the cropped image result
                val data = result.data
                if (data != null && data.hasExtra("data")) {
                    val croppedImage = data.getParcelableExtra<Bitmap>("data")

                    // Now we write the bitmap to a file
                    val croppedFile = File(context!!.cacheDir, "cropped_image.jpg")
                    val outputStream = FileOutputStream(croppedFile)
                    croppedImage!!.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    outputStream.flush()
                    outputStream.close()

                    //get the URI of the cropped image
                    val croppedImageUri = FileProvider.getUriForFile(
                        context!!,
                        "${context!!.packageName}.fileprovider",
                        croppedFile
                    )

                    // Return the URI of the cropped image through the callback
                    callback(croppedImageUri)
                } else {
                    // Return null if the cropped image data is not available
                    callback(null)
                }
            } else {
                // Return null if the cropping activity did not complete successfully
                callback(null)
            }
        }

        // Start the cropping activity
        intent.putExtra("return-data", true)
        val extras = Bundle()
        extras.putParcelable("rect", cropRect)
        intent.putExtras(extras)
        cropActivityResultLauncher.launch(intent)
    }
*/


    fun takePhotos() {
        //Commented this out because we want to focus the camera on the center of the bounding box, should be updated at the same time
        // Focus camera before taking the shot
        //meterAction?.let { camera?.cameraControl?.startFocusAndMetering(it) }

        val focusArea = mResultView?.highestConfidence;

        // Get a stable reference of the modifiable image capture use case
        imageCapture?.let { imageCapture ->

            // Create output file to hold the image
            val photoFile = createFile(outputDirectory, FILENAME, PHOTO_EXTENSION)
            var rectString = "null"
            if(focusArea != null){
                val delimiter = ","
                rectString = "${focusArea.rect.left}$delimiter${focusArea.rect.top}$delimiter${focusArea.rect.right}$delimiter${focusArea.rect.bottom}"
            }

            // Setup image capture metadata
            val metadata = Metadata().apply {

                // Mirror image when using the front camera
                isReversedHorizontal = lensFacing == CameraSelector.LENS_FACING_FRONT
                //pass bounding box
            }

            // Create output options object which contains file + metadata
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
                .setMetadata(metadata)
                .build()

            // Setup image capture listener which is triggered after photo has been taken
            imageCapture.takePicture(
                outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                        if (Animation.Step()) {takePhotos()} // Recursively call the take photo function
                    }

                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        var savedUri = output.savedUri ?: Uri.fromFile(photoFile)
                        Log.d(TAG, "Photo capture succeeded: $savedUri")

                        if(mResultView?.highestConfidence != null){
                            // Perform image cropping here
                            /*
                            cropPhoto(savedUri, crop) { croppedImageUri ->
                                // Handle the cropped image URI
                                if (croppedImageUri != null) {
                                    // Use the cropped image URI
                                    savedUri = croppedImageUri
                                    Log.d(TAG, "Image crop succeeded: $savedUri")
                                } else {
                                    // Show an error message or handle the case where the cropping did not complete successfully
                                    Log.e(TAG, "Image crop failed")
                                }
                            }*/
                        }
                        //Log.d(TAG, "No box found, image will not be cropped")


                        if (Animation.Step()) {
                            takePhotos() // Recursively call the take photo function
                        }

                        // We can only change the foreground Drawable using API level 23+ API
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            // Update the gallery thumbnail with latest picture taken
                            setGalleryThumbnail(savedUri)
                        }

                        // Implicit broadcasts will be ignored for devices running API level >= 24
                        // so if you only target API level 24+ you can remove this statement
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                            requireActivity().sendBroadcast(
                                Intent(android.hardware.Camera.ACTION_NEW_PICTURE, savedUri)
                            )
                        }

                        // If the folder selected is an external media directory, this is
                        // unnecessary but otherwise other apps will not be able to access our
                        // images unless we scan them using [MediaScannerConnection]
                        val mimeType = MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(savedUri.toFile().extension)
                        MediaScannerConnection.scanFile(
                            context,
                            arrayOf(savedUri.toFile().absolutePath),
                            arrayOf(mimeType)
                        ) { _, uri ->
                            Log.d(TAG, "Image capture scanned into media store: $uri")
                        }
                    }
                })

            // We can only change the foreground Drawable using API level 23+ API
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                // Display flash animation to indicate that photo was captured
                fragmentCameraBinding.root.postDelayed({
                    fragmentCameraBinding.root.foreground = ColorDrawable(Color.WHITE)
                    fragmentCameraBinding.root.postDelayed(
                        { fragmentCameraBinding.root.foreground = null }, ANIMATION_FAST_MILLIS)
                }, ANIMATION_SLOW_MILLIS)
            }
        }
    }

    /** Enabled or disabled a button to switch cameras depending on the available cameras */
    private fun updateCameraSwitchButton() {
        try {
            cameraUiContainerBinding?.cameraSwitchButton?.isEnabled = hasBackCamera() && hasFrontCamera()
        } catch (exception: CameraInfoUnavailableException) {
            cameraUiContainerBinding?.cameraSwitchButton?.isEnabled = false
        }
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }


    /**
     * Our custom image analysis class.
     *
     * <p>All we need to do is override the function `analyze` with our desired operations. Here,
     * we compute the average luminosity of the image by looking at the Y plane of the YUV frame.
     */
    //changed class type from private to inner so that it can access the members above
    @SuppressLint("RestrictedApi")
    private inner class LuminosityAnalyzer(listener: OBJListener? = null, context: Context) : ImageAnalysis.Analyzer {
        private var mModule: Module? = null
        private val frameRateWindow = 8
        private val frameTimestamps = ArrayDeque<Long>(5)
        private val listeners = ArrayList<OBJListener>().apply { listener?.let { add(it) } }
        private var lastAnalyzedTimestamp = 0L
        var framesPerSecond: Double = -1.0
            private set

        init {
            try {
                //mResultView = ResultView(context)
                mModule = LiteModuleLoader.load(assetFilePath(getApplicationContext(context), "best.torchscript.ptl"))
                val br = BufferedReader(InputStreamReader(context.getAssets().open("classes.txt")))
                val iterator = br.lineSequence().iterator()
                val classes: MutableList<String> = ArrayList()
                while (iterator.hasNext()) {
                    val line = iterator.next()
                    classes.add(line)
                }
                PrePostProcessor.mClasses = classes.toTypedArray()
            } catch (e: IOException) {
                Log.e("Object Detection", "Error reading assets", e)
            }
        }

        /**
         * Used to add listeners that will be called with each luma computed
         */
        fun onFrameAnalyzed(listener: OBJListener) = listeners.add(listener)

        /**
         * Helper extension function used to extract a byte array from an image plane buffer
         */
        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        private fun imgToBitmap(image: Image): Bitmap? {
            val planes = image.planes
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer[nv21, 0, ySize]
            vBuffer[nv21, ySize, vSize]
            uBuffer[nv21, ySize + vSize, uSize]
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
            val imageBytes = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        }

        /**
         * Analyzes an image to produce a result.
         *
         * <p>The caller is responsible for ensuring this analysis method can be executed quickly
         * enough to prevent stalls in the image acquisition pipeline. Otherwise, newly available
         * images will not be acquired and analyzed.
         *
         * <p>The image passed to this method becomes invalid after this method returns. The caller
         * should not store external references to this image, as these references will become
         * invalid.
         *
         * @param image image being analyzed VERY IMPORTANT: Analyzer method implementation must
         * call image.close() on received images when finished using them. Otherwise, new images
         * may not be received or the camera may stall, depending on back pressure setting.
         *
         */
        override fun analyze(image: ImageProxy) {
            // If there are no listeners attached, we don't need to perform analysis
            if (listeners.isEmpty()) {
                image.close()
                return
            }

//            // Keep track of frames analyzed
//            val currentTime = System.currentTimeMillis()
//            frameTimestamps.push(currentTime)
//
//            // Compute the FPS using a moving average
//            while (frameTimestamps.size >= frameRateWindow) frameTimestamps.removeLast()
//            val timestampFirst = frameTimestamps.peekFirst() ?: currentTime
//            val timestampLast = frameTimestamps.peekLast() ?: currentTime
//            framesPerSecond = 1.0 / ((timestampFirst - timestampLast) /
//                    frameTimestamps.size.coerceAtLeast(1).toDouble()) * 1000.0
//
//            // Analysis could take an arbitrarily long amount of time
//            // Since we are running in a different thread, it won't stall other use cases
//
//            lastAnalyzedTimestamp = frameTimestamps.first

            var results = ArrayList<Result>()

            runBlocking {
                launch(Dispatchers.Default) {
                    //CONVERT IMAGE TO BITMAP
                    var bitmap = imgToBitmap(image.image!!)
                    //CREATE MATRIX OBJECT AND ROTATE BITMAP BY 90 DEGREES
                    val matrix = Matrix()
                    //matrix.postRotate(90.0f)
                    //CREATE A NEW BITMAP WITH SAME ASPECT RATIO AS ORIGINAL BITMAP, BUT WITH SMALLER SIZE
                    bitmap = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap!!.width, bitmap!!.height, matrix, true)
                    val resizedBitmap = Bitmap.createScaledBitmap(bitmap, PrePostProcessor.mInputWidth, PrePostProcessor.mInputHeight, true)

                    //CONVERT BITMAP TO TENSOR
                    val inputTensor = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap, PrePostProcessor.NO_MEAN_RGB, PrePostProcessor.NO_STD_RGB)
                    //RUN INPUT TENSOR THROUGH MODEL, RETURNS AS A TUPLE
                    val outputTuple: Array<IValue> = mModule?.forward(IValue.from(inputTensor))!!.toTuple()
                    //EXTRACTS FIRST ELEMENT FROM THE OUTPUT TUPLE
                    val outputTensor = outputTuple[0].toTensor()
                    //CONVERT TO ARRAY OF FLOAT VALUES, THIS HOLDS THE OUTPUT OF TH EMODEL FOR THE INPUT IMAGE
                    val outputs = outputTensor.dataAsFloatArray

                    //CALCULATE SCALING FACTORS FOR IMAGE AND RESULTVIEW SO WE CAN CORRECTLY DISPLAY THE OBJECT DETECTION RESULT
                    //SCALE FACTOR FOR IMAGE WIDTH
                    val imgScaleX = bitmap.width.toFloat() / PrePostProcessor.mInputWidth
                    //SCALE FACTOR FOR IMAGE HEIGHT
                    val imgScaleY = bitmap.height.toFloat() / PrePostProcessor.mInputHeight
                    //SCALE FACTOR FOR RESULTVIEW WIDTH
                    val ivScaleX = mResultView!!.width.toFloat() / bitmap.width
                    //SCALE FACTOR FOR RESULTVIEW HEIGHT
                    val ivScaleY = mResultView!!.height.toFloat() / bitmap.height
                    //SET STARTING COORDINATES FOR RESULTVIEW
                    val startX = 0.toFloat()
                    val startY = 0.toFloat()

                    //Log.d(TAG, "imgscalex: ${imgScaleX}")
                    //Log.d(TAG, "imgscaley: ${imgScaleY}")
                    //Log.d(TAG, "ivscalex: ${ivScaleX}")
                    //Log.d(TAG, "ivscaley: ${ivScaleY}")
                    //Log.d(TAG, "startx: ${startX}")
                    //Log.d(TAG, "starty: ${startY}")



                    //TAKE OUTPUTS AND PROCESS IT INTO PREDICTIONS
                    results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, startX, startY)
                }
            }
//            // Since format in ImageAnalysis is YUV, image.planes[0] contains the luminance plane
//            val buffer = image.planes[0].buffer
//
//            // Extract image data from callback object
//            val data = buffer.toByteArray()
//
//            // Convert the data into an array of pixel values ranging 0-255
//            val pixels = data.map { it.toInt() and 0xFF }
//
//            // Compute average luminance for the image
//            val luma = pixels.average()

            // Call all listeners with new value
             listeners.forEach { it(results) }

            image.close()
        }
    }

    fun setPhotoCount(Current: Int, Total: Int) {
        val countText: TextView = cameraUiContainerBinding?.photoCountText ?: TextView(context)
        countText.text = "$Current OF $Total"
    }

    /** Add inputted ammount to the zoom ratio */
    fun addZoom(ammount: Float) {
        zoomRatio += ammount
        camera?.cameraControl?.setZoomRatio(zoomRatio)
    }

    companion object {

        private const val TAG = "CameraXBasic"
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
                File(baseFolder, SimpleDateFormat(format, Locale.US)
                        .format(System.currentTimeMillis()) + extension)
    }
}
