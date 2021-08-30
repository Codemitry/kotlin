/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.tests.projects

import org.jetbrains.kotlin.mppconverter.MppProjectConverter
import org.jetbrains.kotlin.mppconverter.tests.MppConverterCopyProjectTestCase
import org.junit.Test

class MppBirthdaykataProjectConverterTest : MppConverterCopyProjectTestCase("/Users/Dmitry.Sokolov/ideaProjects/tests/success/birthdaykata") {

    @Test
    fun test() {
        val converter = MppProjectConverter(this)
        converter.connectToProject()

        converter.convertBuildScripts { _, _ ->
return@convertBuildScripts """
plugins {
    id 'org.jetbrains.kotlin.multiplatform' version '1.5.10'
}


group 'com.ubertob'
version '1.0-SNAPSHOT'

sourceCompatibility = 1.8

repositories {
    mavenCentral()
}


wrapper {
    gradleVersion = "5.3.1"
}


kotlin {
    jvm()
    js()

    sourceSets {
        commonMain {
            dependencies {
//                implementation(kotlin("stdlib"))
               
            }
        }


//        jvmMain {
//            dependencies {
//                implementation "com.google.code.gson:gson:2.8.2"
//                implementation "org.slf4j:slf4j-log4j12:1.7.12"
//            }
//        }
    }
}

""".trimIndent()
        }
        converter.convert()

        converter.closeConnection()
    }
}