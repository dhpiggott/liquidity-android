import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.google.protobuf.gradle.*
import org.jetbrains.kotlin.config.KotlinCompilerVersion

plugins {
    id("com.github.ben-manes.versions") version "0.21.0"
    id("com.android.application")
    id("com.google.protobuf") version "0.8.8"
    id("kotlin-android")
    id("kotlin-android-extensions")
    id("androidx.navigation.safeargs.kotlin")
    id("com.github.triplet.play") version "2.2.1"
}

tasks.named<DependencyUpdatesTask>("dependencyUpdates") {
  resolutionStrategy {
    componentSelection {
      all {
        val rejected = listOf("alpha", "beta", "rc", "cr", "m", "preview", "b", "ea").any { qualifier ->
          candidate.version.matches(Regex("(?i).*[.-]$qualifier[.\\d-+]*"))
        }
        if (rejected) {
          reject("Release candidate")
        }
      }
    }
  }
}

android {
    compileSdkVersion(28)
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    defaultConfig {
        applicationId = "com.dhpcs.liquidity"
        minSdkVersion(21)
        targetSdkVersion(28)
        versionCode = 39
        versionName = "39"
        resConfigs("en")
    }
    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "android"
            keyAlias = "androidreleasekey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
        lintOptions {
            disable("InvalidPackage")
        }
    }
}

play {
    defaultToAppBundles = true
    serviceAccountCredentials = file("service-account-credentials.json")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.7.1"
    }
    plugins {
        id("java") {
            artifact = "com.google.protobuf:protoc:3.7.1"
        }
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:1.21.0"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                id("java")
                id("grpc")
            }
        }
    }
}

dependencies {
    protobuf("com.dhpcs:liquidity-ws-protocol:3")
    implementation(kotlin("stdlib-jdk7", KotlinCompilerVersion.VERSION))
    implementation("io.grpc:grpc-protobuf:1.21.0")
    implementation("io.grpc:grpc-stub:1.21.0")
    implementation("io.grpc:grpc-okhttp:1.21.0")
    implementation("javax.annotation:javax.annotation-api:1.3.2")
    implementation("com.madgag.spongycastle:pkix:1.54.0.0")
    implementation("com.nimbusds:nimbus-jose-jwt:7.2.1")
    implementation("io.reactivex.rxjava2:rxjava:2.2.8")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")
    implementation("net.danlew:android.joda:2.10.1.2")
    implementation("android.arch.navigation:navigation-fragment-ktx:1.0.0")
    implementation("android.arch.navigation:navigation-ui-ktx:1.0.0")
    implementation("androidx.lifecycle:lifecycle-extensions:2.0.0")
    implementation("androidx.lifecycle:lifecycle-common-java8:2.0.0")
    implementation("androidx.lifecycle:lifecycle-reactivestreams-ktx:2.0.0")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.appcompat:appcompat:1.0.2")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.recyclerview:recyclerview:1.0.0")
    implementation("androidx.preference:preference:1.0.0")
    implementation("com.google.android.material:material:1.0.0")
    implementation("com.sothree.slidinguppanel:library:3.4.0")
    implementation("net.glxn.qrgen:android:2.0")
    implementation("com.journeyapps:zxing-android-embedded:3.6.0")
    implementation("de.psdev.licensesdialog:licensesdialog:2.0.0")
}
