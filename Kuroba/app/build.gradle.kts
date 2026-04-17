import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.github.k1rakishou.buildsrc.FreeCompilerArgs
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

val gitHashProvider = providers.exec {
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }

android {
    namespace = "com.github.k1rakishou.chan"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        applicationId = "com.github.k1rakishou.chan"

        buildConfigField("String", "RELEASE_UPDATE_API_ENDPOINT", "\"https://api.github.com/repos/K1rakishou/Kuroba-Experimental/releases/latest\"")
        buildConfigField("String", "BETA_UPDATE_API_ENDPOINT", "\"https://api.github.com/repos/K1rakishou/Kuroba-Experimental-beta/releases/latest\"")
        buildConfigField("String", "GITHUB_ENDPOINT", "\"https://github.com/K1rakishou/Kuroba-Experimental\"")
        buildConfigField("String", "GITHUB_REPORTS_ENDPOINT", "\"https://github.com/KurobaExReports/Reports/issues/\"")
        buildConfigField("String", "RELEASE_SIGNATURE", "\"86242978CF53C34361A8C962D0A57107AEB70E10631AE13EB5B006C0CF673FA9\"")
        buildConfigField("String", "DEBUG_SIGNATURE", "\"DC5195CC40E42B95267D500B6E93E46EC51028C67BDD3D09BBB9C208BF20C8FE\"")
        buildConfigField("String", "COMMIT_HASH", "\"${gitHashProvider.get()}\"")

        //            M -> Major version
        //            m -> Minor version
        //            p -> patch
        //            MmmPP
        versionCode = 10340
        versionName = "v1.3.40"

        configurations.configureEach {
            resolutionStrategy {
                force(libs.emoji2)
            }
            exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
        }

        vectorDrawables.useSupportLibrary = true
    }

    // signingConfigs must come before buildTypes
    signingConfigs {
        val releasePropsFile = file("release.properties")
        if (releasePropsFile.exists()) {
            val props = Properties().apply {
                load(FileInputStream(releasePropsFile))
            }
            create("release") {
                storeFile = file(props["keystoreFile"] as String)
                storePassword = props["keystorePass"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPass"] as String
                enableV1Signing = true
                enableV2Signing = true
            }
        }

        val debugPropsFile = file("debug.properties")
        if (debugPropsFile.exists()) {
            val props = Properties().apply {
                load(FileInputStream(debugPropsFile))
            }
            getByName("debug") {
                storeFile = file(props["keystoreFile"] as String)
                storePassword = props["keystorePass"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPass"] as String
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    flavorDimensions += "default"

    productFlavors {
        // FLAVOR_TYPE 0 - release (stable) build
        // FLAVOR_TYPE 1 - beta build
        // FLAVOR_TYPE 2 - dev build
        // FLAVOR_TYPE 3 - fdroid build

        create("stable") {
            dimension = "default"
            applicationIdSuffix = ""
            extra["apkVersionNameSuffix"] = ""
            buildConfigField("int", "FLAVOR_TYPE", "0")
            buildConfigField("int", "UPDATE_DELAY", "1")
            manifestPlaceholders["appName"] = "KurobaEx"
            manifestPlaceholders["iconLoc"] = "@mipmap/ic_launcher_release"
            manifestPlaceholders["fileProviderAuthority"] = "${defaultConfig.applicationId}.fileprovider"
            manifestPlaceholders["appTheme"] = "@style/Chan.DefaultTheme"
        }
        create("beta") {
            dimension = "default"
            applicationIdSuffix = ".beta"
            extra["apkVersionNameSuffix"] = "-beta"
            buildConfigField("int", "FLAVOR_TYPE", "1")
            buildConfigField("int", "UPDATE_DELAY", "1")
            manifestPlaceholders["appName"] = "KurobaEx-beta"
            manifestPlaceholders["iconLoc"] = "@mipmap/ic_launcher_beta"
            manifestPlaceholders["fileProviderAuthority"] = "${defaultConfig.applicationId}.beta.fileprovider"
            manifestPlaceholders["appTheme"] = "@style/Chan.DefaultTheme"
        }
        create("dev") {
            dimension = "default"
            applicationIdSuffix = ".dev"
            extra["apkVersionNameSuffix"] = "-dev"
            buildConfigField("int", "FLAVOR_TYPE", "2")
            buildConfigField("int", "UPDATE_DELAY", "99999999")
            manifestPlaceholders["appName"] = "KurobaEx-dev"
            manifestPlaceholders["iconLoc"] = "@mipmap/ic_launcher_dev"
            manifestPlaceholders["fileProviderAuthority"] = "${defaultConfig.applicationId}.dev.fileprovider"
            manifestPlaceholders["appTheme"] = "@style/Chan.DebugTheme"
        }
        create("fdroid") {
            dimension = "default"
            applicationIdSuffix = ".fdroid"
            extra["apkVersionNameSuffix"] = "-fdroid"
            buildConfigField("int", "FLAVOR_TYPE", "3")
            buildConfigField("int", "UPDATE_DELAY", "99999999")
            manifestPlaceholders["appName"] = "KurobaEx-fdroid"
            manifestPlaceholders["iconLoc"] = "@mipmap/ic_launcher_release"
            manifestPlaceholders["fileProviderAuthority"] = "${defaultConfig.applicationId}.fdroid.fileprovider"
            manifestPlaceholders["appTheme"] = "@style/Chan.DefaultTheme"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg")
            isDebuggable = false
            if (signingConfigs.names.contains("release")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            if (signingConfigs.names.contains("debug")) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    // APK rename
    applicationVariants.all {
        val variant = this
        variant.outputs
            .map { it as BaseVariantOutputImpl }
            .forEach { output ->
                val flavor = android.productFlavors.find { it.name == variant.flavorName }
                    ?: throw GradleException("Couldn't find flavor by variant.flavorName: '${variant.flavorName}'")

                val apkVersionNameSuffix = flavor.extra["apkVersionNameSuffix"] as String
                val abiFilter = output.getFilter("ABI")
                val baseName = "KurobaEx$apkVersionNameSuffix"

                output.outputFileName = if (abiFilter != null) {
                    "$baseName-$abiFilter.apk"
                } else {
                    "$baseName.apk"
                }
            }
    }

    compileOptions {
        encoding = "UTF-8"
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
            freeCompilerArgs.addAll(FreeCompilerArgs.args)
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmTarget.get().toInt()))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        error += setOf(
            "MissingPermission",
            "ProtectedPermissions",
            "InlinedApi",
            "NewApi",
            "HardcodedDebugMode",
            "PackageManagerGetSignatures",
            "UnsafeCryptoAlgorithm",
            "TrustAllX509TrustManager"
        )
        disable += setOf(
            "StopShip",
            "AppLinksAutoVerify",
            "InvalidPackage",
            "UnusedResources",
            "SetJavaScriptEnabled"
        )

        checkAllWarnings = true
        abortOnError = true
        checkReleaseBuilds = true
        checkDependencies = false
        ignoreTestSources = true
        checkGeneratedSources = false
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt.yml")
    baseline = file("$projectDir/config/detekt-baseline.xml")
}

dependencies {
    implementation(project(":core-logger"))
    implementation(project(":core-settings"))
    implementation(project(":core-themes"))
    implementation(project(":core-spannable"))
    implementation(project(":core-common"))
    implementation(project(":core-model"))
    implementation(project(":core-parser"))

    implementation(libs.appcompat)
    implementation(libs.androidx.preferences.ktx)
    implementation(libs.constraintlayout)
    implementation(libs.slidingpanelayout)
    implementation(libs.swiperefreshlayout)
    implementation(libs.core.ktx)
    implementation(libs.work.runtime.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.material)
    implementation(libs.androidx.window)
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation.graphics)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.exoplayer.core)
    implementation(libs.exoplayer.ui)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.jsoup)
    implementation(libs.gif.drawable)
    implementation(libs.subsampling.scale.image.view)
    implementation(libs.autolink)
    implementation(libs.gson)
    implementation(libs.rxjava)
    implementation(libs.rxandroid)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.collections.immutable)
    implementation(libs.joda.time)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.coroutines.rx2)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.voyager.core)
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.transitions)
    implementation(libs.fsaf)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.dagger)
    ksp(libs.dagger.compiler)

    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    implementation(libs.epoxy)
    ksp(libs.epoxy.processor)

    detektPlugins(libs.detekt.formatting)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.powermock.module.junit4)
    testImplementation(libs.powermock.api.mockito2)
    testImplementation(libs.kotlin.coroutines.test)
}