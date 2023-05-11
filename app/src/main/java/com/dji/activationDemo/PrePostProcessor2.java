package com.dji.activationDemo;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;


public class PrePostProcessor2 {
    // for yolov5 model, no need to apply MEAN and STD
    public static float[] NO_MEAN_RGB = new float[] {0.0f, 0.0f, 0.0f};
    public static float[] NO_STD_RGB = new float[] {1.0f, 1.0f, 1.0f};

    // model input image size
    public static int mInputWidth = 640;
    public static int mInputHeight = 640;

    // model output is of size 25200*(num_of_class+5)
    private static int mOutputRow = 25200; // as decided by the YOLOv5 model for input image of size 640*640
    private static int mOutputColumn = 6; // left, top, right, bottom, score and 80 class probability
    private static float mThreshold = 0.30f; // score above which a detection is generated
    private static int mNmsLimit = 15;

    public static String[] mClasses;

    // The two methods nonMaxSuppression and IOU below are ported from https://github.com/hollance/YOLO-CoreML-MPSNNGraph/blob/master/Common/Helpers.swift
    /**
     Removes bounding boxes that overlap too much with other boxes that have
     a higher score.
     - Parameters:
     - boxes: an array of bounding boxes and their scores
     - limit: the maximum number of boxes that will be selected
     - threshold: used to decide whether boxes overlap too much
     */
    static ArrayList<Result> nonMaxSuppression(ArrayList<Result> boxes, int limit, float threshold) {

        // Do an argsort on the confidence scores, from high to low.
        Collections.sort(boxes,
                new Comparator<Result>() {
                    @Override
                    public int compare(Result o1, Result o2) {
                        return o1.score.compareTo(o2.score);
                    }
                });

        ArrayList<Result> selected = new ArrayList<>();
        boolean[] active = new boolean[boxes.size()];
        Arrays.fill(active, true);
        int numActive = active.length;

        // The algorithm is simple: Start with the box that has the highest score.
        // Remove any remaining boxes that overlap it more than the given threshold
        // amount. If there are any boxes left (i.e. these did not overlap with any
        // previous boxes), then repeat this procedure, until no more boxes remain
        // or the limit has been reached.
        boolean done = false;
        for (int i=0; i<boxes.size() && !done; i++) {
            if (active[i]) {
                Result boxA = boxes.get(i);
                selected.add(boxA);
                if (selected.size() >= limit) break;

                for (int j=i+1; j<boxes.size(); j++) {
                    if (active[j]) {
                        Result boxB = boxes.get(j);
                        if (IOU(boxA.rect, boxB.rect) > threshold) {
                            active[j] = false;
                            numActive -= 1;
                            if (numActive <= 0) {
                                done = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        return selected;
    }

    /**
     Computes intersection-over-union overlap between two bounding boxes.
     */
    static float IOU(Rect a, Rect b) {
        // Calculate the area of the first bounding box
        float areaA = (a.right - a.left) * (a.bottom - a.top);
        // If the area is 0 or negative, the boxes do not overlap, so return 0.
        if (areaA <= 0.0) return 0.0f;

        // Calculate the area of the second bounding box
        float areaB = (b.right - b.left) * (b.bottom - b.top);
        // If the area is 0 or negative, the boxes do not overlap, so return 0.
        if (areaB <= 0.0) return 0.0f;

        // Calculate the coordinates of the intersection rectangle
        float intersectionMinX = Math.max(a.left, b.left);
        float intersectionMinY = Math.max(a.top, b.top);
        float intersectionMaxX = Math.min(a.right, b.right);
        float intersectionMaxY = Math.min(a.bottom, b.bottom);

        // Calculate the area of the intersection rectangle
        float intersectionArea = Math.max(intersectionMaxY - intersectionMinY, 0) *
                Math.max(intersectionMaxX - intersectionMinX, 0);

        // Calculate the union of the two boxes and return the IoU
        return intersectionArea / (areaA + areaB - intersectionArea);
    }

    /**
     Converts model outputs to Non-Maximum Suppression (NMS) predictions. This is used by the anomaly detection
     model and annotates bounding boxes with the classname: "anomaly"
     @param outputs - Model outputs.
     @param imgScaleX - Scaling factor for X dimension of the input image.
     @param imgScaleY - Scaling factor for Y dimension of the input image.
     @param ivScaleX - Scaling factor for X dimension of the ImageView.
     @param ivScaleY - Scaling factor for Y dimension of the ImageView.
     @param startX - Starting X position of the ImageView.
     @param startY - Starting Y position of the ImageView.
     @return ArrayList of NMS predictions.
     **/
    public static ArrayList<Result> outputsToNMSPredictions(float[] outputs, float imgScaleX, float imgScaleY, float ivScaleX, float ivScaleY, float startX, float startY) {
        ArrayList<Result> results = new ArrayList<>();
        for (int i = 0; i< mOutputRow; i++) {
            // Check if the confidence score for the predicted box is greater than the threshold
            if (outputs[i* mOutputColumn +4] > mThreshold) {
                float x = outputs[i* mOutputColumn];
                float y = outputs[i* mOutputColumn +1];
                float w = outputs[i* mOutputColumn +2];
                float h = outputs[i* mOutputColumn +3];

                // Compute the bounding box coordinates in pixels
                float left = imgScaleX * (x - w/2);
                float top = imgScaleY * (y - h/2);
                float right = imgScaleX * (x + w/2);
                float bottom = imgScaleY * (y + h/2);

                // Increase the size of the bounding box by 10%
                float rectWidth = right - left;
                float rectHeight = bottom - top;
                float rectWidthIncrease = rectWidth * 0.1f;
                float rectHeightIncrease = rectHeight * 0.1f;
                left -= rectWidthIncrease / 2f;
                top -= rectHeightIncrease / 2f;
                right += rectWidthIncrease / 2f;
                bottom += rectHeightIncrease / 2f;

                // Get the predicted class for the box
                float max = outputs[i* mOutputColumn +5];
                int cls = 0;
                for (int j = 0; j < mOutputColumn -5; j++) {
                    if (outputs[i* mOutputColumn +5+j] > max) {
                        max = outputs[i* mOutputColumn +5+j];
                        cls = j;
                    }
                }

                // Convert the bounding box coordinates to pixels and create a new Result object
                Rect rect = new Rect((int)(startX+ivScaleX*left), (int)(startY+top*ivScaleY), (int)(startX+ivScaleX*right), (int)(startY+ivScaleY*bottom));
                Result result = new Result(cls, outputs[i*mOutputColumn+4], rect);
                results.add(result);
            }
        }
        return nonMaxSuppression(results, mNmsLimit, mThreshold);
    }
}
