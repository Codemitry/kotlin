/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle.generator

sealed class Target(val name: String) {
    abstract val target: String

    override fun toString(): String = "$target(${if (name == "jvm" || name == "js") "" else "\"$name\""})"
}

class JvmTarget(name: String = "jvm") : Target(name) {
    override val target: String
        get() = "jvm"
}

class JsTarget(name: String = "js") : Target(name) {
    override val target: String
        get() = "js"
}