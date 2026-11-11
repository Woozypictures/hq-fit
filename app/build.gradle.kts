plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The signing key comes from GitHub Actions secrets (HQ_KEYSTORE_B64, HQ_KEYSTORE_PASS), never from the repo.
// It is the same key as HQ, so the two apps can trust each other's signature.
val ksPath: String? = System.getenv("HQ_KEYSTORE_PATH")
val ksPass: String = System.getenv("HQ_KEYSTORE_PASS") ?: ""

android {
    namespace = "uk.woozy.fit"
    compileSdk = 35
    defaultConfig {
        applicationId = "uk.woozy.fit"
        minSdk = 28
        targetSdk = 35
        versionCode = (System.getenv("VERSION_CODE") ?: "1").toInt()
        versionName = System.getenv("VERSION_NAME") ?: "0.1"
    }
    signingConfigs {
        create("release") {
            storeFile = ksPath?.let { file(it) }
            storePassword = ksPass
            keyAlias = "hq"
            keyPassword = ksPass
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (ksPath != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
        textReport = true
        textOutput = file("build/lint-report.txt")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(platform(libs.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.camera.core)
    implementation(libs.camera.camera2)
    implementation(libs.camera.lifecycle)
    implementation(libs.camera.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.health.connect)
}
