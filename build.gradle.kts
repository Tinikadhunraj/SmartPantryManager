// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}

val tmpBuildDir = file("C:/tmp/SmartPantryManagerBuild")
allprojects {
    layout.buildDirectory.set(tmpBuildDir.resolve(project.name))
}