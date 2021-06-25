/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

fun rawDependenciesToList(lines: List<String>, configuration: String): List<String> {
    val dependencies = mutableListOf<String>()

    val firstDepLineIdx = lines.indexOfFirst { it.startsWith(configuration) } + 1

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