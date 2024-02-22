package internal

import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.run.v2.Service
import com.google.cloud.run.v2.ServiceName
import com.google.cloud.run.v2.ServicesClient
import com.google.cloud.run.v2.ServicesSettings
import com.google.cloud.tools.jib.api.Containerizer
import com.google.cloud.tools.jib.api.DockerDaemonImage
import com.google.cloud.tools.jib.api.Jib
import com.google.cloud.tools.jib.api.RegistryImage
import com.google.cloud.tools.jib.api.buildplan.AbsoluteUnixPath
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import java.io.ByteArrayInputStream
import java.util.*
import kotlin.io.path.name


class GcpSettings(
    val serviceAccountJson: () -> String?,
    val region: String,
    /**
     * Pass null to have Jib generate a local image
     */
    val projectName: String?
)

/**
 * @param imageName name used for the image. Also used to name the cloud run service
 */
internal fun Project.configureDeploy(imageName: String, mainClass: String, gcpSettings: GcpSettings) {
    val deployImageToGcp = registerBuildImageTask(imageName, mainClass, "deployImageToGcp", gcpSettings)
    registerBuildImageTask(imageName, mainClass, "deployImageToDockerDaemon", gcpSettings)

    tasks.register("bumpCloudRunRevision", BumpCloudRunRevision::class.java) {
        it.serviceName.set(imageName)
        it.gcpRegion.set(gcpSettings.region)
        it.gcpProjectName.set(gcpSettings.projectName)
        it.gcpServiceAccountJson.set(provider { gcpSettings.serviceAccountJson() })

        it.dependsOn(deployImageToGcp)
    }
}

private fun Project.registerBuildImageTask(
    imageName: String,
    mainClass: String,
    taskName: String,
    gcpSettings: GcpSettings
): TaskProvider<BuildImageTask> {
    val isMpp = extensions.getByName("kotlin") is KotlinMultiplatformExtension
    val jarTask = if (isMpp) "jvmJar" else "jar"
    val runtimeConfiguration = if (isMpp) "jvmRuntimeClasspath" else "runtimeClasspath"

    return tasks.register(taskName, BuildImageTask::class.java) {
        it.jarFile.fileProvider(tasks.named(jarTask).map { it.outputs.files.singleFile })
        it.runtimeClasspath.from(configurations.getByName(runtimeConfiguration))
        it.mainClass.set(mainClass)
        it.imageName.set(imageName)
        it.gcpRegion.set(gcpSettings.region)
        it.gcpProjectName.set(gcpSettings.projectName)
        it.gcpServiceAccountJson.set(provider { gcpSettings.serviceAccountJson() })
    }
}

abstract class BuildImageTask : DefaultTask() {

    @get:InputFiles
    abstract val runtimeClasspath: ConfigurableFileCollection

    @get:InputFile
    abstract val jarFile: RegularFileProperty

    @get:Input
    abstract val mainClass: Property<String>

    @get:Input
    abstract val imageName: Property<String>

    @get:Input
    abstract val gcpRegion: Property<String>

    @get:Input
    abstract val gcpProjectName: Property<String>

    @get:Input
    @get:org.gradle.api.tasks.Optional
    abstract val gcpServiceAccountJson: Property<String>

    @TaskAction
    fun taskAction() {
        val path = jarFile.get().asFile.toPath()
        var imageRef: String
        val containerizer = if (gcpServiceAccountJson.isPresent) {
            val repo = "${imageName.get()}-images"
            imageRef = "${gcpRegion.get()}-docker.pkg.dev/${gcpProjectName.get()}/$repo/${imageName.get()}"
            Containerizer.to(RegistryImage.named(imageRef).addCredential("_json_key", gcpServiceAccountJson.get()))
        } else {
            imageRef = "confetti.${imageName.get()}:latest"
            Containerizer.to(DockerDaemonImage.named(imageRef))
        }

        Jib.from("openjdk:17-alpine")
            .addLayer(listOf(path), AbsoluteUnixPath.get("/"))
            .addLayer(runtimeClasspath.files.map { it.toPath() }, AbsoluteUnixPath.get("/classpath"))
            .setEntrypoint(
                "java",
                "-cp",
                (runtimeClasspath.files.map { "classpath/${it.name}" } + path.name).joinToString(":"),
                mainClass.get())
            .containerize(containerizer)

        logger.lifecycle("Image deployed to '$imageRef'")
    }
}

abstract class BumpCloudRunRevision : DefaultTask() {
    @get:Input
    abstract val serviceName: Property<String>

    @get:Input
    abstract val gcpRegion: Property<String>

    @get:Input
    abstract val gcpProjectName: Property<String>

    @get:Input
    abstract val gcpServiceAccountJson: Property<String>

    @TaskAction
    fun taskAction() {
        val serviceName = serviceName.get()
        val servicesClient = ServicesClient.create(
            ServicesSettings.newBuilder()
                .setCredentialsProvider(
                    GoogleCredentials.fromStream(
                        ByteArrayInputStream(gcpServiceAccountJson.get().encodeToByteArray())
                    ).let {
                        FixedCredentialsProvider.create(it)
                    }
                )
                .build())

        val fullName = ServiceName.of(gcpProjectName.get(), gcpRegion.get(), serviceName).toString()
        val existingService = servicesClient.getService(fullName)
        val newService = Service.newBuilder()
            .setName(fullName)
            .setTemplate(
                existingService.template
                    .toBuilder()
                    .setRevision("$serviceName-${revision()}")
                    .build()
            )
            .build()

        servicesClient.updateServiceAsync(newService).get()

        servicesClient.close()
    }
}

/**
 * We need to force something new or else no new revision is created
 */
private fun revision(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.UTC)
    return String.format(
        Locale.ROOT,
        "%4d-%02d-%02d-%02d%02d%02d",
        now.year,
        now.monthNumber,
        now.dayOfMonth,
        now.hour,
        now.minute,
        now.second
    )
}