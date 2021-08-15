/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.tests

import org.jetbrains.kotlin.idea.codeInsight.gradle.MultiplePluginVersionGradleImportingTestCase
import java.io.File

abstract class MppConverterTestCase : MultiplePluginVersionGradleImportingTestCase() {

    abstract val projectFile: File

    val virtualProjectPath: String by lazy { projectPath }

    fun importVirtualProject() = importProjectFromTestData()


    override fun testDataDirectory(): File {
        return projectFile
    }
}