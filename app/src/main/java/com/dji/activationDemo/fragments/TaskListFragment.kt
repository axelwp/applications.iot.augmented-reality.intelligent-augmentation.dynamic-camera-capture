package com.dji.activationDemo.fragments

import android.R
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.Navigation
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener
import com.dji.activationDemo.MainActivity
import com.dji.activationDemo.TaskAdapter
import com.dji.activationDemo.TaskModel
import com.dji.activationDemo.databinding.FragmentTaskListBinding
import org.json.JSONArray
import org.json.JSONException


class TaskListFragment : Fragment() {
    private var _fragmentTaskListBinding: FragmentTaskListBinding? = null
    private val fragmentTaskListBinding get() = _fragmentTaskListBinding!!
    private var courseRV: RecyclerView? = null
    private var searchView: AutoCompleteTextView? = null
    private var taskModelArrayList: ArrayList<TaskModel>? = ArrayList<TaskModel>()
    var weldJobLocationSuggestion = ArrayList<String>()
    private var taskAdapter: TaskAdapter? = null
    //private val main_context: Context = this
    var weld_task_array: JSONArray? = null
    private var hashMapSuggestion: HashMap<String, String> = HashMap()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Get task list from api calls
        weld_task_array = MainActivity.getTaskList()


        //filtering data and rendering appropriate task id into ui

        //filtering data and rendering appropriate task id into ui
        for (i in 0 until weld_task_array!!.length()) {
            //grab each json object one by one and get each part of the data from the json object
            try {
                var task = weld_task_array!!.getJSONObject(i)
                var weldJobId = task.getString("weldJobId")
                var weldJobName = task.getString("weldJobName")
                var weldJobLocation = task.getString("weldJobLocation")
                var taskId = task.getString("taskId")
                var scanMember = task.getString("scanMember")
                var pieceMark = task.getString("pieceMark")
                var location = task.getString("location")
                var elevation = task.getString("elevation")
                var weldType = task.getString("weldType")
                var weldSize = task.getString("weldSize")
                var weldLength = task.getString("weldLength")
                var weldInterMarkerDistance = task.getString("weldInterMarkerDistance")
                var status = task.getInt("status")
                var statusMessage = task.getString("statusMessage")
                Log.d("SUCCESS", taskId)
                if (status == 0) {
                    // insert webJobLocation into the hashMap if hashMap doesn't have it yet
                    if (!hashMapSuggestion.containsKey(weldJobLocation)) {
                        Log.d("POPULATINGHASH", weldJobLocation)
                        hashMapSuggestion[weldJobLocation] = weldJobLocation
                    }
                    Log.d("SUCCESS", "Adding new ID$taskId")
                    taskModelArrayList!!.add(
                        TaskModel(
                            taskId, weldJobName, weldJobLocation,
                            weldJobId, scanMember, pieceMark,
                            location, elevation, weldType,
                            weldSize, weldLength, weldInterMarkerDistance,
                            status, statusMessage
                        )
                    )
                }
            } catch (e: JSONException) {
                e.printStackTrace()
            }
        }

        for ((_, value) in hashMapSuggestion) {
            weldJobLocationSuggestion.add(value)
            Log.d("POPSUGGESTION", value)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) : View {
        _fragmentTaskListBinding = FragmentTaskListBinding.inflate(inflater, container, false)
        return fragmentTaskListBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fragmentTaskListBinding.searchBar.hint = "Search Location";
        var itemsAdapter: ArrayAdapter<String> =
            ArrayAdapter<String>(requireContext(), R.layout.simple_list_item_1, weldJobLocationSuggestion)
        fragmentTaskListBinding.searchBar.setAdapter(itemsAdapter)
        fragmentTaskListBinding.searchBar!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                getSearchResult(charSequence.toString())
            }

            override fun onTextChanged(charSequence: CharSequence, i: Int, i1: Int, i2: Int) {
                getSearchResult(charSequence.toString())
            }

            override fun afterTextChanged(editable: Editable) {}
        })

        taskAdapter = TaskAdapter(requireContext(), taskModelArrayList)

        // below line is for setting a layout manager for our recycler view.
        // here we are creating vertical list so we will provide orientation as vertical

        // below line is for setting a layout manager for our recycler view.
        // here we are creating vertical list so we will provide orientation as vertical
        val linearLayoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        // in below two lines we are setting layoutmanager and adapter to our recycler view.

        // in below two lines we are setting layoutmanager and adapter to our recycler view.
        fragmentTaskListBinding.idTVTaskId!!.layoutManager = linearLayoutManager
        fragmentTaskListBinding.idTVTaskId!!.adapter = taskAdapter

        /** BINDING NAVIGATION TO INDIVIDUAL ITEM */
        //bind onclick item of recyler view with navigation function
//        fragmentTaskListBinding.idTVTaskId.addOnItemTouchListener(object :
//            OnItemTouchListener {
//            private val itemTouchListener: OnItemTouchListener? = null
//            override fun onInterceptTouchEvent(
//                recyclerView: RecyclerView,
//                motionEvent: MotionEvent
//            ): Boolean {
//                return false
//            }
//
//            override fun onTouchEvent(recyclerView: RecyclerView, motionEvent: MotionEvent) {
//                navigateToTaskDetails()
//            }
//            override fun onRequestDisallowInterceptTouchEvent(b: Boolean) {}
//        })

    }
    fun getSearchResult(text: String) {
        val filtered_list = java.util.ArrayList<TaskModel>()
        //if text is an empty string, return all
        if (text === "") {
            taskAdapter!!.filterList(taskModelArrayList)
            return
        }
        for (item in taskModelArrayList!!) {
            if (item.weldJobLocation.lowercase().contains(text.lowercase())) {
                filtered_list.add(item)
            }
        }
        if (filtered_list.isEmpty()) {
            Log.d("FILTERING: ", "NO DATA FOUND")
        } else {
            taskAdapter!!.filterList(filtered_list)
        }
    }

    fun navigateToTaskDetails() {
        Log.d("BINDING_DB", "SUCCESS")
        lifecycleScope.launchWhenStarted {
            Navigation.findNavController(requireActivity(), com.dji.activationDemo.R.id.fragment_container).navigate(
                TaskListFragmentDirections.actionTaskListToTaskDetails()
            )
        }
    }

}