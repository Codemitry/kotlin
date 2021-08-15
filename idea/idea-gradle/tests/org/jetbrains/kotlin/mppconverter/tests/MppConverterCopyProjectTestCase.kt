/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.tests

import java.io.File

val testsRoot = File("/Users/Dmitry.Sokolov/ideaProjects/testsResults")

abstract class MppConverterCopyProjectTestCase(val copiedProjectPath: String) : MppConverterTestCase() {
    val copiedProjectFile = File(copiedProjectPath)

    override val projectFile = copiedProjectFile.copyToTestsRoot()

    private val File.testDirName: String
        get() = "${this.name}_mpp"

    private val File.testDir: File
        get() = File(testsRoot, testDirName)

    private fun File.copyToTestsRoot(): File = testDir.apply {
        deleteRecursively()
        this@copyToTestsRoot.copyRecursively(this)
    }
}