import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    namespace = "org.qosp.notes"

    defaultConfig {
        applicationId = "io.github.quillpad"
        minSdk = 24
        targetSdk = 35
        versionCode = 53
        versionName = "1.5.11"

        testInstrumentationRunner = "org.qosp.notes.TestRunner"

        // Enable per-app language preferences
        androidResources {
            localeFilters += listOf(
                "ar",
                "ca",
                "cs",
                "de",
                "el",
                "en",
                "es",
                "fr",
                "it",
                "nb-rNO",
                "nl",
                "pl",
                "pt-rBR",
                "ru",
                "tr",
                "uk",
                "vi",
                "zh-rCN",
                "zh-rTW"
            )

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

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            "-opt-in=kotlinx.coroutines.FlowPreview",
        )
    }
}

// export schema
// https://stackoverflow.com/a/44645943/4594587
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("KOIN_CONFIG_CHECK", "true")
    arg("KOIN_DEFAULT_MODULE", "true")
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
    coreLibraryDesugaring(libs.coreLibraryDesugaring)

    // Test
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk.android)
    testImplementation(libs.mockk.agent)
    testImplementation(libs.roomTesting)
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)
    androidTestImplementation(libs.roomTesting)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.mockk.agent)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.workTesting)

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

    // Koin
    implementation(project.dependencies.platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.core.coroutines)
    implementation(libs.koin.android)
    implementation(libs.koin.android.compat)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.androidx.navigation)

    // Yaml parsing
    implementation(libs.yamlkt)

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
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.retrofit)
    implementation(libs.retrofit2Convertor)

    // Software Quality
    debugImplementation(libs.leakcanaryAndroid)
    implementation(libs.bundles.acra)
}
