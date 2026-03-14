plugins {
    id("KurobaLibraryPlugin")
}

android {
    namespace = "com.github.k1rakishou.chan.core.common"
}

dependencies {
    implementation(project(":core-logger"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.appcompat)
    implementation(libs.androidx.preferences.ktx)
    implementation(libs.okhttp)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.joda.time)

    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    implementation(libs.jsr305)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.coroutines.test)
    testImplementation(libs.robolectric)
}