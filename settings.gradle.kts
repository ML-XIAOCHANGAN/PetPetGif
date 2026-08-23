pluginManagement {
    repositories {
        // 阿里云镜像（国内网络优先，避免 SSL/直连问题）
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/central")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // 阿里云镜像（国内网络优先，避免 SSL/直连问题）
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/central")
        google()
        // 官方 Maven Central 兜底（阿里云未同步的库如 kibotu 从这里拉）
        mavenCentral()
    }
}

rootProject.name = "PetPetGif"
include(":app")
