/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

import org.jetbrains.kotlin.mppconverter.gradle.generator.Dependency
import org.jetbrains.kotlin.mppconverter.gradle.generator.ExternalDependency
import org.jetbrains.kotlin.mppconverter.gradle.generator.ModuleDependency

fun rawDependenciesToList(lines: List<String>, configuration: String): List<Dependency> {
    val dependencies = mutableListOf<Dependency>()

    val firstDepLineIdx = lines.indexOfFirst { it.startsWith(configuration) } + 1

    if (lines[firstDepLineIdx].contains("No dependencies", true)) return dependencies

    var lineIdx = firstDepLineIdx
    while (!lines[lineIdx].startsWith("\\---")) {
        if (lines[lineIdx].startsWith("+---"))
            dependencies.add(lines[lineIdx].substringAfter("+---").dependency(configuration))
        lineIdx++
    }
    dependencies.add(lines[lineIdx].substringAfter("\\---").dependency(configuration))

    return dependencies
}

private fun String.dependency(configuration: String): Dependency {
    val firstWord = firstWord()
    return if (firstWord == "project")
        ModuleDependency(substringAfter(firstWord).firstWord(), configuration)
    else
        ExternalDependency(firstWord, configuration)
}

private fun String.firstWord(): String {
    val trimmed = trim()

    var spaceIdx = 0
    while (!trimmed[spaceIdx].isWhitespace()) spaceIdx++

    return trimmed.substring(0 until spaceIdx)
}