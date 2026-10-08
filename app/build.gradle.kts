plugins {
    alias(libs.plugins.android.application)
}

val activeResourcesDir = layout.buildDirectory.dir("generated/res/active/main")
val prepareActiveResources by tasks.registering(Sync::class) {
    // Keep the app's referenced layouts; the remaining legacy layouts and animations lack their resource set.
    from("src/main/res") {
        exclude("layout/**", "anim/**")
    }
    from("src/main/res/layout") {
        include(
            "activity_main.xml",
            "activity_screen_grid.xml",
            "activity_wallpaper.xml",
            "item_app.xml",
            "popup_app_actions.xml",
            "screen_apps.xml",
            "screen_home.xml",
            "screen_home_second.xml"
        )
        into("layout")
    }
    into(activeResourcesDir)
}

android {
    namespace = "com.example.oslaucher"
    compileSdk {
        version = release(37)
    }

    sourceSets {
        getByName("main") {
            res.directories.clear()
            res.directories.add(activeResourcesDir.get().asFile.absolutePath)
        }
    }

    defaultConfig {
        applicationId = "com.example.oslaucher"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.named("preBuild").configure {
    dependsOn(prepareActiveResources)
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}