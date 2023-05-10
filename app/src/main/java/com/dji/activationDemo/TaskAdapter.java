package com.dji.activationDemo;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.dji.activationDemo.fragments.CameraFragment;
import com.dji.activationDemo.fragments.TaskListFragment;

import java.util.ArrayList;

public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.Viewholder> {
    private Context context;
    private ArrayList<TaskModel> taskModelArrayList;
//    public Fragment fragment = new Fragment();

    // Constructor
    public TaskAdapter(Context context, ArrayList<TaskModel> taskModelArrayList) {
        this.context = context;
        this.taskModelArrayList = taskModelArrayList;
    }

    @NonNull
    @Override
    public TaskAdapter.Viewholder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // to inflate the layout for each item of recycler view.
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_layout, parent, false);
        return new Viewholder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskAdapter.Viewholder holder, int position) {
        // to set data to textview and imageview of each card layout
        TaskModel model = taskModelArrayList.get(position);
        holder.taskIdTV.setText("Task ID: " + model.getTask_id());
        holder.itemView.findViewById(R.id.idTVTaskId).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.d("SUCCESS", "PASSING DATA TO DETAILS FRAGMENT");
                Bundle bundle = new Bundle();
                bundle.putString("jobName", model.getWeldJobName());
                bundle.putString("jobId", model.getWeldJobId());
                bundle.putString("taskId", model.getTask_id());
                bundle.putString("scanMember", model.getScanMember());
                bundle.putString("pieceMark", model.getPieceMark());
                bundle.putString("location", model.getLocation());
                bundle.putString("elevation", model.getElevation());
                bundle.putString("weldType", model.getWeldType());
                bundle.putString("weldSize", model.getWeldSize());
                bundle.putString("weldLength", model.getWeldLength());
                bundle.putString("weldJobLocation", model.getWeldJobLocation());
//                fragment.setArguments(bundle);
                Navigation.findNavController(view).navigate(R.id.action_taskList_to_taskDetails, bundle);
//                Intent intent = new Intent(context, TaskDetails.class);
//                intent.putExtra("jobName", model.getWeldJobName());
//                intent.putExtra("taskId", model.getTask_id());
//                intent.putExtra("scanMember", model.getScanMember());
//                intent.putExtra("pieceMark", model.getPieceMark());
//                intent.putExtra("location", model.getLocation());
//                intent.putExtra("elevation", model.getElevation());
//                intent.putExtra("weldType", model.getWeldType());
//                intent.putExtra("weldSize", model.getWeldSize());
//                intent.putExtra("weldLength", model.getWeldLength());
//                intent.putExtra("weldJobLocation", model.getWeldJobLocation());
//                context.startActivity(intent);


            }
        });

    }


    @Override
    public int getItemCount() {
        // this method is used for showing number
        // of card items in recycler view.
        return taskModelArrayList.size();
    }

    // View holder class for initializing of
    // your views such as TextView and Imageview.
    public class Viewholder extends RecyclerView.ViewHolder {
        private TextView taskIdTV;

        public Viewholder(@NonNull View itemView) {
            super(itemView);
            taskIdTV = itemView.findViewById(R.id.idTVTaskId);
        }
    }

    public void filterList(ArrayList<TaskModel> filtered_list) {
        taskModelArrayList = filtered_list;
        notifyDataSetChanged();

    }
}
