buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:3.4.1")
        classpath(kotlin("gradle-plugin", "1.3.31"))
        classpath("android.arch.navigation:navigation-safe-args-gradle-plugin:1.0.0")
    }
}

allprojects {
    repositories {
        jcenter()
        google()
        maven("https://dl.bintray.com/dhpcs/maven")
    }
}

task<Delete>("clean") {
    delete(rootProject.buildDir)
}
