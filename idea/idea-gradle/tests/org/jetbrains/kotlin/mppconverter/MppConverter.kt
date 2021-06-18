/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.gradle.tooling.GradleConnectionException
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ResultHandler
import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.debugger.readAction
import org.jetbrains.kotlin.psi.KtFile
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

// наследование только для того, чтобы application у ApplicationManager был не null
class MppConverter : MultiplePluginVersionGradleImportingTestCase() {

    @Test
    fun main() {

        assertJvmProjectStructure()

        loadConfigurationDependencies { dependencies ->
            this.dependencies = dependencies
            setupProject()
        }
    }


    var jvmProjectDirectory: String = "W:\\Kotlin\\Projects\\du"


    lateinit var jvmFiles: List<VirtualFile>


    var multiplatformProjectDirectory: String = "C:\\Users\\Codemitry\\Desktop\\${File(jvmProjectDirectory).name}_mpp"
    var repositories: List<String>? = listOf("{{kts_kotlin_plugin_repositories}}")
    var dependencies: List<String>? = null

    lateinit var mppProject: Project
        private set


    private fun createStructure() {
        File(multiplatformProjectDirectory).mkdirs()

        val src = File(multiplatformProjectDirectory, "src").apply { mkdir() }

        val buildGradle = File(multiplatformProjectDirectory, "build.gradle.kts").apply {
            createNewFile()
            writeText(generateBuildGradleText(repositories, dependencies))
        }

        val commonMain = File(src, "commonMain").apply { mkdir() }
        val jvmMain = File(src, "jvmMain").apply { mkdir() }

        File(commonMain, "kotlin/__jvm").apply {
            mkdirs()
        }

        File(jvmMain, "kotlin").mkdir()
    }

    private fun setupProject() {
        createStructure()

        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().readAction {

                File(jvmProjectDirectory, "src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList().forEach { curJvmFile ->
                    File(multiplatformProjectDirectory, "src/commonMain/kotlin/__jvm/${curJvmFile.name}").apply {
                        createNewFile()
                        // delete all '\r' because in intellij idea's guide said that strings delimiters are only '\n'
                        writeText(curJvmFile.readText().replace("\r", ""))
                    }
                }
                
//              setup mpp project
                mppProject = myTestFixture.project
                jvmFiles = importProjectFromTestData().filter { it.extension == "kt" && it.parent.name == "__jvm" }

                processFiles()

            }
        }
    }


    private fun processFiles() {
        WriteCommandAction.runWriteCommandAction(mppProject) {

            jvmFiles.forEach { jvmFile ->
                val ktFile = jvmFile.toPsiFile(mppProject) as KtFile

                val converter = MppFileConverter(ktFile)
                converter.convert(
                    "${multiplatformProjectDirectory}/src/commonMain/kotlin/",
                    "${multiplatformProjectDirectory}/src/jvmMain/kotlin/"
                )
                File("${multiplatformProjectDirectory}/src/commonMain/kotlin/__jvm", jvmFile.name).delete()
            }
            File("${multiplatformProjectDirectory}/src/commonMain/kotlin/__jvm").delete()
        }
    }


    private fun assertJvmProjectStructure() {
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


    private fun rawDependenciesToList(lines: List<String>): List<String> {
        val dependencies = mutableListOf<String>()

        val firstDepLineIdx = lines.indexOfFirst { it.startsWith("implementation") } + 1

        if (lines[firstDepLineIdx].contains("No dependencies", true)) return dependencies

        var lineIdx = firstDepLineIdx
        while (!lines[lineIdx].startsWith("\\---")) {
            if (lines[lineIdx].startsWith("+---"))
                dependencies.add(lines[lineIdx].substringAfter("+--- ").split(" ")[0])
            lineIdx++
        }
        dependencies.add(lines[lineIdx].substringAfter("\\--- ").split(" ")[0])

        return dependencies
    }

    private fun loadConfigurationDependencies(onDependenciesLoaded: (List<String>) -> Unit) {
        val connector = GradleConnector.newConnector()

        connector.forProjectDirectory(File(jvmProjectDirectory))
        val connection = connector.connect()

        val build = connection.newBuild()

        val outputStream = ByteArrayOutputStream()
        build.setStandardOutput(outputStream)

        build.withArguments("dependencies", "--configuration", "implementation")
        build.run(object : ResultHandler<Void> {
            override fun onComplete(p0: Void?) {
                val lines = outputStream.toString("UTF-8").lines()
                onDependenciesLoaded(rawDependenciesToList(lines))

            }

            override fun onFailure(e: GradleConnectionException?) {
                println("failure on get dependencies for project: $e")

            }
        })
        connection.close()
    }


    // dir with project
    // required by TestCase
    override fun testDataDirectory(): File {
        return File(multiplatformProjectDirectory)
    }
}


fun generateBuildGradleText(
    repositories: List<String>? = null,
    dependencies: List<String>? = null,
    groupId: String? = null,
    version: String? = null,
): String {
    return """
plugins {
    kotlin("multiplatform") version "1.5.10"
}
${if (groupId != null) "group = \"${groupId}\"" else ""}
${if (version != null) "version = \"${version}\"" else ""}

repositories {
${repositories?.joinToString(System.lineSeparator()) { "\t$it" } ?: ""}
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "1.8"
        }
        testRuns["test"].executionTask.configure {
            useJUnit()
        }
    }
    sourceSets {
        val commonMain by getting {
            dependencies {
${dependencies?.joinToString(System.lineSeparator()) { "\t\t\t\timplementation(\"${it}\")" } ?: ""}
            }
        }
        val commonTest by getting {
        }
        val jvmMain by getting {}
        val jvmTest by getting {
            dependencies {
// implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
            }
        }
    }
}

    """.trimIndent()
}