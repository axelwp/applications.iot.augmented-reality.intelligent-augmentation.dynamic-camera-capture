package com.dji.activationDemo;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class TaskDetails extends AppCompatActivity {
    //directly bind data to UI in on create
    private TextView jobName;
    private TextView taskId;
    private TextView scanMember;
    private TextView pieceMark;
    private TextView location;
    private TextView elevation;
    private TextView weldType;
    private TextView weldSize;
    private TextView weldLength;
    private TextView jobLocation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_task_details);
        // bind jobname
        jobName = findViewById(R.id.job_name);
        jobName.setText("Job Name: " + getIntent().getStringExtra("jobName"));

        //bind jobLocation
        jobLocation = findViewById(R.id.job_location);
        jobLocation.setText("Job Location: " + getIntent().getStringExtra("weldJobLocation"));

        //bind taskId
        taskId = findViewById(R.id.task_id);
        taskId.setText("Task ID: " + getIntent().getStringExtra("taskId"));

        //bind Member
        scanMember = findViewById(R.id.scan_member);
        scanMember.setText(getIntent().getStringExtra("scanMember"));

        //bind piece mark
        pieceMark = findViewById(R.id.piece_mark);
        pieceMark.setText(getIntent().getStringExtra("pieceMark"));

        //bind location
        location = findViewById(R.id.location);
        location.setText(getIntent().getStringExtra("location"));

        //bind elevation
        elevation = findViewById(R.id.elevation);
        elevation.setText(getIntent().getStringExtra("elevation"));

        //bind weld type
        weldType = findViewById(R.id.weld_type);
        weldType.setText(getIntent().getStringExtra("weldType"));

        //bind weld size
        weldSize = findViewById(R.id.weld_size);
        weldSize.setText(getIntent().getStringExtra("weldSize"));

        //bind weld length
        weldLength = findViewById(R.id.weld_length);
        weldSize.setText(getIntent().getStringExtra("weldLength"));






    }
}