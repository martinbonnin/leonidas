
import internal.GcpSettings
import internal.configureDeploy
import internal.configureTerraform
import org.gradle.api.Project

val projectName = "leonidas-415019"
val region = "europe-west9"
val serviceAccountJson = { System.getenv("GOOGLE_SERVICES_JSON") ?: error("GOOGLE_SERVICES_JSON env variable is needed to deploy") }

fun Project.configureDeploy(name: String, mainClass:String){
    this@configureDeploy.configureDeploy(
        imageName = name,
        mainClass = mainClass,
        gcpSettings = GcpSettings(
            projectName = projectName,
            region = region,
            serviceAccountJson = serviceAccountJson
        )
    )
}

fun Project.configureTerraform() {
    this@configureTerraform.configureTerraform(serviceAccountJson)
}