plugins {
  `kotlin-dsl`
}

repositories {
  google()
  mavenCentral()
}

dependencies {
  implementation("com.android.tools.build:gradle:8.13.2")
  implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.0")
  implementation("com.google.devtools.ksp:symbol-processing-gradle-plugin:2.3.4")
}