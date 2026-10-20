plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
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
            "activity_page_transition.xml",
            "activity_appearance.xml",
            "activity_general.xml",
            "activity_app_effects.xml",
            "activity_change_app_icon.xml",
            "activity_wallpaper.xml",
            "screen_loading.xml",
            "item_app.xml",
            "popup_app_actions.xml",
            "screen_apps.xml",
            "screen_app_library.xml",
            "item_app_library.xml",
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
        version = release(36)
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
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