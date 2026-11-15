# Scalo for Android

Open this directory in Android Studio with JDK 17 and Android SDK 35. To build the APK and run the pitch tests on Windows, run:

    .\gradlew.bat :app:assembleDebug :app:testDebugUnitTest

On macOS or Linux, run:

    ./gradlew :app:assembleDebug :app:testDebugUnitTest

The debug APK is generated in app/build/outputs/apk/debug/. Version 2.1.0 includes the Sa–Re–Ga launch animation, microphone capture, and the bundled offline studio in standalone/web/.
