/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle.generator

interface MultiplatformProjectBuildScriptGenerator {
    val plugins: MutableList<String>
    val targets: MutableList<Target>
    val repositories: MutableList<String>

    /**
     * format: sourceSet: dependency
     */
    val sourceSetsDependencies: MutableMap<String, MutableList<Dependency>>

    val pluginsSection: String
    val targetsSection: String
    val sourceSetsSection: String
    val repositoriesSection: String

    fun addDependencyToSourceSet(sourceSet: String, dependency: Dependency) {
        if (!sourceSetsDependencies.containsKey(sourceSet))
            sourceSetsDependencies[sourceSet] = mutableListOf()

        sourceSetsDependencies[sourceSet]!!.add(dependency)
    }

    fun setDependenciesToSourceSet(sourceSet: String, dependencies: Collection<Dependency>) {
        sourceSetsDependencies[sourceSet]?.clear()

        dependencies.forEach { addDependencyToSourceSet(sourceSet, it) }
    }

    fun setPlugins(plugins: Collection<String>) {
        this.plugins.clear()
        this.plugins.addAll(plugins)
    }

    fun setPlugins(vararg plugins: String) {
        setPlugins(plugins.toList())
    }

    fun setTargets(targets: Collection<Target>) {
        this.targets.clear()
        this.targets.addAll(targets)
    }

    fun setTargets(vararg targets: Target) {
        setTargets(targets.toList())
    }

    fun setRepositories(repositories: Collection<String>) {
        this.repositories.clear()
        this.repositories.addAll(repositories)
    }

    fun setRepositories(vararg repositories: String) {
        setRepositories(repositories.toList())
    }

    fun sourceSetSection(sourceSet: String): String
    fun generate(): String


    fun hasSourceSet(sourceSet: String): Boolean = sourceSetsDependencies.containsKey(sourceSet)
    fun hasNotSourceSet(sourceSet: String): Boolean = !hasSourceSet(sourceSet)

    fun sourceSetDependencies(sourceSet: String): List<Dependency>? = sourceSetsDependencies[sourceSet]?.toList()

    fun String.withIndent(indent: Int): String = lines().joinToString("\n") { "${"\t".repeat(indent)}$it" }
}
