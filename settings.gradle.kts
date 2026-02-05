pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

rootProject.name = "domain-api"

include(":lib")
include(":lib-kfsm")
include(":lib-kfsm-v2")
