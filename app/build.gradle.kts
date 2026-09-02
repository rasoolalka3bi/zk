plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.faceattend.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.faceattend.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    // منع ضغط ملف الموديل (.tflite) - ضغطه بيكسر تحميله وقت التشغيل
    androidResources {
        noCompress += "tflite"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX - عرض الكاميرا الحية وتحليل كل فريم
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit - كشف وجود الوجه وموقعه (وليس التمييز - ده دور TensorFlow Lite تحت)
    implementation("com.google.mlkit:face-detection:16.1.6")

    // TensorFlow Lite - تشغيل موديل استخراج "بصمة الوجه الرقمية" (Embedding)
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")

    // Gson - حفظ بيانات الموظفين وسجل الحضور في ملفات JSON محلية
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
}
