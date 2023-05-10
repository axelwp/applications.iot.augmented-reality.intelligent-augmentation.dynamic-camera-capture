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



        // initialize mediaList if it is null

        //mediaList = mediaList ?: mutableListOf()
        try {

            mModule = LiteModuleLoader.load(MainActivity.assetFilePath(ContextUtil.getApplicationContext(myContext), "best.torchscript.ptl"))
            val br = BufferedReader(InputStreamReader(myContext.getAssets().open("classes.txt")))
            val iterator = br.lineSequence().iterator()
            //val classes: MutableList<String> = ArrayList()
            while (iterator.hasNext()) {
                val line = iterator.next()
                classes.add(line)
            }
            mModule2 = LiteModuleLoader.load(MainActivity.assetFilePath(ContextUtil.getApplicationContext(myContext), "maskInTrainLast.torchscript.ptl"))
            val br2 = BufferedReader(InputStreamReader(myContext.getAssets().open("classes_defect.txt")))
            val iterator2 = br2.lineSequence().iterator()
            while (iterator2.hasNext()) {
                val line = iterator2.next()
                classes2.add(line)
            }
            PrePostProcessor.mClasses = classes.toTypedArray()
        } catch (e: IOException) {
            Log.e("Object Detection", "Error reading assets", e)
        }

    }
    override fun onAttach(myContext: Context) {
        super.onAttach(myContext)
        this.myContext = myContext
    }

    private fun cropImage(bitmap: Bitmap, rect: Rect): Bitmap {
        val croppedBitmap = Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(croppedBitmap)
        val srcRect = Rect(rect.left, rect.top, rect.right, rect.bottom)
        val dstRect = Rect(0, 0, rect.width(), rect.height())
        canvas.drawBitmap(bitmap, srcRect, dstRect, null)
        return croppedBitmap
    }

    private fun runImageDetection(index: Int, mModule: Module) {
        var results = ArrayList<Result>()
        var results2 = ArrayList<Result>()
        val resultSet = ArrayList<Result>()
        var cropBox: Rect
        //runBlocking {
            //launch(Dispatchers.Default) {
                mBitmap = BitmapFactory.decodeFile(mediaList[index].absolutePath)


                //CREATE MATRIX OBJECT AND ROTATE BITMAP BY 90 DEGREES
                val matrix = Matrix()
                //matrix.postRotate(90.0f)
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

                val TAG: String = "Gallery: "
                /*
                Log.d(TAG, "imgscalex: ${imgScaleX}")
                Log.d(TAG, "imgscaley: ${imgScaleY}")
                Log.d(TAG, "ivscalex: ${ivScaleX}")
                Log.d(TAG, "ivscaley: ${ivScaleY}")
                Log.d(TAG, "startx: ${startX}")
                Log.d(TAG, "starty: ${startY}")
                */

                //TAKE OUTPUTS AND PROCESS IT INTO PREDICTIONS
                results = PrePostProcessor.outputsToNMSPredictions(outputs, imgScaleX, imgScaleY, ivScaleX, ivScaleY, startX, startY)
                Log.d("result size", results.size.toString())

                if(results.size >= 1){
                    //undo the operations within PrePostProcessor
                    var rHeight = results[0].rect.height()
                    var rWidth = results[0].rect.width()

                    if(rHeight < rWidth){
                        rHeight = rWidth - rHeight
                        rHeight /= 2
                        rWidth = 0
                    } else {
                        rWidth = rHeight - rWidth
                        rWidth /= 2
                        rHeight = 0
                    }
                    cropBox = Rect((((results[0].rect.left - rWidth - startX) / ivScaleX).toInt()), (((results[0].rect.top + rHeight - startY) / ivScaleY).toInt()), (((results[0].rect.right + rWidth - startX) / ivScaleX).toInt()), (((results[0].rect.bottom - rHeight - startY) / ivScaleY).toInt()))

                    // Crop the bitmap based on the rectangle coordinates
                    // Crop the bitmap based on the rectangle coordinates

                    //change the PrePostProcessor to the new class list
                    PrePostProcessor2.mClasses = classes2.toTypedArray()

                    mBitmap2 = BitmapFactory.decodeFile(mediaList[index].absolutePath)
                    val file = File(mediaList[index].absolutePath)


                    //CREATE MATRIX OBJECT AND ROTATE BITMAP BY 90 DEGREES
                    val matrix2 = Matrix()
                    //matrix.postRotate(90.0f)
                    //CREATE A NEW BITMAP WITH SAME ASPECT RATIO AS ORIGINAL BITMAP, BUT WITH SMALLER SIZE
                    //var bitmap2 = Bitmap.createBitmap(mBitmap2!!, 0, 0, mBitmap2!!.width, mBitmap2!!.height, matrix2, true)
                    Log.d("rectangle height: ", results[0].rect.height().toString())
                    Log.d("rectangle width: ", results[0].rect.width().toString())



                    val rBitmap2: Bitmap = cropImage(mBitmap!!, cropBox)
                    Log.d("bitmap height: ", rBitmap2.height.toString())

                    Log.d("bitmap width: ", rBitmap2.width.toString())

                    FileOutputStream(file).use { out ->
                        rBitmap2.compress(Bitmap.CompressFormat.JPEG, 100, out)
                    }
                    val adapter = fragmentGalleryBinding.photoViewPager.adapter as? MediaPagerAdapter

                    // Notify the adapter that the data set has changed
                    adapter!!.notifyDataSetChanged()

                    // Update the current item to display the new image
                    fragmentGalleryBinding.photoViewPager.setCurrentItem(index, false)


                    val resizedBitmap2 = Bitmap.createScaledBitmap(rBitmap2, PrePostProcessor2.mInputWidth, PrePostProcessor2.mInputHeight, true)
                    val imageView = view?.findViewById <ImageView>(R.id.croppedImage)
                    imageView!!.setImageBitmap(resizedBitmap2)

                    val params = imageView.layoutParams
                    params.width = resizedBitmap2.width
                    params.height = resizedBitmap2.height
                    imageView.layoutParams = params

                    mResultView2!!.layoutParams = params

                    Log.d("ImageView height", params.height.toString())
                    Log.d("ImageView width", params.width.toString())
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

                    //val TAG2: String = "Gallery: "
                    /*
                    Log.d(TAG, "imgscalex: ${imgScaleX}")
                    Log.d(TAG, "imgscaley: ${imgScaleY}")
                    Log.d(TAG, "ivscalex: ${ivScaleX}")
                    Log.d(TAG, "ivscaley: ${ivScaleY}")
                    Log.d(TAG, "startx: ${startX}")
                    Log.d(TAG, "starty: ${startY}")
                    */
                    Log.d("Weld location: ", results[0].rect.toString())

                        //TAKE OUTPUTS AND PROCESS IT INTO PREDICTIONS
                        results2 = PrePostProcessor2.outputsToNMSPredictions(outputs2, imgScaleX2, imgScaleY2, ivScaleX2, ivScaleY2, startX2, startY2)
                        Log.d("anomalies found: ", results.size.toString())

                        val cropBox = results[0].rect // the bounding box used to crop the original image
                        val cropWidth = cropBox.width()
                        val cropHeight = cropBox.height()

                        val scaleX = mBitmap!!.width.toFloat() / PrePostProcessor2.mInputWidth
                        val scaleY = mBitmap!!.height.toFloat() / PrePostProcessor2.mInputHeight

                        //results2.forEach {
                            //val rect = it.rect
                            //Log.d("Before scaling: ", it.rect.toString())

                            //var left = rect.left * imgScaleX2 * ivScaleX
                            //var top = rect.top * imgScaleY2 * ivScaleY
                            //var right = rect.right * imgScaleX2 * ivScaleX
                            //var bottom = rect.bottom * imgScaleY2 * ivScaleY

                            //var scaledBox = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())
                            //Log.d("After scaling: ", scaledBox.toString())
                            //left = cropBox.left + (scaledBox.left.toFloat() * cropWidth / PrePostProcessor.mInputWidth)
                            //right = cropBox.right + (scaledBox.right.toFloat() * cropWidth / PrePostProcessor.mInputWidth)
                            //top = cropBox.top + (scaledBox.top.toFloat() * cropHeight / PrePostProcessor.mInputWidth)
                            //bottom = cropBox.bottom + (scaledBox.bottom.toFloat() * cropHeight / PrePostProcessor.mInputWidth)
                            //var transformedRect = Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())


                            //val x = rect.left + cropBox.left
                            //val y = rect.top + cropBox.top
                            //val transformedRect = Rect(x, y, rect.right + cropBox.left, rect.bottom + cropBox.top)
                            //Log.d("After transforming: ", transformedRect.toString())
                            //it.rect = transformedRect
                        //}
                //}
            //}
        }


        mResultView2!!.setResults(results2)
        mResultView2!!.invalidate()

        mResultView!!.setResults(results)
        mResultView!!.invalidate()

        //mResultView = mResultView2
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


        for (file in mediaList) {
            Log.d("GalleryFragment", file.absolutePath)
        }
        //mediaList = mediaList ?: mutableListOf()

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
                val total = mediaList.size
                index = position
                val photo = position + 1
                fragmentGalleryBinding.photoCountText.text = "$photo OF $total"
                val bitMapOption = BitmapFactory.Options()
                bitMapOption.inJustDecodeBounds = true
                BitmapFactory.decodeFile(mediaList[position].absolutePath, bitMapOption)
                fragmentGalleryBinding.photoProperties.text = bitMapOption.outWidth.toString() + " x " + bitMapOption.outHeight.toString()



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

    fun performAnomalyDetection() {

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
