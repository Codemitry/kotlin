/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle.parser

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.mppconverter.createTmpKotlinScriptFile
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtScriptInitializer
import java.io.File

class KtsBuildScriptParser(
    private val usingProject: Project,
    override val buildScriptPath: String
) : BuildScriptParser {

    private val buildScriptFile = File(buildScriptPath)

    init {
        if (!buildScriptFile.isFile)
            throw IllegalArgumentException("buildScriptPath ($buildScriptPath) is not path to file!")

        if (buildScriptFile.extension != "kts")
            throw IllegalArgumentException("buildScriptPath ($buildScriptPath) is not path to .kts file!")
    }

    private fun getRepositoriesCall(): KtCallExpression? {
        return usingProject.createTmpKotlinScriptFile("build.gradle.kts", buildScriptFile.readText())
            .script?.blockExpression?.children?.filterIsInstance<KtScriptInitializer>()
            ?.filter { it.firstChild is KtCallExpression }
            ?.map { it.firstChild as KtCallExpression }?.find { it.calleeExpression?.text == "repositories" }
    }

    override fun getRepositoriesSection(): String? = getRepositoriesCall()?.text

    override fun getRepositoriesSectionInside(): String? = getRepositoriesCall()?.lambdaArguments?.first()?.getLambdaExpression()?.bodyExpression?.text
}