/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.impl.scopes.ModulesScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.util.projectStructure.getModuleDir
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class ModuleHelper(val module: IdeaModule, val project: Project) {
    private val gradleProject = module.gradleProject

    val modulePath = module.contentRoots.getAt(0).rootDirectory.absolutePath

    val buildScript = gradleProject.buildScript.sourceFile!!
//    val buildScriptFileType = buildScriptFileTypeFor(buildScript)

//    private val buildScriptParser: BuildScriptParser
//    private val mppBuildScriptGenerator: MultiplatformProjectBuildScriptGenerator
//    private val depsManager = GradleDependenciesManager(module.contentRoots.getAt(0).rootDirectory.absolutePath)

//    init {
//        when (buildScriptFileType) {
//            BuildScriptFileType.KotlinScript -> {
//                buildScriptParser = KtsBuildScriptParser(project, buildScript.absolutePath)
//                mppBuildScriptGenerator = KtsMultiplatformProjectBuildScriptGenerator()
//            }
//            BuildScriptFileType.GroovyScript -> {
//                buildScriptParser = GroovyBuildScriptParser(project, buildScript.absolutePath)
//                mppBuildScriptGenerator = GroovyMultiplatformProjectBuildScriptGenerator()
//            }
//        }
//    }

    val commonMain: File by lazy { File("$modulePath${File.separator}src", "commonMain").apply { mkdirs() } }
    val commonMainSources: File by lazy { File(commonMain, "kotlin").apply { mkdir() } }
    val commonMainResources: File by lazy { File(commonMain, "resources").apply { mkdir() } }


    val jvmMain: File by lazy { File("$modulePath${File.separator}src", "jvmMain").apply { mkdirs() } }
    val jvmMainSources: File by lazy { File(jvmMain, "kotlin").apply { mkdir() } }
    val jvmMainResources: File by lazy { File(jvmMain, "resources").apply { mkdir() } }


    val jsMain: File by lazy { File("$modulePath${File.separator}src", "jsMain").apply { mkdirs() } }
    val jsMainSources: File by lazy { File(jsMain, "kotlin").apply { mkdir() } }
    val jsMainResources: File by lazy { File(jsMain, "resources").apply { mkdir() } }


    private val tmpDirName = "__tmp"
    val tmpCommonMain: File by lazy { File(commonMainSources, tmpDirName).apply { mkdir() } }
    val tmpJvmMain: File by lazy { File(jvmMainSources, tmpDirName).apply { mkdir() } }

    fun importFilesToJvmSources() {
        val jvmModuleSources = module.contentRoots.getAt(0).sourceDirectories.find { it.directory.name == "kotlin" }!!.directory
        jvmModuleSources.copyRecursively(tmpJvmMain)

        jvmModuleSources.parentFile.deleteRecursively()
        File("$modulePath/src", "test").deleteRecursively()
    }


//    fun createBuildScriptForMppAtPath(path: String) {
//        File(path, buildScript.name).apply {
//            parentFile.mkdirs()
//            createNewFile()
//            writeText(getBuildScriptTextForMppProject())
//        }
//    }

//    fun getBuildScriptTextForMppProject(): String {
//
//        val dependencies = depsManager.getDependenciesSynchronously("implementation")
//            .concat(depsManager.getDependenciesSynchronously("compileOnly"))
//            .concat(depsManager.getDependenciesSynchronously("runtimeOnly"))
//            .concat(depsManager.getDependenciesSynchronously("api"))!!
//            .toList()
//
//        buildScriptParser.getRepositoriesSectionInside()?.let { mppBuildScriptGenerator.setRepositories(it) }
//
//        return mppBuildScriptGenerator.apply {
//            setTargets(
//                JvmTarget(),
//                JsTarget()
//            )
//
//            setPlugins(multiplatformPlugin(buildScriptFileType, isRootModule))
//
//            setDependenciesToSourceSet("commonMain", dependencies)
//            setDependenciesToSourceSet("jvmMain", dependencies)
//            setDependenciesToSourceSet("jsMain", dependencies)
//        }.generate()
//    }

//    val isRootModule: Boolean
//        get() {
//            val allModules =
//                module.project.modules.toList().minus(module).filter { it.contentRoots.getAt(0).sourceDirectories.isNotEmpty() }
//
//            return allModules.any { modulePath.startsWith(it.contentRoots.getAt(0).rootDirectory.absolutePath + File.separator) }.not()
//        }
}

fun Module.allKotlinFiles(): List<KtFile> {
    val virtualFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, ModulesScope.moduleScope(this))

    return virtualFiles
        .map { PsiManager.getInstance(project).findFile(it) }
        .filterIsInstance<KtFile>()
}

val Module.commonMainSources: VirtualFile
    get() = VfsUtil.createDirectoryIfMissing(LocalFileSystem.getInstance().findFileByPath(getModuleDir())!!, "src/commonMain/kotlin")

val Module.jvmMainSources: VirtualFile
    get() = VfsUtil.createDirectoryIfMissing(LocalFileSystem.getInstance().findFileByPath(getModuleDir())!!, "src/jvmMain/kotlin")
//        .("src")!!
//        .findChild("jvmMain")!!
//        .findChild("kotlin")!!

val Module.jsMainSources: VirtualFile
    get() = VfsUtil.createDirectoryIfMissing(LocalFileSystem.getInstance().findFileByPath(getModuleDir())!!, "src/jsMain/kotlin")

private const val tmpPackagePath = "__tmp"
val Module.tmpJvmDirectory: VirtualFile
    get() = lazy { VfsUtil.createDirectoryIfMissing(jvmMainSources, tmpPackagePath) }.value

val Module.tmpCommonDirectory: VirtualFile
    get() = lazy { VfsUtil.createDirectoryIfMissing(commonMainSources, tmpPackagePath) }.value

fun Module.allKotlinFilesForSourceSet(sourceSet: VirtualFile): List<KtFile> = allKotlinFiles().filter { it.virtualFilePath.startsWith(sourceSet.path) }