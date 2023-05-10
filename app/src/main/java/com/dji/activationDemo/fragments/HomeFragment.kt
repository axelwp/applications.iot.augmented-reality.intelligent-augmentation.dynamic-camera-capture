package com.dji.activationDemo.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import com.dji.activationDemo.MainActivity
import com.dji.activationDemo.R
import com.dji.activationDemo.databinding.FragmentHomepageBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.*


class HomeFragment : Fragment() {
    //Fetch fragment binding
    private var _fragmentHomepageBinding: FragmentHomepageBinding? = null
    private val fragmentHomepageBinding get() = _fragmentHomepageBinding!!
    private lateinit var outputDirectory: File
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _fragmentHomepageBinding = FragmentHomepageBinding.inflate(inflater, container, false)
        return fragmentHomepageBinding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentHomepageBinding.btnGallery.setOnClickListener {
            navigateToGallery()
        }
        fragmentHomepageBinding.btnHelp.setOnClickListener {
            // Need to change this so that help site will be availble without even logging in?
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://inference-tt-integ.i2a.dev/webportal/help"))
            startActivity(browserIntent)
        }

        fragmentHomepageBinding.btnScan.setOnClickListener {
            //navigateToCamera()
            /** Loc's update: instead of navigating to camera
             * navigate to task list, then from task list to task details
             * and then from task details to scan
             */
            navigateToTaskList()

        }
        fragmentHomepageBinding.btnSettings.setOnClickListener {
            navigateToSettings()
        }
        fragmentHomepageBinding.btnBack.setOnClickListener {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigateUp()
        }
    }
//    private fun navigateToCamera() {
//        lifecycleScope.launchWhenStarted {
//            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
//                HomeFragmentDirections.actionHomeFragmentToCameraFragment()
//            )
//        }
//    }
    private fun navigateToSettings() {
        lifecycleScope.launchWhenStarted {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                HomeFragmentDirections.actionHomeFragmentToSettingsFragment()
            )
        }
    }

    private fun navigateToGallery() {
        // Determine the output directory
        outputDirectory = MainActivity.getOutputDirectory(requireContext())
        lifecycleScope.launchWhenStarted {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                HomeFragmentDirections.actionHomeFragmentToGalleryFragment(outputDirectory.absolutePath)
            )
        }
    }
    private fun navigateToTaskList() {
//        MainActivity.getTaskList()
        lifecycleScope.launchWhenStarted {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(HomeFragmentDirections.actionHomeFragmentToTaskList())
        }
    }


}