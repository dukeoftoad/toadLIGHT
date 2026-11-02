pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Maven Central via repo1 (repo.maven.apache.org rate-limits CI sandboxes).
        maven("https://repo1.maven.org/maven2")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        maven("https://repo1.maven.org/maven2")
        maven("https://maven.aliyun.com/repository/public")
    }
}

rootProject.name = "toadLIGHT"
include(":app")
