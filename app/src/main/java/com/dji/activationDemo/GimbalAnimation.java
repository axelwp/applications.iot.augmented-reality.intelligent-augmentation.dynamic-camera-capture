package com.dji.activationDemo;

import android.util.Log;

import dji.common.gimbal.CapabilityKey;
import dji.common.gimbal.Rotation;
import dji.common.gimbal.RotationMode;
import dji.common.handheld.RecordAndShutterButtons;
import dji.common.handheld.ZoomState;
import dji.common.handheldcontroller.ControllerMode;
import dji.common.util.DJIParamCapability;
import dji.liveviewar.jni.Vector3;
import dji.sdk.base.BaseProduct;
import dji.sdk.gimbal.Gimbal;
import dji.sdk.handheldcontroller.HandheldController;
import dji.sdk.products.Aircraft;
import dji.sdk.products.HandHeld;
import dji.sdk.sdkmanager.DJISDKManager;

import com.dji.activationDemo.fragments.CameraFragment;

public class GimbalAnimation {

    /** Interfaces with the camera fragment. Must be called before photos are taken */
    public void Construct(CameraFragment camerafragment) {
        Camera = camerafragment;
        if (DemoApplication.getProductInstance() != null) {
            remoteController = ((HandHeld) DemoApplication.getProductInstance()).getHandHeldController(); // Get product instance
            remoteController.setHardwareStateCallback(rcHardwareState -> { // Setup listener for change in product's hardware state
                // If the photo button is pressed and an animation isn't currently being preformed
                if (rcHardwareState.getRecordAndShutterButtons() == RecordAndShutterButtons.SHUTTER_CLICK && count == NumPhotos + 1) {
                    updateAnimation();
                    try { // Take the first step in the animation
                        Step();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Camera.takePhotos(); // Begin taking photos
                }
                // Stop animation if button is held and if there is an animation happening
                if (rcHardwareState.getRecordAndShutterButtons() == RecordAndShutterButtons.SHUTTER_LONG_CLICK && count != NumPhotos + 1) {
                    count = 1;
                }
                if (rcHardwareState.getZoomState().toString().equals("ZOOM_OUT")) {
                    Log.d(TAG, "ZoomIn");
                    Camera.addZoom(0.1f);
                }
                if (rcHardwareState.getZoomState().toString().equals("ZOOM_IN")) {
                    Log.d(TAG, "ZoomOut");
                    Camera.addZoom(-0.1f);
                }
            });
        }
        else {
            Log.e(TAG, "Gimbal Connection Fail");
        }
    }

    private static final String TAG = GimbalAnimation.class.getName();

    private Gimbal gimbal = null;
    private CameraFragment Camera = null;
    private HandheldController remoteController;
    private final Rotation.Builder builder = new Rotation.Builder();
    private Vector3 Pos = new Vector3(0f, 0f, 0f);      // The current position of the gimbal
    private Vector3 SavePos = new Vector3(0f, 0f, 0f);  // Save a position. Used for when the animation ends so that the gimbal can return

    private int NumPhotos = 10; // Total number of photos to take
    private int rows = 3;
    private int columns = 10;

    private float dp = 10.0f;       // Δposition, how wide apart should each step be?
    private float dv = 15.0f;       // Δvertical, same as dp, but for vertical movement
    private float speed = 60.0f;    // The speed the gimbal will rotate in degrees per second
    private float aStep = 0;        // Animation step
    private float horAngle = 45.0f; // The initial horizontal angle to rotate to
    private float verAngle = 15.0f; // The initial vertical angle to rotate to
    private float iVer = 1;     // Determines if the vertical angle should be inverted
    private float iHor = 1;     // Determines if the horizontal angle should be inverted
    private float flip = 1.0f; // Inverts the horizontal movement as necessary
    private int pause = 500;    // Number of milliseconds to wait between photos if gimbal isn't connected
    private float buff = 0.2f;  // Number of extra seconds to wait between photos if gimbal is connected
    private int count = NumPhotos + 1; // Countdown to last step

    // Animation Function. Returns a vector that tells the gimbal how to move based on the function
    private Vector3 aFunction(float x) { // Animation function. Returns a num for one dimension of the gimbal movement
        Vector3 Result = new Vector3(0f, 0f, 0f);
        if (x == 0) { // Initial movement
            Result = new Vector3(iHor*horAngle, iVer*verAngle, 0f);
            SavePos.x = Pos.x;
            SavePos.y = Pos.y;
            // z axis is locked. Unlock DJI roll axis before uncommenting or else app can crash
            //SavePos.z = Pos.z;
        }
        else if (x % columns == 0) { // Subsequent movements
            Result = new Vector3(
                    0f,
                    -iVer*dv,
                    0f);
            flip = flip * -1;
        }
        else {
            Result = new Vector3(
                    -iHor*flip*dp,
                    0f,
                    0f);
        }
        return Result;
    }

    public Boolean Step() throws InterruptedException { // Do a single step in the animation. Returns false when done.
        count = count - 1; // Decrement step counter
        Camera.setPhotoCount( Math.abs(count - NumPhotos) + 1, NumPhotos); // Update photo count display
        if (count == 0) { // If last step
            aStep = 0; // Reset steps
            count = NumPhotos + 1; // Reset count
            flip = 1; // Reset flip
            Camera.setPhotoCount(0, NumPhotos);
            resetGimbal(); // Send gimbal back to 0 degrees for yaw and pitch
            return false; } // Return that all steps are done
        else {
            if (gimbal != null) {
                sendRotateGimbalCommand(gimbal, aFunction(aStep), RotationMode.RELATIVE_ANGLE); // Apply angle and move gimbal
            }
            else {
                Thread.sleep(pause); // Gimbal isn't connected, wait between shots
            }

            aStep = aStep + 1;
            return true; // Steps are not yet done
        }
    }

    private void updateState() { // Gets the gimbal's current angle values in degrees
        DemoApplication.getProductInstance().getGimbal().setStateCallback(gimbalState -> {
            Pos.x = gimbalState.getAttitudeInDegrees().getYaw();
            Pos.y = gimbalState.getAttitudeInDegrees().getPitch();
            Pos.z = gimbalState.getAttitudeInDegrees().getRoll();
        });
    }

    private void resetGimbal() throws InterruptedException { // Send Gimbal back to inital state
        sendRotateGimbalCommand(gimbal, SavePos, RotationMode.ABSOLUTE_ANGLE);
    }

    private void sendRotateGimbalCommand(Gimbal gimbal, Vector3 Target, RotationMode Mode) throws InterruptedException {
        if (gimbal == null) { // Make sure the gimbal is connected
            return;
        }
        builder.mode(Mode);
        builder.yaw(Target.x);
        builder.pitch(Target.y);
        //builder_a.roll(Target.z); Roll is locked. Unlock roll before uncommenting otherwise app will crash
        float Time;
        if (Mode == RotationMode.ABSOLUTE_ANGLE) { // If absolute angle base vector A on current position
            Time = Distance(Pos, Target) / speed; // Calculate the time it will take to move at the currently defined speed (in seconds)
        }
        else { // If relative angle base vector A on 0
            Time = Distance(new Vector3(0f, 0f, 0f), Target) / speed;
        }
        if (Time < 0.2f) {Time = 0.2f;} // Doesn't work well with very small distances, add if statement to compensate
        builder.time(Time); // Put that time into the builder
        gimbal.rotate(builder.build(), null); // Send rotation command
        Time = Time + buff; // Add buffer time, makes the gimbal work more smoothly
        Thread.sleep((long) (Time * 1000)); // Wait until gimbal is done rotating
    }

    private float Distance(Vector3 A, Vector3 B) { // Gives the distance in degrees between a source (A) and destination (B) vector
        // Center vector
        float x = (B.x - A.x);
        float y = (B.y - A.y);
        //int z = (int) (B.z - A.z); Roll is locked, no need to calculate (make sure to put z*z into magnitude if uncommented

        // Return approximate magnitude
        return (float) Math.sqrt(x*x + y*y);
    }

    /** pulls numbers from main activity and applies them to the animation settings. Best called before first animation step */
    public void updateAnimation() {
        horAngle = MainActivity.Companion.getHorAngle();
        verAngle = MainActivity.Companion.getVerAngle();
        rows = MainActivity.Companion.getRows();
        columns = MainActivity.Companion.getColumns();
        buff = MainActivity.Companion.getBuff();
        // If the gimbal is connected, take num of photos == grid, otherwise follow user input
        if (gimbal != null) {
            NumPhotos = rows * columns;
            Log.d(TAG, "updateAnimation: " + NumPhotos);
            dp = (horAngle * 2 + 10) / columns; // Calculate the distance between horizontal shots
            dv = (verAngle * 2 + 10) / rows; // Calculate the distance between vertical shots
        } else {
            NumPhotos = MainActivity.Companion.getImgNum();
            pause = (int) (1 / MainActivity.Companion.getIps() * 1000);
        }

        // Setup inversion
        if (!MainActivity.Companion.getHorInvert()) {
            iHor = 1;
        } else {
            iHor = -1;
        }
        if (!MainActivity.Companion.getVerInvert()) {
            iVer = 1;
        } else {
            iVer = -1;
        }

        count = NumPhotos + 1; // Reset count
        aStep = 0; // Reset steps
        Camera.setPhotoCount(0, NumPhotos); // Update photo count display

    }

    /** Initializes gimbal movement. Best when called after construct */
    public void initGimbal() {
        //DJISDKManager.getInstance().startConnectionToProduct();
        if (DemoApplication.getProductInstance() != null) {
            Log.d(TAG, "Initializing Gimbal");
            if (DJISDKManager.getInstance() != null) {
                BaseProduct product = DJISDKManager.getInstance().getProduct();
                Log.d(TAG, "Product: " + product);
                if (product != null) {
                    if (product instanceof Aircraft) {
                        gimbal = ((Aircraft) product).getGimbals().get(0);
                    } else {
                        gimbal = product.getGimbal();
                    }
                    Log.d(TAG, "Initialized");
                    Object key = CapabilityKey.ADJUST_YAW;
                    DJIParamCapability capability = gimbal.getCapabilities().get(key);
                }

                updateState();
            }
        }
    }
}
