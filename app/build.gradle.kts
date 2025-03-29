plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlinAndroid)
    alias(libs.plugins.kotlinParcelize)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.navigationSafeArgs)
}

android {
    compileSdk = 35
    buildToolsVersion = "30.0.3"
    namespace = "org.qosp.notes"

    defaultConfig {
        applicationId = "io.github.quillpad"
        minSdk = 24
        targetSdk = 35
        versionCode = 32
        versionName = "1.4.25"

        testInstrumentationRunner = "org.qosp.notes.TestRunner"

        // export schema
        // https://stackoverflow.com/a/44645943/4594587
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
            arg("KOIN_CONFIG_CHECK", "true")
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
        }
    }

    flavorDimensions += "versions"
    productFlavors {
        create("googleFlavor") {
            dimension = "versions"
            buildConfigField("boolean", "IS_GOOGLE", "true")
        }
        create("defaultFlavor") {
            dimension = "versions"
            buildConfigField("boolean", "IS_GOOGLE", "false")
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
        buildConfig = true
        viewBinding = true
        compose = true
    }

    packaging {
        resources.excludes.addAll(
            listOf(
                "META-INF/LICENSE.md",
                "META-INF/LICENSE-notice.md",
            )
        )
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
    testImplementation(libs.mockk.android)
    testImplementation(libs.mockk.agent)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.mockk.agent)
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

    // Koin
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp.compiler)
    implementation(libs.koin.core)
    implementation(libs.koin.core.coroutines)
    implementation(libs.koin.android)
    implementation(libs.koin.android.compat)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.androidx.navigation)
    implementation(libs.koin.androidx.startup)

    // Coil
    implementation(libs.coil)
    implementation(libs.coilVideo)
    implementation(libs.coilGif)

    // PhotoView
    implementation(libs.photoview)

    // ExoPlayer
    implementation(libs.exoplayerCore)
    implementation(libs.exoplayerUi)

    // Retrofit
    implementation(libs.retrofit)
    implementation(libs.retrofit2Convertor)

    // LeakCanary
    debugImplementation(libs.leakcanaryAndroid)
}
