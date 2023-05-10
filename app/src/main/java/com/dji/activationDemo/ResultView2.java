package com.dji.activationDemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;


public class ResultView2 extends View {

    private final static int TEXT_X = 40;
    private final static int TEXT_Y = 35;
    private final static int TEXT_WIDTH = 260;
    private final static int TEXT_HEIGHT = 50;

    private Paint mPaintRectangle;
    private Paint mPaintText;
    private ArrayList<Result> mResults;

    public ResultView2(Context context) {
        super(context);
    }

    public ResultView2(Context context, AttributeSet attrs){
        super(context, attrs);
        mPaintRectangle = new Paint();
        mPaintRectangle.setColor(Color.YELLOW);
        mPaintText = new Paint();
    }
    public Result highestConfidence;
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mResults == null) return;
        if (highestConfidence == null && !mResults.isEmpty()) {
            // Initialize highestConfidence to the first item in mResults
            highestConfidence = mResults.get(0);
        }
        for (Result result : mResults) {
            // Calculate the percentage of scaling (10%)
            float scalingFactor = 0.001f;
            float width = result.rect.width();
            float height = result.rect.height();
            float xAmount = width * scalingFactor;
            float yAmount = height * scalingFactor;

            // Update rectangle coordinates to scale up by 10%
            RectF scaledRectF = new RectF(result.rect.left - xAmount, result.rect.top - yAmount, result.rect.right + xAmount, result.rect.bottom + yAmount);

            mPaintRectangle.setStrokeWidth(5);
            mPaintRectangle.setStyle(Paint.Style.STROKE);
            canvas.drawRect(scaledRectF, mPaintRectangle);

            Path mPath = new Path();
            RectF mRectF = new RectF(scaledRectF.left, scaledRectF.top - TEXT_HEIGHT, scaledRectF.left + TEXT_WIDTH, scaledRectF.top);
            mPath.addRect(mRectF, Path.Direction.CW);
            mPaintText.setColor(Color.CYAN);
            canvas.drawPath(mPath, mPaintText);

            mPaintText.setColor(Color.BLACK);
            mPaintText.setStrokeWidth(0);
            mPaintText.setStyle(Paint.Style.FILL);
            mPaintText.setTextSize(32);
            canvas.drawText(String.format("%s %.2f", PrePostProcessor2.mClasses[result.classIndex], result.score), scaledRectF.left + TEXT_X, (float) (scaledRectF.top - (TEXT_Y * 0.5)), mPaintText);

            //keep track of the bounding box with the highest confidence
            if(highestConfidence != null){
                if(highestConfidence.score * highestConfidence.rect.width() * highestConfidence.rect.height() < result.score * result.rect.width() * result.rect.height())
                    highestConfidence = result;
            }
        }
    }

    public void setResults(ArrayList<Result> results) {
        mResults = results;
    }
}