plugins {
  id("java-library")
  id("java-test-fixtures")
  id("org.jetbrains.kotlin.jvm")
  id("com.vanniktech.maven.publish") version "0.34.0"
  kotlin("plugin.serialization") version "1.9.0"
}

repositories {
  mavenCentral()
}

java {
  toolchain {
    languageVersion.set(JavaLanguageVersion.of(11))
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
  implementation(libs.kotlinxSerializationCore)
  implementation(libs.quiver)

  // Test fixtures dependencies (published for downstream testing)
  testFixturesImplementation(libs.arrow)
  testFixturesImplementation(libs.kotlinxSerializationCore)
  testFixturesImplementation(libs.quiver)

  // Test dependencies
  testImplementation(testFixtures(project(":lib")))
  testImplementation(libs.bundles.kotest)
  testImplementation(libs.kotlinxSerializationJson)
  testImplementation(libs.mockk)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
}
