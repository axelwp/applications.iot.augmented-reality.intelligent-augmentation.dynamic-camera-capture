package com.dji.activationDemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;

// A custom view class to draw bounding boxes and labels on an image. This ResultView is used to draw the bounding
//boxes returned by the weld detection model for both the live feed and still image detection.
public class ResultView extends View {

    // Constants for text position and dimensions
    private final static int TEXT_X = 40;
    private final static int TEXT_Y = 35;
    private final static int TEXT_WIDTH = 260;
    private final static int TEXT_HEIGHT = 50;

    // Paint objects for rectangle and text
    private Paint mPaintRectangle;
    private Paint mPaintText;


    private ArrayList<Result> mResults;     // ArrayList to hold the detection results

    public ResultView(Context context) {
        super(context);
    }

    public ResultView(Context context, AttributeSet attrs){
        super(context, attrs);
        // Initialize the rectangle paint object to yellow color
        mPaintRectangle = new Paint();
        mPaintRectangle.setColor(Color.YELLOW);
        // Initialize the text paint object
        mPaintText = new Paint();
    }

    // A variable to keep track of the bounding box with the highest confidence
    public Result highestConfidence;
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Check if there are any detection results to draw
        if (mResults == null) return;

        // Initialize the highestConfidence to the first item in mResults if it is not set already
        if (highestConfidence == null && !mResults.isEmpty()) {
            highestConfidence = mResults.get(0);
        }

        // Loop through all the detection results and draw the bounding box and label
        for (Result result : mResults) {
            // Calculate the percentage of scaling (0.01%)
            // Scaling done here only affects the drawn rectangle, not the actual rectangle stored in ResultView
            // Therefore scaling was moved to within PrePostProcessor and this was reduced to a negligible 0.01%
            float scalingFactor = 0.0001f;
            float width = result.rect.width();
            float height = result.rect.height();
            float xAmount = width * scalingFactor;
            float yAmount = height * scalingFactor;

            // Update rectangle coordinates to scale
            RectF scaledRectF = new RectF(result.rect.left - xAmount, result.rect.top - yAmount, result.rect.right + xAmount, result.rect.bottom + yAmount);

            // Set the properties of the rectangle paint object
            mPaintRectangle.setStrokeWidth(5);
            mPaintRectangle.setStyle(Paint.Style.STROKE);

            // Draw the rectangle on the canvas
            canvas.drawRect(scaledRectF, mPaintRectangle);

            // Create a path for the text background rectangle
            Path mPath = new Path();
            RectF mRectF = new RectF(scaledRectF.left, scaledRectF.top - TEXT_HEIGHT, scaledRectF.left + TEXT_WIDTH, scaledRectF.top);
            mPath.addRect(mRectF, Path.Direction.CW);

            // Set the color of the text paint object to cyan
            mPaintText.setColor(Color.CYAN);

            // Draw the background rectangle for the label
            canvas.drawPath(mPath, mPaintText);

            // Set the color, size, and style of the text paint object
            mPaintText.setColor(Color.BLACK);
            mPaintText.setStrokeWidth(0);
            mPaintText.setStyle(Paint.Style.FILL);
            mPaintText.setTextSize(32);

            // Draw the label on the canvas
            canvas.drawText(String.format("%s %.2f", PrePostProcessor.mClasses[result.classIndex], result.score), scaledRectF.left + TEXT_X, (float) (scaledRectF.top - (TEXT_Y * 0.5)), mPaintText);

            // Update the highestConfidence variable if necessary
            if(highestConfidence != null){
                if(highestConfidence.score * highestConfidence.rect.width() * highestConfidence.rect.height() < result.score * result.rect.width() * result.rect.height())
                    highestConfidence = result;
            }
        }
    }

    //Setter function to provide the ArrayList of Results to be drawn on this ResultView
    public void setResults(ArrayList<Result> results) {
        mResults = results;
    }
}