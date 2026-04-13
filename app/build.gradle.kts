import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

android {
    namespace = "com.example.mediavault"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.mediavault"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        renderscriptTargetApi = 19
        renderscriptSupportModeEnabled = true

        buildConfigField("String", "GOOGLE_BOOKS_API_KEY", "\"${localProperties.getProperty("GOOGLE_BOOKS_API_KEY") ?: ""}\"")
        buildConfigField("String", "TMDB_API_KEY", "\"${localProperties.getProperty("TMDB_API_KEY") ?: ""}\"")
        buildConfigField("String", "OMDB_API_KEY", "\"${localProperties.getProperty("OMDB_API_KEY") ?: ""}\"")
        buildConfigField("String", "SUPABASE_URL", "\"${localProperties.getProperty("SUPABASE_URL") ?: ""}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties.getProperty("SUPABASE_ANON_KEY") ?: ""}\"")
        buildConfigField("boolean", "SUPABASE_ENABLED", "${localProperties.getProperty("SUPABASE_ENABLED") ?: "false"}")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.lifecycle.livedata)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.work.runtime)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)

    // QR generation/scanning (offline)
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    implementation(libs.blurview)
    
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)
    implementation(libs.jsoup)
}
