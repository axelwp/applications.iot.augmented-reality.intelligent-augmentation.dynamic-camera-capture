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
import android.content.Context
import android.graphics.*
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.impl.utils.ContextUtil
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.navigation.Navigation
import androidx.navigation.fragment.navArgs
import androidx.viewpager.widget.ViewPager
import com.android.example.cameraxbasic.utils.padWithDisplayCutout
import com.android.example.cameraxbasic.utils.showImmersive
import com.dji.activationDemo.*
import com.dji.activationDemo.databinding.FragmentGalleryBinding
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.torchvision.TensorImageUtils
import java.io.*
import java.util.*

val EXTENSION_WHITELIST = arrayOf("JPG")

/** Fragment used to present the user with a gallery of photos taken */
class GalleryFragment internal constructor() : Fragment() {

    /** Android ViewBinding */
    private var _fragmentGalleryBinding: FragmentGalleryBinding? = null

    private val fragmentGalleryBinding get() = _fragmentGalleryBinding!!

    /** AndroidX navigation arguments */
    private val args: GalleryFragmentArgs by navArgs()

    // Declare private variables
    private lateinit var mediaList: MutableList<File>
    private var mBitmap: Bitmap? = null
    private var mBitmap2: Bitmap? = null
    private var mModule: Module? = null
    private var mModule2: Module? = null
    private var mResultView: ResultView? = null
    private var mResultView2: ResultView2? = null
    private var mButtonDetect: Button? = null
    private var index: Int = 0
    private lateinit var myContext: Context
    private val classes: MutableList<String> = ArrayList()
    private val classes2: MutableList<String> = ArrayList()

    /** Adapter class used to present a fragment containing one photo or video as a page */
    inner class MediaPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {
        override fun getCount(): Int = mediaList.size
        override fun getItem(position: Int): Fragment = PhotoFragment.create(mediaList[position])
        override fun getItemPosition(obj: Any): Int = POSITION_NONE

    }

    @SuppressLint("RestrictedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.e("onCreate was called", "true")

        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
        retainInstance = true

        // Try to load the weld and anomaly detection models
        // Load in the classnames from the .txt files contained in the same directory
        try {
            //Load the weld detection model
            mModule = LiteModuleLoader.load(MainActivity.assetFilePath(ContextUtil.getApplicationContext(myContext), "best.torchscript.ptl"))
            val br = BufferedReader(InputStreamReader(myContext.getAssets().open("classes.txt")))
            val iterator = br.lineSequence().iterator()
            while (iterator.hasNext()) {
                val line = iterator.next()
                classes.add(line)
            }
            //Load the anomaly detection model
            mModule2 = LiteModuleLoader.load(MainActivity.assetFilePath(ContextUtil.getApplicationContext(myContext), "maskInTrainLast.torchscript.ptl"))
            val br2 = BufferedReader(InputStreamReader(myContext.getAssets().open("classes_defect.txt")))
            val iterator2 = br2.lineSequence().iterator()
            while (iterator2.hasNext()) {
                val line = iterator2.next()
                classes2.add(line)
            }
            PrePostProcessor.mClasses = classes.toTypedArray()
        } catch (e: IOException) {
            // Log an error message if something goes wrong while reading the assets
            Log.e("Object Detection", "Error reading assets", e)
        }

    }
    override fun onAttach(myContext: Context) {
        super.onAttach(myContext)
        // Store a reference to the attached context
        this.myContext = myContext
    }

    // A function that crops a rectangular region from a given bitmap and returns a new bitmap of the cropped region
    private fun cropImage(bitmap: Bitmap, rect: Rect): Bitmap {
        // create a new bitmap with the dimensions of the rectangular region to be cropped
        val croppedBitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)

        // create a canvas object with the new bitmap
        val canvas = Canvas(croppedBitmap)

        // create source and destination rectangles for cropping
        val srcRect = Rect(rect.left, rect.top, rect.right, rect.bottom)
        val dstRect = Rect(0, 0, rect.width(), rect.height())

        // draw the cropped region from the source bitmap to the new bitmap using the canvas
        canvas.drawBitmap(bitmap, srcRect, dstRect, null)

