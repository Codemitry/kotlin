/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle.generator

import org.jetbrains.kotlin.mppconverter.gradle.BuildGradleFileForMultiplatformProjectConfigurator.Dependency

interface BuildScriptGenerator {
    val plugins: MutableList<String>
    val targets: MutableList<Target>

    // TODO: add repositories

    /**
     * format: sourceSet: dependency
     */
    val sourceSetsDependencies: MutableMap<String, MutableList<Dependency>>

    val pluginsSection: String
    val targetsSection: String
    val sourceSetsSection: String

    fun addDependencyToSourceSet(sourceSet: String, dependency: Dependency) {
        if (!sourceSetsDependencies.containsKey(sourceSet))
            sourceSetsDependencies[sourceSet] = mutableListOf()

        sourceSetsDependencies[sourceSet]!!.add(dependency)
    }

    fun hasSourceSet(sourceSet: String): Boolean = sourceSetsDependencies.containsKey(sourceSet)
    fun hasNotSourceSet(sourceSet: String): Boolean = !hasSourceSet(sourceSet)

    fun sourceSetDependencies(sourceSet: String): List<Dependency>? = sourceSetsDependencies[sourceSet]?.toList()

    fun String.withIndent(indent: Int): String = lines().joinToString("\n") { "${"\t".repeat(indent)}$it" }
}
