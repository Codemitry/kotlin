/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.gradle.tooling.model.GradleProject
import java.io.ByteArrayOutputStream
import java.io.File

class GradleProjectHelper(projectRoot: String) {

    private val projectRoot = File(projectRoot)

    private var connector: GradleConnector? = null
    private var connection: ProjectConnection? = null

    init {
        if (!this.projectRoot.isDirectory) throw IllegalArgumentException("projectRoot $projectRoot is not directory!")
    }

    fun connectToProject() {
        connector = GradleConnector.newConnector().apply {
            forProjectDirectory(projectRoot)
            connection = connect()
        }

    }

    fun loadDependencies(configuration: String = "implementation", onDependenciesLoaded: (List<String>) -> Unit) {
        val build = connection?.newBuild() ?: throw IllegalStateException("Illegal state! #connectToProject must be called before #loadDependensies.")

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