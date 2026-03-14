plugins {
    id("KurobaLibraryPlugin")
}

android {
    namespace = "com.github.k1rakishou.chan.core.parser"
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-logger"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.jsoup)
    implementation(libs.joda.time)

    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.powermock.module.junit4)
    testImplementation(libs.powermock.api.mockito2)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
}