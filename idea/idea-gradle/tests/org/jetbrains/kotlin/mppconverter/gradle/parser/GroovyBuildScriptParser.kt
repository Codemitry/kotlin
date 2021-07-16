/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle.parser

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.mppconverter.createTmpGroovyFile
import org.jetbrains.kotlin.mppconverter.createTmpKotlinScriptFile
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import java.io.File

class GroovyBuildScriptParser(
    private val usingProject: Project,
    override val buildScriptPath: String
) : BuildScriptParser {

    private val buildScriptFile = File(buildScriptPath)

    init {
        if (!buildScriptFile.isFile)
            throw IllegalArgumentException("buildScriptPath ($buildScriptPath) is not path to file!")

        if (buildScriptFile.extension != "gradle")
            throw IllegalArgumentException("buildScriptPath ($buildScriptPath) is not path to .kts file!")
    }

    override fun getRepositoriesSection(): String? {
        val repositoriesCall = usingProject.createTmpGroovyFile(buildScriptFile.readText()).children
            .filterIsInstance<GrMethodCallExpression>()
            .find { it.invokedExpression.text == "repositories" }
        return repositoriesCall?.text
    }
}