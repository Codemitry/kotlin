/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.tests.projects

import org.jetbrains.kotlin.mppconverter.MppProjectConverter
import org.jetbrains.kotlin.mppconverter.tests.MppConverterCopyProjectTestCase
import org.junit.Test

class MppDuProjectConverterTest : MppConverterCopyProjectTestCase("/Users/Dmitry.Sokolov/ideaProjects/tests/success/du") {

    @Test
    fun duTest() {
        val converter = MppProjectConverter(this)
        converter.connectToProject()

        converter.convertBuildScripts { _, _ ->
return@convertBuildScripts """
plugins {
    kotlin("multiplatform") version "1.5.10"
}

group = "me.codemitry"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}


kotlin {
    jvm()
    js()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib"))
            }
        }


        val jvmMain by getting {
            dependencies {
                implementation("com.xenomachina:kotlin-argparser:2.0.7")
            }
        }
    }
}

""".trimIndent()
        }
        converter.convert()

        converter.closeConnection()
    }
}