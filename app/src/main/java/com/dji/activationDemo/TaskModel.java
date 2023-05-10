package com.dji.activationDemo;

public class TaskModel {

    private String task_id;
    private String weldJobName;
    private String weldJobLocation;
    private String weldJobId;
    private String scanMember;
    private String pieceMark;
    private String location;
    private String elevation;
    private String weldType;
    private String weldSize;
    private String weldLength;
    private String weldInterMarkerDistance;
    private Integer status;
    private String statusMessage;
    // Constructor
    public TaskModel(String task_id, String weldJobName, String weldJobLocation,
                     String weldJobId, String scanMember, String pieceMark,
                     String location, String elevation, String weldType,
                     String weldSize, String weldLength, String weldInterMarkerDistance,
                     Integer status, String statusMessage) {
        this.task_id = task_id;
        this.weldJobId = weldJobId;
        this.weldJobName = weldJobName;
        this.weldJobLocation = weldJobLocation;
        this.scanMember = scanMember;
        this.pieceMark = pieceMark;
        this.location = location;
        this.weldSize = weldSize;
        this.elevation = elevation;
        this.weldType = weldType;
        this.weldLength = weldLength;
        this.weldInterMarkerDistance = weldInterMarkerDistance;
        this.status = status;
        this.statusMessage = statusMessage;
    }

    // Getter and Setter
    public String getTask_id() { return task_id; }
    public String getWeldJobId() { return weldJobId; }
    public String getWeldJobName() { return weldJobName; }
    public String getWeldJobLocation() { return weldJobLocation; }
    public String getScanMember() { return scanMember; }
    public String getPieceMark() { return pieceMark; }
    public String getLocation() { return location; }
    public String getElevation() { return elevation; }
    public String getWeldType() { return weldType; }
    public String getWeldSize() { return weldSize; }
    public String getWeldLength() { return weldLength; }
    public String getWeldInterMarkerDistance() { return weldInterMarkerDistance; }
    public Integer getStatus() { return status; }
    public String getStatusMessage() { return statusMessage; }



    public void setTask_id(String task_id) {
        this.task_id = task_id;
    }
}
