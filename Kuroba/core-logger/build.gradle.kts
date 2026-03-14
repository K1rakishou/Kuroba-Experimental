plugins {
    id("KurobaLibraryPlugin")
}

android {
    namespace = "com.github.k1rakishou.chan.logger"
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.compose.ui)
    implementation(libs.kotlin.stdlib)
    implementation(libs.joda.time)
    implementation(libs.kotlin.coroutines.core)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
}