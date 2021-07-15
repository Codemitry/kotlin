/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.debugger.readAction
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.mppconverter.gradle.BuildGradleFileForMultiplatformProjectConfigurator
import org.jetbrains.kotlin.mppconverter.gradle.GradleProjectHelper
import org.jetbrains.kotlin.mppconverter.resolvers.isNotResolvable
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvable
import org.jetbrains.kotlin.mppconverter.typespecifiyng.acceptExplicitTypeSpecifier
import org.jetbrains.kotlin.mppconverter.visitor.getFileWithActuals
import org.jetbrains.kotlin.mppconverter.visitor.getFileWithActualsWithTODOs
import org.jetbrains.kotlin.mppconverter.visitor.getFileWithExpects
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtScriptInitializer
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.junit.Test
import java.io.File

class MppProjectConverter : MultiplePluginVersionGradleImportingTestCase() {

    @Test
    fun main() {

        assertGradleOneModuleProjectStructure()

        val gph = GradleProjectHelper(jvmProjectDirectory)
        gph.connectToProject()

        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().readAction {
                // TODO: put repositories getting to function
                when (gph.buildScriptFileType) {
                    GradleProjectHelper.BuildScriptFileType.KotlinScript -> {
                        val repositoriesCall = project.createTmpKotlinScriptFile(
                            "build.gradle.kts",
                            gph.buildScriptFile.readText()
                        ).script?.blockExpression?.children?.filterIsInstance<KtScriptInitializer>()
                            ?.filter { it.firstChild is KtCallExpression }
                            ?.map { it.firstChild as KtCallExpression }?.find { it.calleeExpression?.text == "repositories" }

                        repositories = repositoriesCall?.lambdaArguments?.first()?.getLambdaExpression()?.bodyExpression?.text
                    }
                    GradleProjectHelper.BuildScriptFileType.GroovyScript -> {
                        val repositoriesCall = project.createTmpGroovyFile(gph.buildScriptFile.readText()).children
                            .filterIsInstance<GrMethodCallExpression>()
                            .find { it.invokedExpression.text == "repositories" }
                        repositories = repositoriesCall?.closureArguments?.first()?.statements?.joinToString("\n") { it.text }
                    }
                }
            }
        }

        gph.loadDependencies { dependencies ->
            this@MppProjectConverter.dependencies = dependencies

            ApplicationManager.getApplication().invokeLater {
                ApplicationManager.getApplication().readAction {
                    setupProject()
                    WriteCommandAction.runWriteCommandAction(project) {
                        processFiles()
                    }

                }
            }
        }

        gph.closeConnection()

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

    val tmpDirectoryName = "__tmp"
    val tmpCommonDirectory by lazy { "$commonMainSources${File.separator}$tmpDirectoryName" }
    val tmpJvmDirectory by lazy { "$jvmMainSources${File.separator}$tmpDirectoryName" }

    var repositories: String? = null
    var dependencies: List<String>? = null

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

    private fun createBuildScriptFile() {
        val buildScript = File(multiplatformProjectDirectory, "build.gradle.kts").apply {
            createNewFile()

            val buildGradleBuilder = BuildGradleFileForMultiplatformProjectConfigurator.Builder()
            repositories?.let { buildGradleBuilder.repositories(listOf(it)) }
            dependencies?.let {
                val deps = it.map { dependency ->
                    BuildGradleFileForMultiplatformProjectConfigurator.Dependency(dependency, "implementation")
                }

                buildGradleBuilder.commonMainDependencies(deps)
                buildGradleBuilder.jvmMainDependencies(deps)
            }

            writeText(buildGradleBuilder.build().text)
        }
    }


    private fun importFilesToJvmSources() {
        File(jvmProjectDirectory, "src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList().forEach { curJvmFile ->
            curJvmFile.copyTo(tmpJvmDirectory)
        }

        // add all files to project
        importProjectFromTestData()
    }

    private fun setupProject() {
        createMppFolderStructure()
        createTmpDirectories()
        createBuildScriptFile()

        importFilesToJvmSources()

        WriteCommandAction.runWriteCommandAction(project) {
            project.allKotlinFiles().filter { it.isInJvmSources() }.forEach {
                it.acceptExplicitTypeSpecifier()
                it.moveTo(tmpCommonDirectory)
            }
        }

    }

    private fun moveAllFilesThatDependsOnJvmAndCantBeConvertedToCommon() {
        var fullyJvmDependentFiles = project.allKotlinFiles().filter {
            it.isInCommonSources() && it.isNotResolvable() && it.isResolvableWithJvmAnalyzer() && !it.canConvertToCommon()
        }

        while (fullyJvmDependentFiles.isNotEmpty()) {
            fullyJvmDependentFiles.forEach {
                it.moveTo("$jvmMainSources/${it.packageToRelativePath()}")
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
                jvmKtFile.moveTo("${commonMainSources}/${jvmKtFile.packageToRelativePath()}")
            } else {
                // create here common file with expects
                val expectFile = jvmKtFile.getFileWithExpects()
                expectFile.createDirsAndWriteFile("${commonMainSources}/${expectFile.packageToRelativePath()}")

                val actualFile = jvmKtFile.getFileWithActuals()
                val actualWithTODOsFile = jvmKtFile.getFileWithActualsWithTODOs()


                // create here jvm/js files with actuals
                when {
                    jvmKtFile.isResolvableWithJvmAnalyzer() -> {
                        actualFile.createDirsAndWriteFile("${jvmMainSources}/${actualFile.packageToRelativePath()}")
                        actualWithTODOsFile.createDirsAndWriteFile("${jsMainSources}/${actualWithTODOsFile.packageToRelativePath()}")

                    }
                    true /* try to resolve with JS analyzer */ -> {
                    }
                    else -> {
                        error("Ooooups... File ${jvmKtFile.name} can be resolved!")
                    }
                }

            }

            File(tmpCommonDirectory, jvmKtFile.name).delete()
        }
        File(tmpCommonDirectory).delete()
        File(tmpJvmDirectory).delete()
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

    private fun KtFile.moveTo(dir: String): KtFile {
        project.allKotlinFiles().find { it === this }!!.delete()
        File("$multiplatformProjectDirectory/${File(virtualFilePath).path.substringAfter(File("project").path)}").delete()
        createDirsAndWriteFile(dir)

        return addKtFileToProject("${dir}/${name}")
    }

    private fun KtFile.remove() {
        delete()
        File("$multiplatformProjectDirectory/${File(virtualFilePath).path.substringAfter(File("project").path)}").delete()
    }

    private fun KtFile.copyTo(dir: String): KtFile {
        createDirsAndWriteFile(dir)
        return addKtFileToProject("${dir}/${name}")
    }

    private fun KtFile.isResolvableWithJvmAnalyzer(): Boolean {
        val jvmSourceFile = copyTo(tmpJvmDirectory)
        val isResolvedWithJvmAnalyzer = jvmSourceFile.isResolvable()
        jvmSourceFile.remove()

        return isResolvedWithJvmAnalyzer
    }

    private fun KtFile.isInCommonSources(): Boolean =
        File(virtualFilePath).path.contains(File(commonMainSources).path.substringAfter(File(multiplatformProjectDirectory).path))

    private fun KtFile.isInJvmSources(): Boolean =
        File(virtualFilePath).path.contains(File(jvmMainSources).path.substringAfter(File(multiplatformProjectDirectory).path))
}
