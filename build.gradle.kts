repositories {
  mavenCentral()
  google()
}

plugins {
  alias(libs.plugins.kotlinGradlePlugin) apply false
  alias(libs.plugins.versionsGradlePlugin)
  alias(libs.plugins.versionCatalogUpdateGradlePlugin)
  alias(libs.plugins.kotlinBinaryCompatibilityPlugin) apply false
  id("org.jetbrains.dokka") version "1.9.10"
}

subprojects {
  apply(plugin = "org.jetbrains.dokka")
  
  tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
  }

  tasks.withType<Test> {
    useJUnitPlatform()
  }
}

tasks.register("publishToMavenCentral") {
  group = "publishing"
  dependsOn(
    ":lib:publishToMavenCentral"
  )
}
