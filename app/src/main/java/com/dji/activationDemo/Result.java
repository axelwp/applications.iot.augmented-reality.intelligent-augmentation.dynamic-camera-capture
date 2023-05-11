package com.dji.activationDemo;

import android.graphics.Rect;

// This class represents the result of an object detection inference
public class Result {
    public int classIndex;     // The index of the predicted class
    Float score;     // The confidence score of the prediction
    public Rect rect;     // The bounding box of the predicted object

    // Constructor to initialize the Result object
    public Result(int cls, Float output, Rect rect) {
        this.classIndex = cls;
        this.score = output;
        this.rect = rect;
    }
}
