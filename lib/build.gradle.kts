plugins {
  id("java-library")
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish") version "0.34.0"
}

repositories {
  mavenCentral()
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
  }
  withSourcesJar()
}

mavenPublishing {
  configure(com.vanniktech.maven.publish.KotlinJvm())
}

dependencies {
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(libs.arrow)
  implementation(libs.jooq)
  implementation(libs.kfsm)
  implementation(libs.kotlinxSerializationCore)
  implementation(libs.quiver)

  // Test dependencies
  testImplementation(libs.bundles.kotest)
  testImplementation(libs.mockk)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
}
