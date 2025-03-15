

---

# Pothole Detection & Road Management App

**The Road Management System** is a real-time pothole detection system powered by YOLOv8. This app uses YOLOv8 to detect potholes. A website is also included to track detected potholes. Additionally, a Jupyter notebook is provided for training the model on custom data.


---

## Features

- **Real-time Pothole and Road Issue Monitoring**: Using YOLOv8 for  detecting potholes.
- **Location-based Notifications**: The app alerts users about nearby potholes or other road issues using speech and visual markers.
- **OSMDroid Map Integration**: Displays detected issues on an interactive map.
- **Text-to-Speech (TTS)**: Provides audio feedback to notify users about detected road issues nearby.
- **Firebase Integration**: Stores detected pothole data in Firebase for real-time updates and tracking.
- **Swipe Hint Animation**: A smooth user interface with animations to enhance the user experience.

---

## Screenshots

*Add screenshots here if you have any visuals for the app.*

---



### Steps

1. **Clone the Repository:**

   Clone this project to your local machine:
   ```bash
   git clone https://github.com/lerapela/Android_PotholeDetectorApp.git
   ```

2. **Setup Firebase:**

   - Go to [Firebase Console](https://console.firebase.google.com/).
   - Create a new project or use an existing one.
   - Setup Firebase Realtime Database and add your configuration in `google-services.json`.
   
   **Important:** To get started with Firebase, download the `google-services.json` file and place it in your `app/` directory.


3. **Run the App:**

   - Open the project in Android Studio.
   - Build and run the app on an emulator or physical device.

---

## How It Works

- **YOLOv8 Model for Image Recognition**: The app uses the YOLOv8 model for real-time detection of potholes on the road.
- **Pothole and Road Issue Detection**: The app captures images of road problems using the device’s camera, processes them with YOLOv8, and transmits the data (latitude, longitude, address, status) to Firebase.
- **Location-Based Alerts**: When the user is near a detected road issue (e.g., pothole), the app provides audio and visual feedback to notify the user.
- **Interactive Map**: OSMDroid is used to display the road issues on the map, along with markers indicating detected problems.

---

## Credits

This project was inspired by the work of  (GitHub: (https://github.com/surendramaran/YOLO)).

I cloned and modified the original project to meet my needs for real-time pothole detection and road issue reporting.

- **YOLOv8**: Used for image recognition to detect road problems. [YOLO GitHub](https://github.com/ultralytics/yolov8)
- **OSMDroid**: Used for mapping and location features. [OSMDroid GitHub](https://github.com/osmdroid/osmdroid)
- **Firebase Realtime Database**: For storing detected pothole and road issue data. [Firebase](https://firebase.google.com/)
- **Text-to-Speech (TTS)**: Used to provide speech feedback for detected issues.
- **Google Location Services**: For obtaining real-time location data.

---



---

### Important Notes:

- **Uploading `google-services.json`**: Please make sure to upload the `google-services.json` file to the root of your `app/` directory for Firebase integration to work properly. This file contains the necessary configurations for your app to connect to Firebase services.
### Additional Resources
-**Jupyter Notebook for Model Training:** A Jupyter notebook is provided to help you train the YOLOv8 model on custom data for pothole detection. Check the training/ directory for more details on how to fine-tune 
   the model for your specific use case.

-**Web-based Platform:** A web-based platform is available for government authorities to monitor detected road issues, track their status, and manage resolutions.


---
