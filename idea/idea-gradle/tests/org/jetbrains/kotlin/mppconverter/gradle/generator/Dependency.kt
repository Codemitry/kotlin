/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle.generator


sealed class Dependency(val depName: String, val configuration: String)

class ExternalDependency(val artifact: String, configuration: String) : Dependency(artifact, configuration)

class ModuleDependency(val moduleNotation: String, configuration: String) : Dependency(moduleNotation, configuration)
