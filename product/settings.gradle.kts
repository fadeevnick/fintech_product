pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "mini-fintech-platform"

include(
    ":apps:platform",
    ":apps:acquirer",
    ":apps:network",
    ":apps:issuer",
    ":apps:vault",
    ":backend:libs:contracts-public",
    ":backend:libs:contracts-internal",
    ":backend:libs:contracts-events",
    ":backend:libs:db",
    ":backend:libs:observability",
    ":backend:libs:service-auth",
)
