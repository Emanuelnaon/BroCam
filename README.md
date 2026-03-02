# BroCam

BroCam is an Android application designed to facilitate remote camera operation. It allows one Android device to act as a camera (the "Lens" device) and another Android device to function as a remote control (the "Control" device). This enables users to capture photos or videos remotely, with potential applications in various scenarios where a detached camera view is beneficial.

## Features

*   **Dual Mode Operation:** Choose between "Camera (Lens)" mode or "Remote Control" mode upon launching the application.
*   **Camera Integration:** Utilizes CameraX library for robust camera functionalities on the Lens device.
*   **Inter-Device Communication:** Leverages Google Play Services Nearby Connections for seamless communication between the Lens and Control devices, enabling remote commands and data streaming.
*   **Modern Android UI:** Built with Jetpack Compose for a modern and declarative user interface.

## Technologies Used

*   **Kotlin:** Primary programming language.
*   **Jetpack Compose:** Modern Android UI toolkit for building native interfaces.
*   **CameraX:** Jetpack library for camera development, simplifying camera app creation.
*   **Google Play Services Nearby Connections:** API for peer-to-peer communication between nearby devices.
*   **Gradle:** Build automation tool for Android projects.

## Permissions

The BroCam application requires the following permissions to function correctly:

*   `android.permission.CAMERA`: Required for accessing the device's camera.
*   `android.permission.ACCESS_FINE_LOCATION`: Used for precise location access, potentially for Nearby Connections.
*   `android.permission.ACCESS_COARSE_LOCATION`: Used for approximate location access, potentially for Nearby Connections.
*   `android.permission.BLUETOOTH_ADVERTISE`: (Android 12+) Allows the app to advertise itself over Bluetooth.
*   `android.permission.BLUETOOTH_CONNECT`: (Android 12+) Allows the app to connect to paired Bluetooth devices.
*   `android.permission.BLUETOOTH_SCAN`: (Android 12+) Allows the app to discover and pair with Bluetooth devices.
*   `android.permission.NEARBY_WIFI_DEVICES`: (Android 13+) Allows the app to discover and connect to nearby Wi-Fi devices.
*   `android.permission.CHANGE_WIFI_STATE`: Allows the app to change Wi-Fi connectivity state.
*   `android.permission.ACCESS_WIFI_STATE`: Allows the app to access information about Wi-Fi networks.

## How to Build

To build the application, follow these steps:

1.  **Clone the repository** (if applicable).
2.  **Open the project in Android Studio**.
3.  **Sync Gradle files** if prompted.
4.  **Generate a debug APK** using the following Gradle command in your terminal or Android Studio's Gradle tab:

    ```bash
    ./gradlew :app:assembleDebug
    ```
    The generated APK will be located in `app/build/outputs/apk/debug/app-debug.apk`.

