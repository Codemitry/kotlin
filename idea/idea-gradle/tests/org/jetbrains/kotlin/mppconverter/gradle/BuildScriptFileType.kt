/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

import java.io.File

sealed class BuildScriptFileType {

    object KotlinScript : BuildScriptFileType()
    object GroovyScript : BuildScriptFileType()

    fun isKotlinScript() = this is KotlinScript
    fun isGroovyScript() = this is GroovyScript
}

fun buildScriptFileTypeFor(buildScript: File): BuildScriptFileType = when (buildScript.extension) {
    "kts" -> BuildScriptFileType.KotlinScript
    "gradle" -> BuildScriptFileType.GroovyScript
    else -> throw IllegalStateException("Unknown type of build gradle script: $buildScript")
}