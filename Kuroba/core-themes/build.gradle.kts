plugins {
    id("KurobaLibraryComposePlugin")
}

android {
    namespace = "com.github.k1rakishou.chan.core.theme"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-logger"))
    implementation(project(":core-settings"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.gson)
    implementation(libs.compose.material)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.fsaf)

    implementation(libs.dagger)
    ksp(libs.dagger.compiler)

    testImplementation(libs.junit)
}