        // return the cropped bitmap
        return croppedBitmap
    }

    private fun runImageDetection(index: Int, mModule: Module) {
        var results = ArrayList<Result>()
        var results2 = ArrayList<Result>()
        var cropBox: Rect

        //Set the bitmap to be displayed to the current gallery index
        mBitmap = BitmapFactory.decodeFile(mediaList[index].absolutePath)

        //CREATE MATRIX OBJECT AND ROTATE BITMAP BY 90 DEGREES
        val matrix = Matrix()
        //CREATE A NEW BITMAP WITH SAME ASPECT RATIO AS ORIGINAL BITMAP, BUT WITH SMALLER SIZE
        var bitmap = Bitmap.createBitmap(mBitmap!!, 0, 0, mBitmap!!.width, mBitmap!!.height, matrix, true)
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

        //TAKE OUTPUTS AND PROCESS IT INTO PREDICTIONS
        results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, startX, startY)

        if(results.size >= 1){
            //undo the operations within PrePostProcessor
            var rHeight = results[0].rect.height()
            var rWidth = results[0].rect.width()

            //enforce the crop region to be of aspect ratio 1:1 by getting the difference between the largest
            // and smallest dimension, then adding 1/2 the difference to each side of the rect to guarantee a square
            if(rHeight < rWidth){
                rHeight = rWidth - rHeight
                rHeight /= 2
                rWidth = 0
            } else {
                rWidth = rHeight - rWidth
                rWidth /= 2
                rHeight = 0
            }
            // Crop the bitmap based on the rectangle coordinates
            cropBox = Rect((((results[0].rect.left - rWidth - startX) / ivScaleX).toInt()),
                (((results[0].rect.top + rHeight - startY) / ivScaleY).toInt()),
                (((results[0].rect.right + rWidth - startX) / ivScaleX).toInt()),
                (((results[0].rect.bottom - rHeight - startY) / ivScaleY).toInt()))

            // Calculate the boundaries of the mResultView
            val x1 = 0
            val y1 = 0
            val x2 = mResultView!!.width
            val y2 = mResultView!!.height

            // Adjust the coordinates of the cropBox
            val adjustedLeft = cropBox.left.coerceIn(x1, x2)
            val adjustedTop = cropBox.top.coerceIn(y1, y2)
            val adjustedRight = cropBox.right.coerceIn(x1, x2)
            val adjustedBottom = cropBox.bottom.coerceIn(y1, y2)

            // Create the adjusted cropBox
            val adjustedCropBox = Rect(adjustedLeft, adjustedTop, adjustedRight, adjustedBottom)

            //change the PrePostProcessor to the new class list
            PrePostProcessor2.mClasses = classes2.toTypedArray()

            mBitmap2 = BitmapFactory.decodeFile(mediaList[index].absolutePath)
            val file = File(mediaList[index].absolutePath)

            //CREATE MATRIX OBJECT AND ROTATE BITMAP BY 90 DEGREES
            val matrix2 = Matrix()
            //matrix.postRotate(90.0f)
            //CREATE A NEW BITMAP WITH SAME ASPECT RATIO AS ORIGINAL BITMAP, BUT WITH SMALLER SIZE
            //If one of the crop dimensions is 0, then feed the full original image into the anomaly detection model
            var rBitmap2: Bitmap = if(adjustedCropBox.width() > 0 && adjustedCropBox.height() > 0) {
                cropImage(mBitmap!!, adjustedCropBox)
            } else {
                mBitmap!!
            }


            FileOutputStream(file).use { out ->
                rBitmap2.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            val adapter = fragmentGalleryBinding.photoViewPager.adapter as? MediaPagerAdapter

            // Notify the adapter that the data set has changed
            adapter!!.notifyDataSetChanged()

            // Update the current item to display the new image
            fragmentGalleryBinding.photoViewPager.setCurrentItem(index, false)

            //Scale the bitmap to 640 x 640 so it can be passed to the model
            val resizedBitmap2 = Bitmap.createScaledBitmap(rBitmap2, PrePostProcessor2.mInputWidth, PrePostProcessor2.mInputHeight, true)

            //update the image view overlay to contain the newly cropped image
            val imageView = view?.findViewById <ImageView>(R.id.croppedImage)
            imageView!!.setImageBitmap(resizedBitmap2)

            //set the parameters for the anomaly display. these params affect imageView and resultView2
            val params = imageView.layoutParams
            params.width = resizedBitmap2.width
            params.height = resizedBitmap2.height
            imageView.layoutParams = params

            mResultView2!!.layoutParams = params

            //CONVERT BITMAP TO TENSOR
            val inputTensor2 = TensorImageUtils.bitmapToFloat32Tensor(resizedBitmap2, PrePostProcessor2.NO_MEAN_RGB, PrePostProcessor2.NO_STD_RGB)
            //RUN INPUT TENSOR THROUGH MODEL, RETURNS AS A TUPLE
            val outputTuple2: Array<IValue> = mModule2?.forward(IValue.from(inputTensor2))!!.toTuple()
            //EXTRACTS FIRST ELEMENT FROM THE OUTPUT TUPLE
            val outputTensor2 = outputTuple2[0].toTensor()
            //CONVERT TO ARRAY OF FLOAT VALUES, THIS HOLDS THE OUTPUT OF TH EMODEL FOR THE INPUT IMAGE
            val outputs2 = outputTensor2.dataAsFloatArray

            //CALCULATE SCALING FACTORS FOR IMAGE AND RESULTVIEW SO WE CAN CORRECTLY DISPLAY THE OBJECT DETECTION RESULT
            //SCALE FACTOR FOR IMAGE WIDTH
            val imgScaleX2 = rBitmap2.width.toFloat() / PrePostProcessor2.mInputWidth
            //SCALE FACTOR FOR IMAGE HEIGHT
            val imgScaleY2 = rBitmap2.height.toFloat() / PrePostProcessor2.mInputHeight
            //SCALE FACTOR FOR RESULTVIEW WIDTH
            val ivScaleX2 = mResultView2!!.width.toFloat() / rBitmap2.width
            //SCALE FACTOR FOR RESULTVIEW HEIGHT
            val ivScaleY2 = mResultView2!!.height.toFloat() / rBitmap2.height
            //SET STARTING COORDINATES FOR RESULTVIEW
            val startX2 = 0.toFloat()
            val startY2 = 0.toFloat()

            //TAKE OUTPUTS AND PROCESS IT INTO PREDICTIONS
            results2 = PrePostProcessor2.outputsToNMSPredictions(outputs2, imgScaleX2, imgScaleY2, ivScaleX2, ivScaleY2, startX2, startY2)
            Log.d("anomalies found: ", results.size.toString())
        }

        //sets the welds and anomalies to be displayed and tells ResultView to reload
        mResultView2!!.setResults(results2)
        mResultView2!!.invalidate()

        mResultView!!.setResults(results)
        mResultView!!.invalidate()

    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _fragmentGalleryBinding = FragmentGalleryBinding.inflate(inflater, container, false)
        return fragmentGalleryBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mResultView = view?.findViewById(R.id.resultView)

        mResultView2 = view?.findViewById(R.id.resultView2)
        mButtonDetect = view.findViewById(R.id.detectButton)

        //If the user presses the detect button, run the image detection function on the image contained in the current index
        mButtonDetect?.setOnClickListener {
            runImageDetection(index, mModule!!)
        }
        // Get root directory of media from navigation arguments
        val rootDirectory = File(args.rootDirectory)

        // Walk through all files in the root directory
        // We reverse the order of the list to present the last photos first
        mediaList = rootDirectory.listFiles { file ->
            EXTENSION_WHITELIST.contains(file.extension.toUpperCase(Locale.ROOT))
        }?.sortedDescending()?.toMutableList() ?: mutableListOf()

        //Log the paths of the photos contained in mediaList
        for (file in mediaList) {
            Log.d("GalleryFragment", file.absolutePath)
        }

        //Checking media files list
        if (mediaList.isEmpty()) {
            fragmentGalleryBinding.deleteButton.isEnabled = false
            fragmentGalleryBinding.shareButton.isEnabled = false
        }

        // Populate the ViewPager and implement a cache of two media items
        fragmentGalleryBinding.photoViewPager.apply {
            offscreenPageLimit = 2
            adapter = MediaPagerAdapter(childFragmentManager)
        }

        // Make sure that the cutout "safe area" avoids the screen notch if any
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use extension method to pad "inside" view containing UI using display cutout's bounds
            fragmentGalleryBinding.cutoutSafeArea.padWithDisplayCutout()
        }

        fragmentGalleryBinding.photoViewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            //When the page is scrolled (swiped), clear the ResultViews and tell them to reload
            override fun onPageScrolled(
                position: Int,
                positionOffset: Float,
                positionOffsetPixels: Int
            ) {
                mResultView!!.setResults(null)
                mResultView!!.invalidate()
                mResultView2!!.setResults(null)
                mResultView2!!.invalidate()
                view?.findViewById<ImageView>(R.id.croppedImage).setImageBitmap(null)
            }

            override fun onPageSelected(position: Int) {
                val total = mediaList.size  // Total number of media items
                index = position    // Update the current position/index
                val photo = position + 1    // Current photo number (1-based indexing)

                fragmentGalleryBinding.photoCountText.text = "$photo OF $total"    // Set the text to display the current photo count

                val bitMapOption = BitmapFactory.Options()
                bitMapOption.inJustDecodeBounds = true  // Set the inJustDecodeBounds property to true to retrieve the image dimensions without loading the full bitmap
                BitmapFactory.decodeFile(mediaList[position].absolutePath, bitMapOption)    // Decode the file path to get the image dimensions
                fragmentGalleryBinding.photoProperties.text = bitMapOption.outWidth.toString() + " x " + bitMapOption.outHeight.toString()  // Set the text to display the image dimensions
            }

            override fun onPageScrollStateChanged(state: Int) {

            }
        })

        // Handle back button press
        fragmentGalleryBinding.backButton.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp()
        }

        // Handle share button press
        fragmentGalleryBinding.shareButton.setOnClickListener {
            AlertDialog.Builder(view.context, android.R.style.Theme_Material_Dialog)
                .setTitle(getString(R.string.upload_title))
                .setMessage(getString(R.string.upload_dialog))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    Toast.makeText(context, "uploading, pls wait...", Toast.LENGTH_SHORT).show()
                    showProgress(true)
                    MainActivity.uploadImages(mediaList as ArrayList<File>, context, this)
                }
                .setNegativeButton(android.R.string.no, null)
                .create().showImmersive()
        }

        // Handle delete button press
        fragmentGalleryBinding.deleteButton.setOnClickListener {

            mediaList.getOrNull(fragmentGalleryBinding.photoViewPager.currentItem)?.let {

                AlertDialog.Builder(view.context, android.R.style.Theme_Material_Dialog)
                        .setTitle(getString(R.string.delete_title))
                        .setMessage(getString(R.string.delete_dialog))
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes) { _, _ ->
                            deleteAll()

                            // If all photos have been deleted, return to camera
                            if (mediaList.isEmpty()) {
                                Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp()
                            }
                        }
                        .setNegativeButton(android.R.string.no, null)
                        .create().showImmersive()
            }
        }
    }

    fun showProgress(visible: Boolean) {
        fragmentGalleryBinding.progressBar.isVisible = visible  // Show progress bar
        if (visible) {
            val total = mediaList.size
            fragmentGalleryBinding.photoCountText.text = "Uploading $total photos"
        }
        else {
            fragmentGalleryBinding.photoCountText.text = ""
        }
    }

    /** Deletes all the photos */
    fun deleteAll() {
        var i = 0
        // Delete all photos
        while (i < mediaList.size) {
            mediaList[i].delete()
            // Send relevant broadcast to notify other apps of deletion
            MediaScannerConnection.scanFile(context, arrayOf(mediaList[i].absolutePath), null, null)
            // Notify our view pager
            mediaList.removeAt(i)
        }
        fragmentGalleryBinding.photoViewPager.adapter?.notifyDataSetChanged()
        Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp()
    }

    override fun onDestroyView() {
        _fragmentGalleryBinding = null
        super.onDestroyView()
    }
}
