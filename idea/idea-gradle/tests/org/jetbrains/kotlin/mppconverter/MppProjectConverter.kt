/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.debugger.readAction
import org.jetbrains.kotlin.mppconverter.gradle.BuildGradleFileForMultiplatformProjectConfigurator
import org.jetbrains.kotlin.mppconverter.gradle.GradleProjectHelper
import org.jetbrains.kotlin.platform.js.JsPlatforms
import org.jetbrains.kotlin.platform.jvm.JdkPlatform
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Test
import java.io.File

// наследование только для того, чтобы application у ApplicationManager был не null
class MppProjectConverter : MultiplePluginVersionGradleImportingTestCase() {

    @Test
    fun main() {

        assertJvmProjectStructure()

        GradleProjectHelper(jvmProjectDirectory).apply {
            connectToProject()

            loadDependencies { dependencies ->
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

            closeConnection()
        }

    }

    /*
    way to create ktfile in runtime:
    val file = File("C:\\Users\\Codemitry\\Desktop\\test_mpp\\src\\jvmMain\\kotlin\\din\\Din.kt");
    val text = configureKotlinVersionAndProperties(FileUtil.loadFile(file, true));
    val vf = createProjectSubFile(file.path.substringAfter(testDataDirectory().path + File.separator), text);
    vf.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, file.absolutePath); val kt = (vf.toPsiFile(project) as KtFile);
    kt.createTempCopy(file.readText())
     */


    var jvmProjectDirectory: String = "W:\\Kotlin\\Projects\\du"

    lateinit var jvmFiles: List<VirtualFile>

    var multiplatformProjectDirectory: String = "C:\\Users\\Codemitry\\Desktop\\${File(jvmProjectDirectory).name}_mpp"
    var repositories: List<String>? = listOf("{{kts_kotlin_plugin_repositories}}")
    var dependencies: List<String>? = null


    private fun createStructure() {
        File(multiplatformProjectDirectory).mkdirs()

        val src = File(multiplatformProjectDirectory, "src").apply { mkdir() }

        val buildGradle = File(multiplatformProjectDirectory, "build.gradle.kts").apply {
            createNewFile()

            val buildGradleBuilder = BuildGradleFileForMultiplatformProjectConfigurator.Builder()
            repositories?.let { buildGradleBuilder.repositories(it) }
            dependencies?.let {
                val deps = it.map { dependency ->
                    BuildGradleFileForMultiplatformProjectConfigurator.Dependency(dependency, "implementation")
                }

                buildGradleBuilder.commonMainDependencies(deps)
                buildGradleBuilder.jvmMainDependencies(deps)
            }

            writeText(
                buildGradleBuilder.build().text
            )
        }

        val commonMain = File(src, "commonMain").apply { mkdir() }
        val jvmMain = File(src, "jvmMain").apply { mkdir() }
        val jsMain = File(src, "jsMain").apply { mkdir() }

        File(commonMain, "kotlin/__jvm").apply { mkdirs() }
        File(jvmMain, "kotlin/__jvm").apply {
            mkdirs()
            // create jvm temporary file to create copy and analyze with jvm analyzer
            File(this, "tmp.kt").apply { createNewFile() }
        }

        File(jvmMain, "kotlin").mkdir()
        File(jsMain, "kotlin").mkdir()
    }

    private fun setupProject() {
        createStructure()

        File(jvmProjectDirectory, "src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList().forEach { curJvmFile ->
            File(multiplatformProjectDirectory, "src/commonMain/kotlin/__jvm/${curJvmFile.name}").apply {
                createNewFile()
                // delete all '\r' because in intellij idea's guide said that strings delimiters are only '\n'
                writeText(curJvmFile.readText().replace("\r", ""))
            }

            // setup mpp project
            val allKtFiles = importProjectFromTestData()
            jvmFiles = allKtFiles.filter { it.extension == "kt" && it.path.contains("commonMain/kotlin/__jvm/") }
        }
    }


    private fun processFiles() {
        jvmFiles.forEach { jvmFile ->
            val ktFile = jvmFile.toPsiFile(project) as KtFile

            val converter = MppFileConverter(ktFile)
            converter.convert(
                "${multiplatformProjectDirectory}/src/commonMain/kotlin/",
                "${multiplatformProjectDirectory}/src/jvmMain/kotlin/"
            )
            File("${multiplatformProjectDirectory}/src/commonMain/kotlin/__jvm", jvmFile.name).delete()
        }
        File("${multiplatformProjectDirectory}/src/commonMain/kotlin/__jvm").delete()
        File("${multiplatformProjectDirectory}/src/jvmMain/kotlin/__jvm").delete()
    }


    private fun assertJvmProjectStructure() {
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
}
