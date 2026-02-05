plugins {
  id("java-library")
  alias(libs.plugins.kotlinGradlePlugin)
  alias(libs.plugins.mavenPublish)
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

val domainApiVersion: String by project

dependencies {
  // Use project dependency for local development, but published POM will use domainApiVersion
  api(project(":lib"))
  constraints {
    api("xyz.block.domainapi:domain-api:$domainApiVersion")
  }

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation(libs.arrow)
  implementation(libs.kfsm2)
  implementation(libs.quiver)

  // Test dependencies
  testImplementation(libs.bundles.kotest)
  testImplementation(libs.mockk)
  testImplementation(libs.junitApi)
  testImplementation(libs.junitEngine)
}

// Replace project dependency with published artifact in POM
publishing {
  publications {
    withType<MavenPublication> {
      pom.withXml {
        val deps = asNode().get("dependencies") as? groovy.util.NodeList
        deps?.forEach { depNode ->
          val dep = depNode as groovy.util.Node
          val artifactId = (dep.get("artifactId") as groovy.util.NodeList).firstOrNull() as? groovy.util.Node
          if (artifactId?.text() == "lib") {
            artifactId.setValue("domain-api")
            val version = (dep.get("version") as groovy.util.NodeList).firstOrNull() as? groovy.util.Node
            version?.setValue(domainApiVersion)
          }
        }
      }
    }
  }
}
