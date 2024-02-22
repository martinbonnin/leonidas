package internal

import org.gradle.api.Project
import org.gradle.api.tasks.Exec

fun Project.configureTerraform(gcpServiceAccountJson: () -> String) {
    val file = layout.buildDirectory.file("service-account.json").get().asFile

    val createGcpCredentials = tasks.register("createGcpCredentials") {
        it.doLast {
            file.parentFile.mkdirs()
            file.writeText(gcpServiceAccountJson())
        }
    }
    tasks.register("init", Exec::class.java) {
        it.dependsOn(createGcpCredentials)
        it.environment("GOOGLE_APPLICATION_CREDENTIALS", file.absolutePath)
        it.commandLine("terraform", "init")
    }

    tasks.register("apply", Exec::class.java) {
        it.environment("GOOGLE_APPLICATION_CREDENTIALS", file.absolutePath)
        it.commandLine("terraform", "apply", "-auto-approve")
    }

    tasks.register("plan", Exec::class.java) {
        it.environment("GOOGLE_APPLICATION_CREDENTIALS", file.absolutePath)
        it.commandLine("terraform", "plan")
    }
}

