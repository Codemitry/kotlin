/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

import com.intellij.openapi.project.Project
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.idea.IdeaProject
import java.io.File

class GradleProjectHelper(projectRoot: String) {

    private val projectRoot = File(projectRoot)

    private var connection: ProjectConnection? = null

    private val modulesHelpers: MutableCollection<ModuleHelper> = mutableListOf()

    private var gradleProjectModel: GradleProject? = null
    private var ideaProjectModel: IdeaProject? = null

    init {
        if (!this.projectRoot.isDirectory) throw IllegalArgumentException("projectRoot $projectRoot is not directory!")
    }

    fun connectToProject(usingProject: Project) {
        GradleConnector.newConnector().apply {
            forProjectDirectory(projectRoot)
            connection = connect()
        }

        ideaProjectModel = connection?.getModel(IdeaProject::class.java)
        gradleProjectModel = connection?.getModel(GradleProject::class.java)


        modulesHelpers.clear()
        ideaProjectModel?.modules?.forEach { module ->
            if (module.contentRoots.getAt(0).sourceDirectories.isNotEmpty())
                modulesHelpers.add(ModuleHelper(module, usingProject))
        }
    }


    val settingsGradleFile: File? by lazy {
        val settingsGroovy = File(this.projectRoot, "settings.gradle")
        val settingsKts = File(this.projectRoot, "settings.gradle.kts")
        when {
            settingsGroovy.isFile -> settingsGroovy
            settingsKts.isFile -> settingsKts
            else -> null
        }
    }

    val projectName: String by lazy {
        gradleProjectModel?.name
            ?: throw IllegalStateException("Illegal state! #connectToProject must be called before calling projectName.")
    }

    val isPrimitiveSingleModuleProject: Boolean by lazy {
        val modules = ideaProjectModel?.modules
            ?: throw IllegalStateException("Illegal state! #connectToProject must be called before calling isOneModule.")

        modules.size == 1 && modules.getAt(0).contentRoots.getAt(0).rootDirectory == this.projectRoot
    }

    fun walkModules(action: (moduleRoot: File, sourcesRoot: File) -> Unit) {
        ideaProjectModel?.modules?.forEach { module ->
            val contentRoot = module.contentRoots.getAt(0)
            action(contentRoot.rootDirectory, contentRoot.sourceDirectories.find { it.directory.name == "kotlin" }!!.directory)
        }
    }


    fun createMppModulesStructureAtPath(mppPath: String) {
        settingsGradleFile?.let { it.copyTo(File(mppPath, it.name)) }

        modulesHelpers.forEach {
            val mppModulePath = mppPath + it.modulePath.substringAfter(projectRoot.absolutePath)
            it.createBuildScriptForMppAtPath(mppModulePath)
        }
    }


    fun closeConnection() {
        ideaProjectModel = null
        gradleProjectModel = null
        connection?.close() ?: throw IllegalStateException("Illegal state! #connectToProject must be called before #closeConnection.")
    }

}

const val multiplatformPluginVersion = "1.5.10"

fun multiplatformPlugin(buildScript: BuildScriptFileType, withVersion: Boolean = true): String = when (buildScript) {
    BuildScriptFileType.KotlinScript -> "kotlin(\"multiplatform\") ${if (withVersion) "version \"$multiplatformPluginVersion\"" else ""}"
    BuildScriptFileType.GroovyScript -> "id 'org.jetbrains.kotlin.multiplatform' ${if (withVersion) "version '$multiplatformPluginVersion'" else ""}"
}