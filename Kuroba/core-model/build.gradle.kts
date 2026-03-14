plugins {
    id("KurobaLibraryPlugin")
}

android {
    namespace = "com.github.k1rakishou.chan.core.model"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":core-common"))
    implementation(project(":core-settings"))
    implementation(project(":core-parser"))
    implementation(project(":core-logger"))
    implementation(project(":core-spannable"))
    implementation(project(":core-themes"))

    implementation(libs.kotlin.stdlib)
    implementation(libs.appcompat)
    implementation(libs.core.ktx)
    implementation(libs.joda.time)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.jsoup)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.dagger)
    ksp(libs.dagger.compiler)

    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlin.coroutines.test)
}