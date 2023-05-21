# applications.iot.augmented-reality.intelligent-augmentation.dynamic-camera-capture
# Weld Inspection Android Application

Welcome to our Weld Inspection Android Application. This application provides a platform for detecting and analyzing welds using Yolov5s models.

## Architecture Diagram

![image](https://github.com/axelwp/applications.iot.augmented-reality.intelligent-augmentation.dynamic-camera-capture/assets/59634858/e331522e-bbdd-4de0-8bc4-d859184475ef)


## Installation

1. Acquire Android Device (We use an Android Galaxy S21)
2. Download Android Studio (ver. 13 or higher)
3. Clone repository to your local device
4. Open repository through Android Studio (build.gradle contains all neccessary dependant packages for building, sync may be required)
5. Connect device via bluetooth or USB
6. Build project to the target device, which will subsequently install it to the device

## Usage

1. Login: The login feature is currently bypassed due to server connection issues.

2. Options:
   - **Scan**: Allows the user to select a weld from the list and scan it using the camera. The detected images can be viewed in the gallery.
   - **Gallery**: Displays the photos taken by the user. Users can upload, delete, or detect welds from the photos.
   - **Settings**: Provides options for configuring the camera, scan settings, and gimbal settings (if a gimbal is available).
   - **Help**: Redirects the user to the web service for more information about the project.

3. Scan: The scan option presents the user with a list of welds and a search bar to filter by location. A test weld is available for users to select due to server connection issues. After selecting a weld, users can scan it using the camera. Once the burst is over, the user can view the last taken photo by clicking on the thumbnail at the bottom right of the screen.

4. Gallery: The gallery option displays the photos taken by the user. Users can swipe to view other photos or click on the detect button to run the Yolov5s model for detecting welds. If a weld is detected, the image is cropped to remove background noise and then fed into another Yolov5s model to detect weld anomalies. The detected image is displayed in the top left of the screen.

5. Settings: Users can adjust the number of images taken per scan, the number of shots per second, the resolution of the photo, toggle auto-upload (currently unavailable), and toggle image correction. Gimbal settings are also available (if a gimbal is available) to adjust grid rows and columns, adjust horizontal and vertical angles, change the pause buffer, and invert horizontal and vertical movement.

6. Help: This option redirects the user to the web service for more information about the project. However, it is currently unavailable due to budget cuts with our project partner, and temporary changes have been made to the login, upload, and weld selection to allow the app to function locally without the need for server connection.

## Testing

We are unable to provide a strict testing measurement due to lack of provision of a physical weld by our project partner at this time. We will acquire one in the coming weeks for our live demonstration at OSU's engineering expo. Ideally, we would have an array of welds with known defects and be able to present individual testing results on specific anomaly detections individually.

## Deployment

We are currently unable to deploy this project as we are under instruction not to by our project partner.

## Demonstration

This video shows how to build the application and use it.

https://drive.google.com/file/d/1zyKjHKngDVBaYb2qDgwwgbAZlDZcf-F9/view?usp=share_link

## Roadmap

In no particular order:

1. Reconnect to backend when available for connection to webpage.

2. Perform 3D reconstruction of welds.

3. Translate anomaly detection to work in 3D.

4. Continue work on improving models.

5. Fix any underlying edge cases not currently found.

## Conclusion

This application provides a platform for detecting and analyzing welds using YOLOv5s models. We hope you find it useful. If you have any feedback or suggestions, please feel free to contact us.

## Credits

Android Demo Application Utilizing Pytorch For Image Detection
https://github.com/pytorch/android-demo-app/blob/master/ObjectDetection

