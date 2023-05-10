package com.dji.activationDemo;

import android.graphics.Rect;

public class Result {
    public int classIndex;
    Float score;
    public Rect rect;

    public Result(int cls, Float output, Rect rect) {
        this.classIndex = cls;
        this.score = output;
        this.rect = rect;
    }
}
