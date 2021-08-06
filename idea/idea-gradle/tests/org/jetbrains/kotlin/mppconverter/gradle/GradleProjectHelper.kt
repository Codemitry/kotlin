/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.idea.IdeaProject
import org.jetbrains.kotlin.mppconverter.gradle.generator.*
import org.jetbrains.kotlin.mppconverter.gradle.parser.BuildScriptParser
import org.jetbrains.kotlin.mppconverter.gradle.parser.GroovyBuildScriptParser
import org.jetbrains.kotlin.mppconverter.gradle.parser.KtsBuildScriptParser
import org.jetbrains.kotlin.util.collectionUtils.concat
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class GradleProjectHelper(projectRoot: String) {

    private val projectRoot = File(projectRoot)

    private var connector: GradleConnector? = null
    private var connection: ProjectConnection? = null

    private lateinit var buildScriptParser: BuildScriptParser
    private lateinit var buildScriptGenerator: MultiplatformProjectBuildScriptGenerator

    private var gradleProjectModel: GradleProject? = null
    private var ideaProjectModel: IdeaProject? = null

    init {
        if (!this.projectRoot.isDirectory) throw IllegalArgumentException("projectRoot $projectRoot is not directory!")
    }

    fun connectToProject(usingProject: Project) {
        connector = GradleConnector.newConnector().apply {
            forProjectDirectory(projectRoot)
            connection = connect()
        }

        ideaProjectModel = connection?.getModel(IdeaProject::class.java)
        gradleProjectModel = connection?.getModel(GradleProject::class.java)

        when (buildScriptFileType) {
            BuildScriptFileType.KotlinScript -> {
                buildScriptParser = KtsBuildScriptParser(usingProject, buildScriptFile.absolutePath)
                buildScriptGenerator = KtsMultiplatformProjectBuildScriptGenerator()
            }
            BuildScriptFileType.GroovyScript -> {
                buildScriptParser = GroovyBuildScriptParser(usingProject, buildScriptFile.absolutePath)
                buildScriptGenerator = GroovyMultiplatformProjectBuildScriptGenerator()
            }
        }
    }

    fun runGetDependenciesSynchronously(configuration: String = "implementation"): List<Dependency> =
        runBlocking {
            getDependenciesSynchronously(configuration)
        }

    suspend fun getDependenciesSynchronously(configuration: String = "implementation"): List<Dependency> = suspendCoroutine { cont ->
        loadDependencies(configuration, onSuccess = { cont.resume(it) }, onFail = { cont.resumeWithException(it) })
    }

    fun loadDependencies(
        configuration: String = "implementation",
        onSuccess: (List<Dependency>) -> Unit,
        onFail: (t: Throwable) -> Unit
    ) {
        val build = connection?.newBuild()
            ?: throw IllegalStateException("Illegal state! #connectToProject must be called before #loadDependensies.")

        val swappedOutputStream = ByteArrayOutputStream()
        build.setStandardOutput(swappedOutputStream)

        build.withArguments("dependencies", "--configuration", configuration)
        build.run(object : ResultHandler<Void> {
            override fun onComplete(p0: Void?) {
                val lines = swappedOutputStream.toString("UTF-8").lines()
                onSuccess(rawDependenciesToList(lines, configuration))
            }

            override fun onFailure(e: GradleConnectionException?) {
                System.err.println("failure on get dependencies for project: $e")
                onFail(e ?: Exception("Unknown error"))
            }
        })
    }


    fun getBuildScriptFileNameForThisProject(): String = when (buildScriptFileType) {
        BuildScriptFileType.KotlinScript -> "build.gradle.kts"
        BuildScriptFileType.GroovyScript -> "build.gradle"
    }


    fun getMultiplatformBuildScriptTextForThisProject(): String {
        buildScriptParser.getRepositoriesSectionInside()?.let { buildScriptGenerator.setRepositories(it) }
        buildScriptGenerator.setTargets(
            JvmTarget(),
            JsTarget()
        )
        buildScriptGenerator.setPlugins(multiplatformPlugin(buildScriptFileType))

        val dependencies = runGetDependenciesSynchronously("implementation")
            .concat(runGetDependenciesSynchronously("compileOnly"))
            .concat(runGetDependenciesSynchronously("runtimeOnly"))
            .concat(runGetDependenciesSynchronously("api"))!!
            .toList()

        buildScriptGenerator.setDependenciesToSourceSet("commonMain", dependencies)
        buildScriptGenerator.setDependenciesToSourceSet("jvmMain", dependencies)
        buildScriptGenerator.setDependenciesToSourceSet("jsMain", dependencies)

        return buildScriptGenerator.generate()
    }

    val buildScriptFile: File by lazy {
        gradleProjectModel?.buildScript?.sourceFile
            ?: throw IllegalStateException("Illegal state! #connectToProject must be called before calling buildScriptFile.")
    }

    val settingsGradleFile: File? by lazy {
        val settingsGroovy = File(this.projectRoot, "settings.gradle")
        val settingsKts = File(this.projectRoot, "settings.gradle.kts")
        when {
            settingsGroovy.isFile -> settingsGroovy
            settingsKts.isFile -> settingsKts
            else -> null
        }
    }

    sealed class BuildScriptFileType {

        object KotlinScript : BuildScriptFileType()
        object GroovyScript : BuildScriptFileType()

        fun isKotlinScript() = this is KotlinScript
        fun isGroovyScript() = this is GroovyScript
    }

    val buildScriptFileType by lazy {
        when (buildScriptFile.extension) {
            "kts" -> BuildScriptFileType.KotlinScript
            "gradle" -> BuildScriptFileType.GroovyScript
            else -> throw IllegalStateException("Unknown type of build gradle script: $buildScriptFile")
        }
    }

    val projectName: String by lazy {
        gradleProjectModel?.name
            ?: throw IllegalStateException("Illegal state! #connectToProject must be called before calling projectName.")
    }

    val isPrimitiveSingleModuleProject: Boolean by lazy {
        val modules = ideaProjectModel?.modules
            ?: throw IllegalStateException("Illegal state! #connectToProject must be called before calling isOneModule.")

        modules.size == 1 && modules.getAt(0).contentRoots.getAt(0).rootDirectory == this.projectRoot
    }

    fun closeConnection() {
        gradleProjectModel = null
        connection?.close() ?: throw IllegalStateException("Illegal state! #connectToProject must be called before #closeConnection.")
    }

}

const val multiplatformPluginVersion = "1.5.10"

fun multiplatformPlugin(buildScript: GradleProjectHelper.BuildScriptFileType): String = when (buildScript) {
    GradleProjectHelper.BuildScriptFileType.KotlinScript -> "kotlin(\"multiplatform\") version \"$multiplatformPluginVersion\""
    GradleProjectHelper.BuildScriptFileType.GroovyScript -> "id 'org.jetbrains.kotlin.multiplatform' version '$multiplatformPluginVersion'"
}