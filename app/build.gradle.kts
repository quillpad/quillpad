plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kapt)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinParcelize)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigationSafeArgs)
    alias(libs.plugins.hiltAndroid)
}

android {
    compileSdk = 35
    buildToolsVersion = "30.0.3"
    namespace = "org.qosp.notes"

    defaultConfig {
        applicationId = "io.github.quillpad"
        minSdk = 24
        targetSdk = 35
        versionCode = project.getVersionCode()
        versionName = project.getVersionName()

        testInstrumentationRunner = "org.qosp.notes.TestRunner"

        // Enable per-app language preferences
        resourceConfigurations.addAll(listOf("ar", "ca", "cs", "de", "el", "en", "es", "fr", "it", "nb-rNO", "nl", "pl", "pt-rBR", "ru", "tr", "uk", "vi", "zh-rCN", "zh-rTW"))

        // export schema
        // https://stackoverflow.com/a/44645943/4594587
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    dependenciesInfo {
        // Disables dependency metadata when building APKs.
        includeInApk = false
        // Disables dependency metadata when building Android App Bundles.
        includeInBundle = false
    }

    if (project.hasProperty("keystore")) {
        signingConfigs {
            create("release") {
                val keystoreFileArg = project.property("keystore").toString()
                val storePassArg = project.property("storepass").toString()
                val keyName = project.property("keyalias").toString()
                val keypassArg = project.property("keypass").toString()
                storeFile = file(keystoreFileArg)
                storePassword = storePassArg
                keyAlias = keyName
                keyPassword = keypassArg
            }
        }
    }

    buildTypes {
        val testLabBuild = project.findProperty("TESTLAB_BUILD")?.toString() ?: "false"

        debug {
            buildConfigField("boolean", "TESTLAB_BUILD", testLabBuild)
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isCrunchPngs = false
            if (project.hasProperty("keystore")) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                debugSymbolLevel = "FULL"
            }
            buildConfigField("boolean", "TESTLAB_BUILD", testLabBuild)
        }
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_11.toString()
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
        compose = true
        buildConfig = true
    }

    kapt {
        javacOptions {
            option("-Adagger.fastInit=ENABLED")
            option("-Adagger.hilt.android.internal.disableAndroidSuperclassValidation=true")
        }
    }

    sourceSets {
        // Adds exported schema location as test app assets.
        getByName("androidTest").assets.srcDirs(files("$projectDir/schemas"))
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(libs.monitor)
    implementation(libs.junit.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    coreLibraryDesugaring(libs.coreLibraryDesugaring)

    implementation(libs.okhttp)
    // AndroidX
    implementation(libs.bundles.kotlin.androidX)

    implementation(libs.bundles.kotlin.deps)

    // Material Components
    implementation(libs.material)
    implementation(libs.androidx.material)
    // Optional - Integration with activities
    implementation(libs.androidx.activity.compose)
    // Optional - Integration with ViewModels
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Room
    ksp(libs.roomCompiler)
    implementation(libs.roomRuntime)
    implementation(libs.roomKtx)
    testImplementation(libs.roomTesting)
    androidTestImplementation(libs.roomTesting)

    // Lifecycle
    implementation(libs.bundles.kotlin.lifecycle)

    // Security
    implementation(libs.securityCrypto)

    // Flow Preferences
    implementation(libs.flowPreferences)

    // DataStore Preferences
    implementation(libs.datastorePreferences)
    implementation(libs.datastoreext)

    // Markwon
    implementation(libs.bundles.markwon)

    // Work Manager
    implementation(libs.workRuntimeKtx)
    androidTestImplementation(libs.workTesting)

    // Coil
    implementation(libs.coil)
    implementation(libs.coilVideo)
    implementation(libs.coilGif)

    // PhotoView
    implementation(libs.photoview)

    // Hilt
    implementation(libs.androidxHiltWork)
    implementation(libs.hiltAndroid)
    androidTestImplementation(libs.hiltAndroidTesting)
    kaptAndroidTest(libs.hiltAndroidCompiler)
    kapt(libs.hiltCompiler)
    kapt(libs.androidxHiltCompiler)

    // ExoPlayer
    implementation(libs.exoplayerCore)
    implementation(libs.exoplayerUi)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit2Convertor)

    // LeakCanary
    debugImplementation(libs.leakcanaryAndroid)
}
