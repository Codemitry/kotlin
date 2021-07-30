/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.debugger.readAction
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.mppconverter.gradle.GradleProjectHelper
import org.jetbrains.kotlin.mppconverter.resolvers.isNotResolvable
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvable
import org.jetbrains.kotlin.mppconverter.typespecifiyng.acceptExplicitTypeSpecifier
import org.jetbrains.kotlin.mppconverter.visitor.getFileWithActuals
import org.jetbrains.kotlin.mppconverter.visitor.getFileWithActualsWithTODOs
import org.jetbrains.kotlin.mppconverter.visitor.getFileWithExpects
import org.jetbrains.kotlin.mppconverter.visitor.isExpectizingDenied
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.junit.Test
import java.io.File

class MppProjectConverter : MultiplePluginVersionGradleImportingTestCase() {

    @Test
    fun main() {

        assertGradleOneModuleProjectStructure()

        val gph = GradleProjectHelper(jvmProjectDirectory)
        gph.connectToProject(project)

        File(multiplatformProjectDirectory).deleteRecursively()
        File(multiplatformProjectDirectory).mkdirs()

        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().readAction {

                File(multiplatformProjectDirectory, gph.getBuildScriptFileNameForThisProject()).apply {
                    createNewFile()
                    writeText(gph.getMultiplatformBuildScriptTextForThisProject())
                }

                setupProject()
                WriteCommandAction.runWriteCommandAction(project) {

                    processFiles()
                    gph.closeConnection()
                    commitVirtualProjectFilesToPhysical()
                }
            }
        }

    }

    var jvmProjectDirectory: String = "/Users/Dmitry.Sokolov/ideaProjects/tests/birthdaykata-master"

    var multiplatformProjectDirectory: String = "/Users/Dmitry.Sokolov/ideaProjects/testsResults/${File(jvmProjectDirectory).name}_mpp"

    lateinit var virtualMultiplatformProjectDirectory: VirtualFile
    lateinit var virtualCommonMainSources: VirtualFile
    lateinit var virtualJvmMainSources: VirtualFile
    lateinit var virtualJsMainSources: VirtualFile

    val tmpDirectoryName = "__tmp"

    lateinit var virtualTmpCommonDirectory: VirtualFile
    lateinit var virtualTmpJvmDirectory: VirtualFile


    private fun importFilesToJvmSources() {
        // add all files to project
        importProjectFromTestData()

        virtualMultiplatformProjectDirectory = LocalFileSystem.getInstance().findFileByIoFile(File(projectPath))!!

        virtualCommonMainSources = virtualMultiplatformProjectDirectory
            .findChild("src")!!
            .findChild("commonMain")!!
            .findChild("kotlin")!!
        virtualJvmMainSources = virtualMultiplatformProjectDirectory
            .findChild("src")!!
            .findChild("jvmMain")!!
            .findChild("kotlin")!!
        virtualJsMainSources = virtualMultiplatformProjectDirectory
            .findChild("src")!!
            .findChild("jsMain")!!
            .findChild("kotlin")!!


        WriteCommandAction.runWriteCommandAction(project) {
            virtualCommonMainSources.createChildDirectory(this, tmpDirectoryName)
            virtualJvmMainSources.createChildDirectory(this, tmpDirectoryName)

            virtualTmpCommonDirectory = virtualCommonMainSources.findChild(tmpDirectoryName)!!

            virtualTmpJvmDirectory = virtualJvmMainSources.findChild(tmpDirectoryName)!!

            File(jvmProjectDirectory, "src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList().forEach { curJvmFile ->
                LocalFileSystem.getInstance().findFileByPath(curJvmFile.path)!!.copy(this, virtualTmpJvmDirectory, curJvmFile.name)
            }
        }


    }

    private fun setupProject() {
        importFilesToJvmSources()

        WriteCommandAction.runWriteCommandAction(project) {
            project.allKotlinFiles().filter { it.isInJvmSources() }.forEach {
                it.acceptExplicitTypeSpecifier()
                it.virtualFile.move(this, virtualTmpCommonDirectory)
            }
        }

    }

    private fun moveAllFilesThatDependsOnJvmAndCantBeConvertedToCommon() {
        var fullyJvmDependentFiles = project.allKotlinFiles().filter {
            it.isInCommonSources() && it.isNotResolvable() && it.isResolvableWithJvmAnalyzer() && !it.canConvertToCommon()
        }

        while (fullyJvmDependentFiles.isNotEmpty()) {
            fullyJvmDependentFiles.forEach {
                VfsUtil.createDirectoryIfMissing(virtualJvmMainSources.path + File.separator + it.packageToRelativePath())
                it.virtualFile.move(this, virtualJvmMainSources.findFileByRelativePath(it.packageToRelativePath())!!)
            }

            fullyJvmDependentFiles = project.allKotlinFiles()
                .filter { it.isInCommonSources() && it.isNotResolvable() && it.isResolvableWithJvmAnalyzer() && !it.canConvertToCommon() }
        }
    }

    private fun processFiles() {
        moveAllFilesThatDependsOnJvmAndCantBeConvertedToCommon()

        project.allKotlinFiles().filter { it.isInCommonSources() }.forEach { jvmKtFile ->

            if (jvmKtFile.isResolvable()) {
                // the file is fully resolvable with common-analyze
                VfsUtil.createDirectoryIfMissing(virtualCommonMainSources.path + File.separator + jvmKtFile.packageToRelativePath())
                jvmKtFile.virtualFile.move(this, virtualCommonMainSources.findFileByRelativePath(jvmKtFile.packageToRelativePath())!!)
            } else {

                if (jvmKtFile.declarations.all { it.isPrivate() || it.isExpectizingDenied() }) {
                    VfsUtil.createDirectoryIfMissing(virtualJvmMainSources.path + File.separator + jvmKtFile.packageToRelativePath())
                    jvmKtFile.virtualFile.move(this, virtualJvmMainSources.findFileByRelativePath(jvmKtFile.packageToRelativePath())!!)
                } else {


                    // create here common file with expects
                    val expectFile = jvmKtFile.getFileWithExpects(
                        virtualCommonMainSources.path + File.separator + jvmKtFile.packageToRelativePath(),
                        jvmKtFile.name
                    )

                    if (!jvmKtFile.isResolvableWithJvmAnalyzer())
                        error("file $jvmKtFile is not resolvable with jvm analyzer!")

                    val actualFile = jvmKtFile.getFileWithActuals(
                        virtualJvmMainSources.path + File.separator + jvmKtFile.packageToRelativePath(),
                        "${jvmKtFile.virtualFile.nameWithoutExtension}Jvm.${jvmKtFile.virtualFile.extension}"
                    )

                    val actualWithTODOsFile = jvmKtFile.getFileWithActualsWithTODOs(
                        virtualJsMainSources.path + File.separator + jvmKtFile.packageToRelativePath(),
                        "${jvmKtFile.virtualFile.nameWithoutExtension}Js.${jvmKtFile.virtualFile.extension}"
                    )


                    virtualTmpCommonDirectory.findChild(jvmKtFile.name)!!.delete(this)
                }

            }

        }
        virtualTmpCommonDirectory.delete(this)
        virtualTmpJvmDirectory.delete(this)
    }


    private fun assertGradleOneModuleProjectStructure() {
        // TODO migrate check to Gradle Tooling API
        val root = File(jvmProjectDirectory)
        if (!root.exists()) throw IllegalArgumentException("Project directory does not exist")

        val buildGradle = File(root, "build.gradle")
        val buildGradleKts = File(root, "build.gradle.kts")
        if (!buildGradle.exists() && !buildGradleKts.exists()) throw IllegalArgumentException("build.gradle does not exist")

        val src = File(root, "src")
        if (!src.exists()) throw IllegalArgumentException("src directory does not exist")

        val mainSourceSet = File(src, "main")
        if (!mainSourceSet.exists()) throw IllegalArgumentException("main source set does not exist")

        if (!File(mainSourceSet, "kotlin").exists()) throw IllegalArgumentException("main/kotlin  does not exist")
    }


    // dir with project
    // required by TestCase
    override fun testDataDirectory(): File {
        return File(multiplatformProjectDirectory)
    }

    private fun KtFile.isResolvableWithJvmAnalyzer(): Boolean {
        val oldDir = virtualFile.directory()
        virtualFile.move(this, virtualTmpJvmDirectory)
        val isResolvedWithJvmAnalyzer = isResolvable()
        virtualFile.move(this, oldDir)

        return isResolvedWithJvmAnalyzer
    }

    private fun commitVirtualProjectFilesToPhysical() {
        File(multiplatformProjectDirectory).walkBottomUp().forEach { it.delete() }
        val projectFile = LocalFileSystem.getInstance().findFileByIoFile(File(projectPath)) ?: error("project path is null")
        projectFile.walkRecursivelyDownTop {
            val dir = multiplatformProjectDirectory + it.directory().path.substringAfter(projectPath)
            if (it.isDirectory)
                File(dir).mkdirs()
            else
                File(dir, it.name).apply {
                    if (!parentFile.exists())
                        parentFile.mkdirs()
                    createNewFile()
                    writeText(it.toPsiFile(project)?.text ?: "")
                }
        }
    }

    private fun VirtualFile.directory(): VirtualFile = if (isDirectory) this else parent

    private fun VirtualFile.walkRecursivelyDownTop(job: (VirtualFile) -> Unit) {
        children.forEach {
            job(it)
            it.walkRecursivelyDownTop(job)
        }
    }


    private fun KtFile.isInCommonSources(): Boolean =
        virtualFilePath.contains(virtualCommonMainSources.path.substringAfter(virtualMultiplatformProjectDirectory.path))

    private fun KtFile.isInJvmSources(): Boolean =
        virtualFilePath.contains(virtualJvmMainSources.path.substringAfter(virtualMultiplatformProjectDirectory.path))

}
