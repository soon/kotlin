package org.jetbrains.kotlin.gradle.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.ConfigurationContainer
import java.net.URL
import org.gradle.api.logging.Logging
import java.util.Properties
import java.io.FileNotFoundException
import org.gradle.api.initialization.dsl.ScriptHandler
import org.gradle.api.invocation.Gradle
import org.gradle.api.logging.Logger
import java.lang.reflect.Method
import org.jetbrains.kotlin.gradle.tasks.KotlinTasksProvider
import kotlin.text.Regex

abstract class KotlinBasePluginWrapper: Plugin<Project> {
    val log = Logging.getLogger(this.javaClass)

    public override fun apply(project: Project) {

        val sourceBuildScript = findSourceBuildScript(project)
        if (sourceBuildScript == null) {
            log.error("Failed to determine source cofiguration of kotlin plugin. Can not download core. Please verify that this or any parent project " +
                    "contains 'kotlin-gradle-plugin' in buildscript's classpath configuration.")
            return
        }

        val kotlinPluginVersion = loadKotlinVersionFromResource(log)
        project.getExtensions().getExtraProperties()?.set("kotlin.gradle.plugin.version", kotlinPluginVersion)

        val gradleVersion = project.getGradle().getGradleVersion()

        val pluginClassLoader = createPluginIsolatedClassLoader(gradleVersion, kotlinPluginVersion, sourceBuildScript)
//        val pluginClassLoader = ClassLoader.getSystemClassLoader()
        val plugin = getPlugin(pluginClassLoader, sourceBuildScript)
        plugin.apply(project)

        //project.getGradle().addBuildListener(FinishBuildListener(pluginClassLoader))
    }

    protected abstract fun getPlugin(pluginClassLoader: ClassLoader, scriptHandler: ScriptHandler): Plugin<Project>

    private fun createPluginIsolatedClassLoader(gradleVersion: String, projectVersion: String, sourceBuildScript: ScriptHandler): ParentLastURLClassLoader {
        val dependencyHandler: DependencyHandler = sourceBuildScript.getDependencies()
        val configurationsContainer: ConfigurationContainer = sourceBuildScript.getConfigurations()

        log.kotlinDebug("Creating configuration and dependency; gradle version $gradleVersion, project version $projectVersion")
        // \todo make version comparison more reliable
        // it seems though that the gradle utilities for that are not suitable (https://discuss.gradle.org/t/is-there-a-way-to-programmatically-ensure-a-minimum-gradle-version-using-non-internal-gradle-apis/5890/1)
        //   so some guessing is needed anyway
        val versionRegex = Regex("^(\\d+)\\.(\\d+).*$")
        val match = versionRegex.match(gradleVersion)
        val gradleVersionInt =
            try {
                if (match == null || match.groups.size() != 3) 0
                else match.groups.get(1)!!.value.toInt() * 1000 + match.groups.get(2)!!.value.toInt()
            }
            catch (e: Exception) { 0 }
        if (gradleVersionInt <= 0)
            throw Exception("Cannot parse gradle version $gradleVersion, ${match?.groups?.map { it?.value }?.joinToString(",")}")
        val kotlinPluginCoreCoordinates =
                (when {
                    gradleVersionInt < 1009 -> "org.jetbrains.kotlin:kotlin-gradle-1.6-plugin-tasks:"
                    gradleVersionInt < 2001 -> "org.jetbrains.kotlin:kotlin-gradle-1.12-plugin-tasks:"
                    else -> "org.jetbrains.kotlin:kotlin-gradle-2.2-plugin-tasks:"
                }) +
                projectVersion
        val dependency = dependencyHandler.create(kotlinPluginCoreCoordinates)
        val configuration = configurationsContainer.detachedConfiguration(dependency)
2
        log.kotlinDebug("Resolving [" + kotlinPluginCoreCoordinates + "]")
        val kotlinPluginDependencies: List<URL> = configuration.getResolvedConfiguration().getFiles({ true })!!.map { it.toURI().toURL() }
        log.kotlinDebug("Resolved files: [" + kotlinPluginDependencies.toString() + "]")
        log.kotlinDebug("Load plugin in parent-last URL classloader")
        val kotlinPluginClassloader = ParentLastURLClassLoader(kotlinPluginDependencies, this.javaClass.getClassLoader())
        log.kotlinDebug("Class loader created")

        return kotlinPluginClassloader
    }

    private fun findSourceBuildScript(project: Project): ScriptHandler? {
        log.kotlinDebug("Looking for proper script handler")
        var curProject = project
        while (curProject != curProject.getParent()) {
            log.kotlinDebug("Looking in project $project")
            val scriptHandler = curProject.getBuildscript()
            val found = scriptHandler.getConfigurations().findByName("classpath")?.firstOrNull { it.name.contains("kotlin-gradle-plugin") } != null
            if (found) {
                log.kotlinDebug("Found! returning...")
                return scriptHandler
            }
            log.kotlinDebug("not found, switching to parent")
            curProject = curProject.getParent()!!
        }
        return null
    }
}

open class KotlinPluginWrapper: KotlinBasePluginWrapper() {
    override fun getPlugin(pluginClassLoader: ClassLoader, scriptHandler: ScriptHandler) = KotlinPlugin(scriptHandler, KotlinTasksProvider(pluginClassLoader))
}

open class KotlinAndroidPluginWrapper : KotlinBasePluginWrapper() {
    override fun getPlugin(pluginClassLoader: ClassLoader, scriptHandler: ScriptHandler) = KotlinAndroidPlugin(scriptHandler, KotlinTasksProvider(pluginClassLoader))
}

open class Kotlin2JsPluginWrapper : KotlinBasePluginWrapper() {
    override fun getPlugin(pluginClassLoader: ClassLoader, scriptHandler: ScriptHandler) = Kotlin2JsPlugin(scriptHandler, KotlinTasksProvider(pluginClassLoader))
}

fun Logger.kotlinDebug(message: String) {
    this.debug("[KOTLIN] $message")
}

