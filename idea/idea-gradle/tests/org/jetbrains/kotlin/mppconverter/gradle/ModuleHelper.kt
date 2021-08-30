/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.impl.scopes.ModulesScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.analyzer.moduleInfo
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.idea.configuration.getWholeModuleGroup
import org.jetbrains.kotlin.idea.project.getStableName
import org.jetbrains.kotlin.idea.util.projectStructure.getModuleDir
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class ModuleHelper(val module: IdeaModule, val project: Project) {

    val modulePath = module.contentRoots.getAt(0).rootDirectory.absolutePath


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
        val jvmModuleSources = module.contentRoots.getAt(0).sourceDirectories.find { it.directory.name == "kotlin" }?.directory

        if (jvmModuleSources?.exists() != true) return

        jvmModuleSources.copyRecursively(tmpJvmMain)

        jvmModuleSources.parentFile?.deleteRecursively()
        File("$modulePath/src", "test").deleteRecursively()
    }
}

fun Module.allKotlinFiles(): List<KtFile> {
    val virtualFiles = FileTypeIndex.getFiles(KotlinFileType.INSTANCE, ModulesScope.moduleScope(this))

    return virtualFiles
        .map { PsiManager.getInstance(project).findFile(it) }
        .filterIsInstance<KtFile>()
}

fun org.jetbrains.kotlin.descriptors.ModuleDescriptor.isCommonModuleForThisProject(project: Project): Boolean {
    return ModuleManager.getInstance(project).findModuleByName(moduleInfo?.displayedName ?: "")?.isCommon ?: false
}

fun KtFile.isInCommonMainSources(): Boolean = module?.commonMainSources?.path?.let { return this.virtualFilePath.startsWith(it) } ?: false


val Module.isCommon: Boolean
    get() = /*this.platform?.isCommon() ?:*/ (getStableName().asString() == "<${getWholeModuleGroup().baseModule.name}_commonMain>" ||
            getStableName().asString() == "<${getWholeModuleGroup().baseModule.name}_commonTest>")

val Module.commonMainSources: VirtualFile
    get() = VfsUtil.createDirectoryIfMissing(LocalFileSystem.getInstance().findFileByPath(getModuleDir())!!, "src/commonMain/kotlin")

val Module.jvmMainSources: VirtualFile
    get() = VfsUtil.createDirectoryIfMissing(LocalFileSystem.getInstance().findFileByPath(getModuleDir())!!, "src/jvmMain/kotlin")

val Module.jsMainSources: VirtualFile
    get() = VfsUtil.createDirectoryIfMissing(LocalFileSystem.getInstance().findFileByPath(getModuleDir())!!, "src/jsMain/kotlin")

private const val tmpPackagePath = "__tmp"
val Module.tmpJvmDirectory: VirtualFile
    get() = lazy { VfsUtil.createDirectoryIfMissing(jvmMainSources, tmpPackagePath) }.value

val Module.tmpCommonDirectory: VirtualFile
    get() = lazy { VfsUtil.createDirectoryIfMissing(commonMainSources, tmpPackagePath) }.value

fun Module.allKotlinFilesForSourceSet(sourceSet: VirtualFile): List<KtFile> = allKotlinFiles().filter { it.virtualFilePath.startsWith(sourceSet.path) }