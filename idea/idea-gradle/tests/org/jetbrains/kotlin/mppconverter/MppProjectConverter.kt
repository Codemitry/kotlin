/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.tooling.model.idea.IdeaModule
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.debugger.readAction
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.idea.util.ifTrue
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import org.jetbrains.kotlin.idea.util.projectStructure.module
import org.jetbrains.kotlin.mppconverter.gradle.*
import org.jetbrains.kotlin.mppconverter.resolvers.isNotResolvable
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvable
import org.jetbrains.kotlin.mppconverter.tests.MppConverterTestCase
import org.jetbrains.kotlin.mppconverter.typespecifiyng.acceptExplicitTypeSpecifier
import org.jetbrains.kotlin.mppconverter.visitor.KtExpectMakerVisitorVoid.removeUnresolvableImports
import org.jetbrains.kotlin.mppconverter.visitor.isExpectizingDenied
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import java.io.File

class MppProjectConverter(
    val testCase: MppConverterTestCase
) {


    private val gradleProjectHelper = GradleProjectHelper(testCase.projectFile.absolutePath)

    fun connectToProject() {
        gradleProjectHelper.connectToProject(testCase.project)
    }

    fun closeConnection() {
        gradleProjectHelper.closeConnection()
    }

    fun convertBuildScripts(convert: (module: IdeaModule, buildScript: File) -> String) {
        gradleProjectHelper.walkModules { module, moduleRoot, sourcesRoot ->
            val buildScript = module.gradleProject.buildScript.sourceFile!!

            buildScript.writeText(convert(module, buildScript))
        }
    }

    fun convert() {
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().readAction {
                gradleProjectHelper.importProjectFiles()
                testCase.importVirtualProject()


                WriteCommandAction.runWriteCommandAction(testCase.project) {
                    setupProject()

                    processFiles()
                    commitVirtualProjectFilesToPhysical()
                }
            }
        }

    }


    private fun setupProject() {
        testCase.project.allModules().forEach { module ->
            module.allKotlinFilesForSourceSet(module.jvmMainSources).forEach { file ->
                file.acceptExplicitTypeSpecifier()
                file.virtualFile.move(this, VfsUtil.createDirectoryIfMissing(module.tmpCommonDirectory, file.packageToRelativePath()))
            }
        }
    }

    private fun KtFile.allDeclarationsThatShouldBeMovedToPlatformSrc(): List<KtDeclaration> =
        if (this.isInCommonSources())
            declarations.filter { it.isNotResolvable() && it.isExpectizingDenied() }
        else
            emptyList()

    private fun Project.allKotlinFilesWithDeclarationsThatShouldBeMovedToPlatformSrc(): List<KtFile> =
        allKotlinFiles().filter { it.isInCommonSources() && it.allDeclarationsThatShouldBeMovedToPlatformSrc().isNotEmpty() }


    private fun moveAllFilesThatDependsOnJvmAndCantBeConvertedToCommon() {

        var processingFiles: List<KtFile>
        do {
            processingFiles = testCase.project.allKotlinFilesWithDeclarationsThatShouldBeMovedToPlatformSrc()

            processingFiles.forEach { file ->
                val processingDcls = file.allDeclarationsThatShouldBeMovedToPlatformSrc().map { it.copy() }

                val jvmKtFileParent = VfsUtil.createDirectoryIfMissing(file.module!!.jvmMainSources.path + VfsUtil.VFS_SEPARATOR + file.packageToRelativePath())!!

                val jvmKtFileName = "${file.virtualFile.nameWithoutExtension}Jvm.${file.virtualFile.extension}"

                val jvmKtFile = jvmKtFileParent.findOrCreateChildData(this, jvmKtFileName).toPsiFile(testCase.project) as KtFile


                if (jvmKtFile.text.isEmpty()) {
                    file.packageDirective?.let { jvmKtFile.addWithEndedNL(it, 1) }
                    file.importList?.let { jvmKtFile.addWithEndedNL(it, 1) }
                }

                processingDcls.forEach { jvmKtFile.addWithEndedNL(it, 1) }
                file.allDeclarationsThatShouldBeMovedToPlatformSrc().forEach { it.delete() }
            }


        } while (processingFiles.isNotEmpty())
    }

    private fun processFiles() {
        moveAllFilesThatDependsOnJvmAndCantBeConvertedToCommon()

        testCase.project.allKotlinFiles().filter { it.isInCommonSources() }.forEach { jvmKtFile ->

            println("Check file $jvmKtFile")
            if (jvmKtFile.isResolvable) {
                // the file is fully resolvable with common-analyze => move it to common sources

                // firstly delete unresolvable annotations
                jvmKtFile.accept(object : KtTreeVisitorVoid() {
                    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
                        super.visitAnnotationEntry(annotationEntry)

                        annotationEntry.typeReference?.isNotResolvable()?.ifTrue { annotationEntry.delete() }
                    }
                })

                jvmKtFile.removeUnresolvableImports()

                VfsUtil.createDirectoryIfMissing(jvmKtFile.module!!.commonMainSources.path + File.separator + jvmKtFile.packageToRelativePath())
                jvmKtFile.virtualFile.move(this, jvmKtFile.module!!.commonMainSources.findFileByRelativePath(jvmKtFile.packageToRelativePath())!!)
            } else {
                    // the file can be converted to expect/actual scheme

                    // only check to validity of resolving. Remove it after tests
//                    if (!jvmKtFile.isResolvableWithJvmAnalyzer) {
//                        commitVirtualProjectFilesToPhysical()
//                        error("file $jvmKtFile is not resolvable with jvm analyzer!")
//                    }

                val expectActualMaker = ExpectActualMaker(jvmKtFile)
                expectActualMaker.generateFiles(
                    jvmKtFile.module!!.commonMainSources.path + File.separator + jvmKtFile.packageToRelativePath(),
                    jvmKtFile.name,

                    jvmKtFile.module!!.jvmMainSources.path + File.separator + jvmKtFile.packageToRelativePath(),
                    "${jvmKtFile.virtualFile.nameWithoutExtension}Jvm.${jvmKtFile.virtualFile.extension}",

                    jvmKtFile.module!!.jsMainSources.path + File.separator + jvmKtFile.packageToRelativePath(),
                    "${jvmKtFile.virtualFile.nameWithoutExtension}Js.${jvmKtFile.virtualFile.extension}"
                )

                if (expectActualMaker.expectFile.declarations.isEmpty()) {
                    // all declarations must be in platform part. Then, remove expect file.
                    expectActualMaker.expectFile.virtualFile.delete(this)
                    expectActualMaker.actualFile.virtualFile.rename(
                        this, expectActualMaker.actualFile.name.apply {
                            dropLast("Jvm.kt".length) + ".kt"
                        }
                    )
                    expectActualMaker.actualTODOFile.virtualFile.delete(this)
                }

                jvmKtFile.virtualFile.delete(this)
            }

        }

        testCase.project.allModules().forEach {
            it.tmpCommonDirectory.delete(this)
            it.tmpJvmDirectory.delete(this)
        }

    }

    private val KtFile.isResolvableWithJvmAnalyzer: Boolean
        get() {
            val oldDir = virtualFile.directory()
            val vdir = VfsUtil.createDirectoryIfMissing(module!!.tmpJvmDirectory, packageToRelativePath())
            virtualFile.move(this, vdir)
            val isResolvedWithJvmAnalyzer = isResolvable
            virtualFile.move(this, oldDir)

            return isResolvedWithJvmAnalyzer
        }

    private fun commitVirtualProjectFilesToPhysical() {
        testCase.projectFile.walkBottomUp().forEach { it.delete() }
        val projectFile = LocalFileSystem.getInstance().findFileByIoFile(File(testCase.virtualProjectPath)) ?: error("project path is null")
        projectFile.walkRecursivelyDownTop {
            val dir = testCase.projectFile.absolutePath + it.directory().path.substringAfter(testCase.virtualProjectPath)
            if (it.isDirectory)
                File(dir).mkdirs()
            else
                File(dir, it.name).apply {
                    if (!parentFile.exists())
                        parentFile.mkdirs()
                    createNewFile()
                    writeText(it.toPsiFile(testCase.project)?.text ?: "")
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


    private fun KtFile.isInCommonSources(): Boolean = module?.commonMainSources?.path?.let { return this.virtualFilePath.startsWith(it) } ?: false

    private fun KtFile.isInJvmSources(): Boolean = module?.jvmMainSources?.path?.let { return this.virtualFilePath.startsWith(it) } ?: false

}
