pluginManagement {
  listOf(repositories, dependencyResolutionManagement.repositories).forEach {
    it.apply {
      mavenCentral()
      google()
    }
  }
}

include(":terraform", ":server")
includeBuild("build-logic")
includeBuild("../apollo-kotlin")