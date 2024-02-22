plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.google.devtools.ksp")
    id("com.apollographql.apollo3")
}

configureDeploy("leonidas", "leonidas.MainKt")

dependencies {
    implementation(platform(libs.http4k.bom.get()))
    implementation(libs.http4k.core)
    implementation(libs.http4k.server.jetty)
    implementation(libs.slf4j.get().toString()) {
        because("jetty uses SL4F")
    }

    implementation(libs.kotlinx.coroutines.core)
    implementation("com.apollographql.apollo3:apollo-execution-incubating")
    ksp(apollo.apolloKspProcessor(file("src/main/resources/schema.graphqls"), "leonidas", "leonidas"))
}

tasks.register("run", JavaExec::class.java) {
    classpath(configurations.getByName("runtimeClasspath"))
    classpath(tasks.named("jar"))

    mainClass.set("leonidas.MainKt")
}
