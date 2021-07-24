/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
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

        createMppFolderStructure()
        createTmpDirectories()

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

    private fun addKtFileToProject(path: String): KtFile {
        val file = File(path)
        val text = configureKotlinVersionAndProperties(FileUtil.loadFile(file, true))
        val virtualFile = createProjectSubFile(file.path.substringAfter(testDataDirectory().path + File.separator), text)
        return virtualFile.toPsiFile(project) as KtFile
    }

    private fun Project.addFileToProject(file: File, atPath: String): PsiFile {
        val virtualFile = createProjectSubFile("$atPath${File.separator}${file.name}", file.readText())
        return virtualFile.toPsiFile(this)!!
    }

    var jvmProjectDirectory: String = "W:\\Kotlin\\Projects\\du"


    var multiplatformProjectDirectory: String = "C:/Users/Codemitry/Desktop/${File(jvmProjectDirectory).name}_mpp"
    lateinit var commonMainSources: String
    lateinit var jvmMainSources: String
    lateinit var jsMainSources: String

    lateinit var virtualMultiplatformProjectDirectory: VirtualFile
    lateinit var virtualCommonMainSources: VirtualFile
    lateinit var virtualJvmMainSources: VirtualFile
    lateinit var virtualJsMainSources: VirtualFile

    val tmpDirectoryName = "__tmp"
    val tmpCommonDirectory by lazy { "$commonMainSources${File.separator}$tmpDirectoryName" }
    val tmpJvmDirectory by lazy { "$jvmMainSources${File.separator}$tmpDirectoryName" }

    lateinit var virtualTmpCommonDirectory: VirtualFile
    lateinit var virtualTmpJvmDirectory: VirtualFile


    private fun createMppFolderStructure() {
        /*
        project:
            - src:
                - commonMain:
                    - kotlin
                - jvmMain:
                    - kotlin
                - jsMain
                    - kotlin
            - build.gradle.kts

         */
        File(multiplatformProjectDirectory).mkdirs()

        val src = File(multiplatformProjectDirectory, "src").apply { mkdir() }

        val commonMain = File(src, "commonMain").apply { mkdir() }
        val commonMainSrcFile = File(commonMain, "kotlin").apply {
            mkdir()
            commonMainSources = absolutePath
        }

        val jvmMain = File(src, "jvmMain").apply { mkdir() }
        File(jvmMain, "kotlin").apply {
            mkdir()
            jvmMainSources = absolutePath
        }

        val jsMain = File(src, "jsMain").apply { mkdir() }
        File(jsMain, "kotlin").apply {
            mkdir()
            jsMainSources = absolutePath
        }
    }

    private fun createTmpDirectories() {
        File(tmpCommonDirectory).mkdirs()
        File(tmpJvmDirectory).mkdirs()
    }


    private fun importFilesToJvmSources() {
        // add all files to project
        importProjectFromTestData()

        virtualMultiplatformProjectDirectory = LocalFileSystem.getInstance().findFileByIoFile(File(projectPath))!!

        virtualCommonMainSources =
            virtualMultiplatformProjectDirectory.findChild("src")!!.findChild("commonMain")!!.findChild("kotlin")!!
        virtualJvmMainSources = virtualMultiplatformProjectDirectory.findChild("src")!!.findChild("jvmMain")!!.findChild("kotlin")!!
        virtualJsMainSources = virtualMultiplatformProjectDirectory.findChild("src")!!.findChild("jsMain")!!.findChild("kotlin")!!


        WriteCommandAction.runWriteCommandAction(project) {
            virtualCommonMainSources.createChildDirectory(this, tmpDirectoryName)
            virtualJvmMainSources.createChildDirectory(this, tmpDirectoryName)

            virtualTmpCommonDirectory = virtualCommonMainSources.findChild(tmpDirectoryName)!!

            virtualTmpJvmDirectory = virtualJvmMainSources.findChild(tmpDirectoryName)!!

            File(jvmProjectDirectory, "src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList().forEach { curJvmFile ->

                LocalFileSystem.getInstance().findFileByPath(curJvmFile.path)!!.copy(this, virtualTmpJvmDirectory, curJvmFile.name)
//            curJvmFile.copyTo(tmpJvmDirectory)
            }
        }


    }

    private fun setupProject() {
        importFilesToJvmSources()

        WriteCommandAction.runWriteCommandAction(project) {
            project.allKotlinFiles().filter { it.isInJvmSources() }.forEach {
                it.acceptExplicitTypeSpecifier()
                it.virtualFile.move(this, virtualTmpCommonDirectory)
//                it.moveTo(tmpCommonDirectory)
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
//                it.moveTo("$jvmMainSources/${it.packageToRelativePath()}")
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
//                jvmKtFile.moveTo("${commonMainSources}/${jvmKtFile.packageToRelativePath()}")
            } else {

                if (jvmKtFile.declarations.all { it.isPrivate() || it.isExpectizingDenied() }) {
                    VfsUtil.createDirectoryIfMissing(virtualJvmMainSources.path + File.separator + jvmKtFile.packageToRelativePath())
                    jvmKtFile.virtualFile.move(this, virtualJvmMainSources.findFileByRelativePath(jvmKtFile.packageToRelativePath())!!)
//                    jvmKtFile.moveTo("${jvmMainSources}/${jvmKtFile.packageToRelativePath()}")
                } else {


                    // create here common file with expects
                    val expectFile = jvmKtFile.getFileWithExpects()
                    VfsUtil.createDirectories(virtualCommonMainSources.path + File.separator + expectFile.packageToRelativePath())
                    // FIXME: 7/23/2021  
                    // expect file - virtual file is null may be
                    expectFile.virtualFile.move(this, virtualCommonMainSources.findFileByRelativePath(expectFile.packageToRelativePath())!!)
//                    expectFile.createDirsAndWriteFile("${commonMainSources}/${expectFile.packageToRelativePath()}")

                    val actualFile = jvmKtFile.getFileWithActuals()
                    val actualWithTODOsFile = jvmKtFile.getFileWithActualsWithTODOs()


                    // create here jvm/js files with actuals
                    when {
                        jvmKtFile.isResolvableWithJvmAnalyzer() -> {
                            actualFile.name = "${actualFile.name.nameWithoutExtension()}Jvm.${actualFile.name.extension()}"

                            VfsUtil.createDirectories(virtualJvmMainSources.path + File.separator + actualFile.packageToRelativePath())
                            actualFile.virtualFile.move(this, virtualJvmMainSources.findFileByRelativePath(actualFile.packageToRelativePath())!!)
//                            actualFile.createDirsAndWriteFile("${jvmMainSources}/${actualFile.packageToRelativePath()}")

                            VfsUtil.createDirectories(virtualJsMainSources.path + File.separator + actualWithTODOsFile.packageToRelativePath())
                            actualWithTODOsFile.virtualFile.move(this, virtualJsMainSources.findFileByRelativePath(actualWithTODOsFile.packageToRelativePath())!!)
                            actualWithTODOsFile.createDirsAndWriteFile("${jsMainSources}/${actualWithTODOsFile.packageToRelativePath()}")

                        }
                        true /* try to resolve with JS analyzer */ -> {
                        }
                        else -> {
                            error("Ooooups... File ${jvmKtFile.name} can not be resolved!")
                        }
                    }

                    virtualTmpCommonDirectory.findChild(jvmKtFile.name)!!.delete(this)
                }

            }

//            File(tmpCommonDirectory, jvmKtFile.name).delete()
        }
        virtualTmpCommonDirectory.delete(this)
        virtualTmpJvmDirectory.delete(this)
//        File(tmpCommonDirectory).delete()
//        File(tmpJvmDirectory).delete()
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

    @Deprecated("Work with VFS")
    private fun KtFile.moveTo(dir: String): KtFile {
        project.allKotlinFiles().find { it === this }!!.delete()
        File("$multiplatformProjectDirectory/${File(virtualFilePath).path.substringAfter(File("project").path)}").delete()
        createDirsAndWriteFile(dir)

        return addKtFileToProject("${dir}/${name}")
    }

    @Deprecated("Work with VFS")
    private fun KtFile.remove() {
        delete()
        File("$multiplatformProjectDirectory/${File(virtualFilePath).path.substringAfter(File("project").path)}").delete()
    }

    @Deprecated("Work with VFS")
    private fun KtFile.copyTo(dir: String): KtFile {
        createDirsAndWriteFile(dir)
        return addKtFileToProject("${dir}/${name}")
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
                    writeText(VfsUtil.virtualToIoFile(it).readText())
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
        File(virtualFilePath).path.contains(File(commonMainSources).path.substringAfter(File(multiplatformProjectDirectory).path))

    private fun KtFile.isInJvmSources(): Boolean =
        File(virtualFilePath).path.contains(File(jvmMainSources).path.substringAfter(File(multiplatformProjectDirectory).path))

    private fun String.nameWithoutExtension(): String = substringBeforeLast(".")
    private fun String.extension(): String = substringAfterLast(".")
}
