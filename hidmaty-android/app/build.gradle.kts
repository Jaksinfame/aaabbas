plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.hidmaty.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hidmaty.app"
        minSdk = 24          // يغطّي 99%+ من أجهزة أندرويد بالسوق السعودي
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        // ⚠️ عدِّل هذا الرابط لعنوان موقعك الفعلي المنشور (https إلزامي).
        buildConfigField("String", "BASE_URL", "\"https://hidmaty.example.com/\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            // ⚠️ عنوان شبكتك المحلية (خادم Vite بجهازك) — يشتغل فقط لما جوالك
            // على نفس شبكة الواي-فاي بالضبط. لو تغيّر عنوان جهازك لاحقاً
            // (شبكة مختلفة)، حدِّثه هنا وبملف
            // res/xml/network_security_config_debug.xml أيضاً (نفس الـIP بالاثنين).
            buildConfigField("String", "BASE_URL", "\"https://storewide-skinning-enjoyment.ngrok-free.dev/\"")
        }
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-ktx:1.9.2")
}
