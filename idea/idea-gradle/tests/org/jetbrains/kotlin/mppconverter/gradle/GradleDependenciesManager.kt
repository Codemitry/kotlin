/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

import kotlinx.coroutines.runBlocking
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.ResultHandler
import org.jetbrains.kotlin.mppconverter.gradle.generator.Dependency
import org.jetbrains.kotlin.mppconverter.gradle.generator.ModuleDependency
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class GradleDependenciesManager(gradleProjectPath: String) {
    val gradleProjectPath: File

    private var connection: ProjectConnection? = null

    init {
        this.gradleProjectPath = File(gradleProjectPath)
        if (!this.gradleProjectPath.isDirectory) throw IllegalArgumentException("$gradleProjectPath must be a directory with gradle project!")
    }

    /**
     * Must be called before get dependencies
     */
    private fun connect(
    ) {
        connection = GradleConnector.newConnector().apply {
            forProjectDirectory(this@GradleDependenciesManager.gradleProjectPath)
        }.connect()
    }


    fun getDependenciesSynchronously(configuration: String = "implementation"): List<Dependency> =
        runBlocking {
            getDependencies(configuration)
        }

    suspend fun getDependencies(configuration: String = "implementation"): List<Dependency> = suspendCoroutine { cont ->
        loadDependencies(configuration, onSuccess = { cont.resume(it) }, onFail = { cont.resumeWithException(it) })
    }

    fun loadDependencies(
        configuration: String = "implementation",
        onSuccess: (List<Dependency>) -> Unit,
        onFail: (t: Throwable) -> Unit
    ) {
        connect()

        val build = connection?.newBuild()
            ?: throw IllegalStateException("Illegal state! #connect must be called before #loadDependensies.")

        val swappedOutputStream = ByteArrayOutputStream()
        build.setStandardOutput(swappedOutputStream)

        build.withArguments("-q", "dependencies", "--configuration", configuration)
        build.run(object : ResultHandler<Void> {
            override fun onComplete(p0: Void?) {
                val lines = swappedOutputStream.toString("UTF-8").lines()
                val dependencies = rawDependenciesToList(lines, configuration).toMutableList()
                dependencies.withIndex().forEach {
                    if (it.value is ModuleDependency) {
                        dependencies[it.index] = ModuleDependency(
                            refineModulePathSynchronously((it.value as ModuleDependency).moduleNotation),
                            it.value.configuration
                        )
                    }
                }
                onSuccess(dependencies)
            }

            override fun onFailure(e: GradleConnectionException?) {
                System.err.println("failure on get dependencies for project: $e")
                onFail(e ?: Exception("Unknown error"))
            }
        })
        closeConnection()
    }

    fun refineModulePathSynchronously(moduleName: String) = runBlocking {
        refineModulePath(moduleName)
    }

    suspend fun refineModulePath(moduleName: String): String = suspendCoroutine { cont ->
        refineModulePathAsync(moduleName, onSuccess = { cont.resume(it) }, onFail = { cont.resumeWithException(it) })
    }

    fun refineModulePathAsync(
        moduleName: String,
        onSuccess: (String) -> Unit,
        onFail: (t: Throwable) -> Unit
    ) {
        val connectedEarly = connection == null
        if (!connectedEarly) connect()

        val build = connection?.newBuild()
            ?: throw IllegalStateException("Illegal state! #connect must be called before #loadDependensies.")

        val swappedOutputStream = ByteArrayOutputStream()
        build.setStandardOutput(swappedOutputStream)

        build.withArguments("-q", "dependencyInsight", "--dependency", moduleName)
        build.run(object : ResultHandler<Void> {
            override fun onComplete(p0: Void?) {
                val lines = swappedOutputStream.toString("UTF-8").lines()
                onSuccess(rawDependencyInsightToModuleName(lines))
            }

            override fun onFailure(e: GradleConnectionException?) {
                System.err.println("failure on refine module $moduleName for project: $e")
                onFail(e ?: Exception("Unknown error"))
            }
        })
        if (!connectedEarly) closeConnection()
    }


    /**
     * Must be called after work
     */
    private fun closeConnection() {
        connection?.close()
    }
}