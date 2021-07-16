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
import org.jetbrains.kotlin.mppconverter.gradle.generator.*
import org.jetbrains.kotlin.mppconverter.gradle.parser.BuildScriptParser
import org.jetbrains.kotlin.mppconverter.gradle.parser.GroovyBuildScriptParser
import org.jetbrains.kotlin.mppconverter.gradle.parser.KtsBuildScriptParser
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class GradleProjectHelper(projectRoot: String) {

    private val projectRoot = File(projectRoot)

    private var connector: GradleConnector? = null
    private var connection: ProjectConnection? = null

    private lateinit var buildScriptParser: BuildScriptParser
    private lateinit var buildScriptGenerator: MultiplatformProjectBuildScriptGenerator

    init {
        if (!this.projectRoot.isDirectory) throw IllegalArgumentException("projectRoot $projectRoot is not directory!")
    }

    fun connectToProject(usingProject: Project) {
        connector = GradleConnector.newConnector().apply {
            forProjectDirectory(projectRoot)
            connection = connect()
        }

        when (buildScriptFileType) {
            BuildScriptFileType.KotlinScript -> {
                buildScriptParser = KtsBuildScriptParser(usingProject, buildScriptFile.absolutePath)
                buildScriptGenerator = KtsMultiplatformProjectBuildScriptGenerator()
            }
            BuildScriptFileType.GroovyScript -> {
                buildScriptParser = GroovyBuildScriptParser(usingProject, buildScriptFile.absolutePath)
                // TODO buildScriptGenerator = GroovyMultiplatformProjectBuildScriptGenerator()
            }
        }
    }

    fun runGetDependenciesSynchronously(configuration: String = "implementation"): List<Dependency> = runBlocking {
        getDependenciesSynchronously(configuration)
    }

    suspend fun getDependenciesSynchronously(configuration: String = "implementation"): List<Dependency> = suspendCoroutine { cont ->
        loadDependencies(configuration) { cont.resume(it) }
    }

    fun loadDependencies(configuration: String = "implementation", onDependenciesLoaded: (List<Dependency>) -> Unit) {
        val build = connection?.newBuild()
            ?: throw IllegalStateException("Illegal state! #connectToProject must be called before #loadDependensies.")

        val swappedOutputStream = ByteArrayOutputStream()
        build.setStandardOutput(swappedOutputStream)

        build.withArguments("dependencies", "--configuration", configuration)
        build.run(object : ResultHandler<Void> {
            override fun onComplete(p0: Void?) {
                val lines = swappedOutputStream.toString("UTF-8").lines()
                onDependenciesLoaded(rawDependenciesToList(lines, configuration))
            }

            override fun onFailure(e: GradleConnectionException?) {
                println("failure on get dependencies for project: $e")
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

        val dependencies = runGetDependenciesSynchronously()
        buildScriptGenerator.setDependenciesToSourceSet("commonMain", dependencies)
        buildScriptGenerator.setDependenciesToSourceSet("jvmMain", dependencies)
        buildScriptGenerator.setDependenciesToSourceSet("jsMain", dependencies)

        return buildScriptGenerator.generate()
    }

    val buildScriptFile: File by lazy {
        connection?.getModel(GradleProject::class.java)?.buildScript?.sourceFile
            ?: throw IllegalStateException("Illegal state! #connectToProject must be called before calling buildScriptFile.")
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
        connection?.getModel(GradleProject::class.java)?.name
            ?: throw IllegalStateException("Illegal state! #connectToProject must be called before calling projectName.")
    }

    val isSingleModule: Boolean by lazy {
        connection?.getModel(GradleProject::class.java)?.children?.isEmpty()
            ?: throw IllegalStateException("Illegal state! #connectToProject must be called before calling isOneModule.")
    }

    fun closeConnection() {
        connection?.close() ?: throw IllegalStateException("Illegal state! #connectToProject must be called before #closeConnection.")
    }

}

const val multiplatformPluginVersion = "1.5.10"

fun multiplatformPlugin(buildScript: GradleProjectHelper.BuildScriptFileType): String = when (buildScript) {
    GradleProjectHelper.BuildScriptFileType.KotlinScript -> "kotlin(\"multiplatform\") version \"$multiplatformPluginVersion\""
    GradleProjectHelper.BuildScriptFileType.GroovyScript -> "id 'org.jetbrains.kotlin.multiplatform' version '$multiplatformPluginVersion'"
}