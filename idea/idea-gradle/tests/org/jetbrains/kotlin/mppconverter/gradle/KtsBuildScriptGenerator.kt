/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

import org.jetbrains.kotlin.mppconverter.gradle.generator.BuildScriptGenerator
import org.jetbrains.kotlin.mppconverter.gradle.generator.Target

class KtsBuildScriptGenerator : BuildScriptGenerator {
    override val plugins = mutableListOf<String>()
    override val targets = mutableListOf<Target>()

    // TODO: add repositories

    override val sourceSetsDependencies = mutableMapOf<String, MutableList<BuildGradleFileForMultiplatformProjectConfigurator.Dependency>>()

    private fun getDependenciesSectionForSourceSet(sourceSet: String): String =
        if (hasNotSourceSet(sourceSet))
            ""
        else """
dependencies {
${sourceSetDependencies(sourceSet)!!.joinToString("\n") { "${it.configuration}(\"${it.name}\")".withIndent(1) }}
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


    override val sourceSetsSection: String
        get() {
            if (targets.isEmpty()) return ""

            return buildString {
                targets.forEach {
                    val dependenciesMain = getDependenciesSectionForSourceSet("${it.name}Main")
                    append(
                        """
${it.name}Main by getting${
                            if (dependenciesMain.isEmpty()) "" else """ {
${dependenciesMain.withIndent(1)}
}

""".trimIndent()
                        }

                """.trimIndent()
                    )

                    val dependenciesTest = getDependenciesSectionForSourceSet("${it.name}Test")
                    append(
                        """
${it.name}Test by getting${
                            if (dependenciesTest.isEmpty()) "" else """ {
${dependenciesTest.withIndent(1)}
}

""".trimIndent()
                        }

                """.trimIndent()
                    )
                }
            }

        }

    fun generate(): String = """
$pluginsSection

kotlin {
${targetsSection.withIndent(1)}
    
    targets.all {
        compilations.all {
            kotlinOptions {
                allWarningsAsErrors = true
            }
        }
    }
    
${sourceSetsSection.withIndent(1)}
}

    """.trimIndent()

    override fun toString(): String = generate()
}