/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle.generator

class KtsMultiplatformProjectBuildScriptGenerator : MultiplatformProjectBuildScriptGenerator() {
    override val plugins = mutableListOf<String>()
    override val targets = mutableListOf<Target>()
    override val repositories = mutableListOf<String>()

    override val sourceSetsDependencies = mutableMapOf<String, MutableList<Dependency>>()

    private fun getDependenciesSectionForSourceSet(sourceSet: String): String =
        if (hasNotSourceSet(sourceSet))
            ""
        else """
dependencies {
${sourceSetDependencies(sourceSet)!!.joinToString("\n") { it.presentableView().withIndent(1) }}
}
        """.trimIndent()


    override val pluginsSection: String
        get() = if (plugins.isEmpty()) "" else """
plugins {
${plugins.joinToString("\n") { it.withIndent(1) }}
}
    """.trimIndent()

    override val targetsSection: String
        get() = if (targets.isEmpty()) "" else """
${targets.joinToString("\n")}
    """.trimIndent()

    override val repositoriesSection: String
        get() = if (repositories.isEmpty()) "" else """
repositories {
${repositories.joinToString("\n") { it.withIndent(1) }}
}
        """.trimIndent()


    override fun sourceSetSection(sourceSet: String): String {
        val dependencies = getDependenciesSectionForSourceSet(sourceSet)
        return """
val $sourceSet by getting${
            if (dependencies.isEmpty()) "" else """ {
${dependencies.withIndent(1)}
}""".trimIndent()
        }

                """.trimIndent()
    }

    override val sourceSetsSection: String
        get() {
            if (targets.isEmpty()) return ""

            return buildString {
                appendLine("sourceSets {")
                appendLine(sourceSetSection("commonMain").withIndent(1))
                appendLine(sourceSetSection("commonTest").withIndent(1))

                targets.forEach {
                    appendLine(sourceSetSection("${it.name}Main").withIndent(1))
                    appendLine(sourceSetSection("${it.name}Test").withIndent(1))
                }
                appendLine("}")
            }
        }

    override fun generate(): String = """
$pluginsSection

$repositoriesSection

kotlin {
${targetsSection.withIndent(1)}
    
    targets.all {
        compilations.all {
            kotlinOptions {
            }
        }
    }
    
${sourceSetsSection.withIndent(1)}
}

    """.trimIndent()

    override fun toString(): String = generate()

    override fun Dependency.presentableView(): String {
        return buildString {
            append(configuration)
            append("(")
            append(
                when (this@presentableView) {
                    is ModuleDependency -> "project(\":$moduleNotation\")"
                    is ExternalDependency -> "\"$artifact\""
                }
            )
            append(")")
        }
    }
}