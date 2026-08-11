plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// NewPipe 0.26.x uses URLDecoder.decode(String, Charset), an overload absent on
// older Android runtimes. Package its original JAR without Utils.class; the app
// supplies the API-24-compatible equivalent below.
val newPipeOriginal by configurations.creating {
    isTransitive = false
}
val patchedNewPipeExtractor by tasks.registering(Jar::class) {
    archiveBaseName.set("newpipe-extractor-android-compatible")
    from({ zipTree(newPipeOriginal.singleFile) })
    exclude("org/schabi/newpipe/extractor/utils/Utils.class")
}

android {
    namespace = "com.ntech.nkara"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.ntech.nkara"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlin {
        jvmToolchain(17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    newPipeOriginal(libs.newpipe.extractor)
    implementation(files(patchedNewPipeExtractor))
    implementation("com.github.TeamNewPipe:nanojson:e9d656ddb49a412a5a0a5d5ef20ca7ef09549996")
    implementation("org.jsoup:jsoup:1.22.2")
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    implementation("com.google.protobuf:protobuf-javalite:4.35.1")
    implementation("org.mozilla:rhino-engine:1.8.1")
    implementation(libs.coil.compose)
    implementation(libs.okhttp)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
