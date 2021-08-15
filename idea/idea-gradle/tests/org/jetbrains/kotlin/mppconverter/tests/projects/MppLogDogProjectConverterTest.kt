/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.tests.projects

import org.jetbrains.kotlin.mppconverter.MppProjectConverter
import org.jetbrains.kotlin.mppconverter.tests.MppConverterCopyProjectTestCase
import org.junit.Test

class MppLogDogProjectConverterTest : MppConverterCopyProjectTestCase("/Users/Dmitry.Sokolov/ideaProjects/tests/success/LogDog") {

    @Test
    fun logDogTest() {
        val converter = MppProjectConverter(this)
        converter.connectToProject()

        converter.convertBuildScripts { _, _ ->
return@convertBuildScripts """
buildscript {
    ext.kotlin_version = '1.5.10'

    repositories {
        mavenCentral()
    }
    dependencies {
        classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:${"\$"}kotlin_version"
    }
}


plugins {
    id 'org.jetbrains.kotlin.multiplatform' version "${"\$"}kotlin_version"
}


group 'logdog'
version '2.0-SNAPSHOT'


sourceCompatibility = 1.8

repositories {
    mavenCentral()
}


kotlin {
    jvm()
    js()

    sourceSets {
        commonMain {
            dependencies {
                implementation(kotlin("stdlib"))
               
            }
        }


        jvmMain {
            dependencies {
                implementation "com.google.code.gson:gson:2.8.2"
                implementation "org.slf4j:slf4j-log4j12:1.7.12"
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