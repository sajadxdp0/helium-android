plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val rustJniLibs = layout.buildDirectory.dir("rustJniLibs")

tasks.register<Exec>("buildRustCore") {
    val outputDirectory = rustJniLibs.get().asFile
    inputs.files(fileTree(rootProject.file("native")) {
        exclude("target/**")
    })
    environment(
        "CARGO_TARGET_DIR",
        layout.buildDirectory.dir("rustTarget").get().asFile.absolutePath,
    )
    workingDir(rootProject.file("native"))
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a",
        "-t", "x86_64",
        "-o", outputDirectory.absolutePath,
        "build", "--release",
        "--manifest-path", rootProject.file("native/Cargo.toml").absolutePath,
    )
}

tasks.named("preBuild").configure {
    dependsOn("buildRustCore")
}

android {
    sourceSets.getByName("main").jniLibs.srcDir(rustJniLibs.get().asFile)
    namespace = "computer.helium.mobile"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "computer.helium.mobile"
        minSdk = 28
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
    }

    // Release signing: credentials come from the environment so no secrets
    // are stored in the repository. See ~/.config/helium-android/signing.env.
    signingConfigs {
        create("release") {
            val keystore = System.getenv("HELIUM_KEYSTORE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("HELIUM_STORE_PASSWORD")
                keyAlias = System.getenv("HELIUM_KEY_ALIAS")
                keyPassword = System.getenv("HELIUM_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (System.getenv("HELIUM_KEYSTORE") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.mozilla.geckoview:geckoview:160.0.20261116130455")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
