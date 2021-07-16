/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle.parser

interface BuildScriptParser {
    val buildScriptPath: String

    /**
     * return section with repositories or null if it is not declared
     */
    fun getRepositoriesSection(): String?
    fun getRepositoriesSectionInside(): String?
}