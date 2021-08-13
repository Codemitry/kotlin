/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

import com.intellij.openapi.project.Project
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.mppconverter.gradle.generator.*
import org.jetbrains.kotlin.mppconverter.gradle.parser.BuildScriptParser
import org.jetbrains.kotlin.mppconverter.gradle.parser.GroovyBuildScriptParser
import org.jetbrains.kotlin.mppconverter.gradle.parser.KtsBuildScriptParser
import org.jetbrains.kotlin.util.collectionUtils.concat
import java.io.File

class ModuleHelper(val module: IdeaModule, val project: Project) {
    private val gradleProject = module.gradleProject

    val modulePath = module.contentRoots.getAt(0).rootDirectory.absolutePath

    val buildScript = gradleProject.buildScript.sourceFile!!
    val buildScriptFileType = buildScriptFileTypeFor(buildScript)

    private val buildScriptParser: BuildScriptParser
    private val mppBuildScriptGenerator: MultiplatformProjectBuildScriptGenerator
    private val depsManager = GradleDependenciesManager(module.contentRoots.getAt(0).rootDirectory.absolutePath)

    init {
        when (buildScriptFileType) {
            BuildScriptFileType.KotlinScript -> {
                buildScriptParser = KtsBuildScriptParser(project, buildScript.absolutePath)
                mppBuildScriptGenerator = KtsMultiplatformProjectBuildScriptGenerator()
            }
            BuildScriptFileType.GroovyScript -> {
                buildScriptParser = GroovyBuildScriptParser(project, buildScript.absolutePath)
                mppBuildScriptGenerator = GroovyMultiplatformProjectBuildScriptGenerator()
            }
        }
    }

    fun createBuildScriptForMppAtPath(path: String) {
        File(path, buildScript.name).apply {
            parentFile.mkdirs()
            createNewFile()
            writeText(getBuildScriptTextForMppProject())
        }
    }

    fun getBuildScriptTextForMppProject(): String {

        val dependencies = depsManager.getDependenciesSynchronously("implementation")
            .concat(depsManager.getDependenciesSynchronously("compileOnly"))
            .concat(depsManager.getDependenciesSynchronously("runtimeOnly"))
            .concat(depsManager.getDependenciesSynchronously("api"))!!
            .toList()

        buildScriptParser.getRepositoriesSectionInside()?.let { mppBuildScriptGenerator.setRepositories(it) }

        return mppBuildScriptGenerator.apply {
            setTargets(
                JvmTarget(),
                JsTarget()
            )

            setPlugins(multiplatformPlugin(buildScriptFileType, isRootModule))

            setDependenciesToSourceSet("commonMain", dependencies)
            setDependenciesToSourceSet("jvmMain", dependencies)
            setDependenciesToSourceSet("jsMain", dependencies)
        }.generate()
    }

    val isRootModule: Boolean
        get() {
            val allModules =
                module.project.modules.toList().minus(module).filter { it.contentRoots.getAt(0).sourceDirectories.isNotEmpty() }

            return allModules.any { modulePath.startsWith(it.contentRoots.getAt(0).rootDirectory.absolutePath + File.separator) }.not()
        }
}