package com.dji.activationDemo.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.Navigation
import com.dji.activationDemo.MainActivity
import com.dji.activationDemo.R
import com.dji.activationDemo.databinding.FragmentTaskDetailsBinding


class TaskDetailsFragment : Fragment() {
    //directly bind data to UI in on create
    private var _fragmentTaskDetailsBinding: FragmentTaskDetailsBinding? = null
    private val fragmentTaskDetailsBinding get() = _fragmentTaskDetailsBinding!!
//    var jobName: String = "";
//    var jobLocation: String = "";
//    var taskId: String = "";
//    var scanMember: String = "";
//    var pieceMark: String = "";
//    var location: String = "";
//    var elevation: String = "";
//    var weldType: String = "";
//    var weldSize: String = "";
//    var weldLength: String = "";






    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) : View {
        _fragmentTaskDetailsBinding = FragmentTaskDetailsBinding.inflate(inflater, container, false)
        return fragmentTaskDetailsBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val bundle = this.arguments
        if (bundle != null) {
            fragmentTaskDetailsBinding.jobName.text = bundle.getString("jobName")
            fragmentTaskDetailsBinding.jobLocation.text = bundle.getString("jobLocation")
            fragmentTaskDetailsBinding.taskId.text = bundle.getString("taskId")
            fragmentTaskDetailsBinding.taskDetails.scanMember.text = bundle.getString("scanMember")
            fragmentTaskDetailsBinding.taskDetails.pieceMark.text = bundle.getString("pieceMark")
            fragmentTaskDetailsBinding.taskDetails.location.text = bundle.getString("location")
            fragmentTaskDetailsBinding.taskDetails.elevation.text = bundle.getString("elevation")
            fragmentTaskDetailsBinding.taskDetails.weldType.text = bundle.getString("weldType")
            fragmentTaskDetailsBinding.taskDetails.weldSize.text = bundle.getString("weldSize")
            fragmentTaskDetailsBinding.taskDetails.weldLength.text = bundle.getString("weldLength")

        }

        // bind scan button to navigate to camera along with passing current task Id
        fragmentTaskDetailsBinding.taskDetails.taskDetailsScanBtn.setOnClickListener {
            // Instead of passing data, just change companion object in MainActivity so
            // when we call upload image, it will upload to the right ID
            if (bundle != null) {
                MainActivity.setCurrentTaskId(bundle.getString("taskId"))
                MainActivity.setCurrentJobtId(bundle.getString("jobId"))
            }
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(R.id.action_taskDetails_to_cameraFragment)
        }

    }
}