plugins {
    id("KurobaLibraryPlugin")
}

android {
    namespace = "com.github.k1rakishou.chan.core.spannable"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-settings"))
    implementation(project(":core-themes"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.gson)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}