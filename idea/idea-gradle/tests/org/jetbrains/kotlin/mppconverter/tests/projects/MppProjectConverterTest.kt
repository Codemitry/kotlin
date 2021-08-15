/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.tests.projects

import org.jetbrains.kotlin.mppconverter.MppProjectConverter
import org.jetbrains.kotlin.mppconverter.tests.MppConverterCopyProjectTestCase
import org.junit.Test

private const val testedProject = "/Users/Dmitry.Sokolov/ideaProjects/tests/AvatarImageGenerator"

class MppProjectConverterTest : MppConverterCopyProjectTestCase(testedProject) {

    @Test
    fun test() {
        val converter = MppProjectConverter(this)
        converter.connectToProject()

        converter.convertBuildScripts { module, buildScript ->
            return@convertBuildScripts """
plugins {
    id 'application'
    id 'org.jetbrains.kotlin.multiplatform' version '1.4.21'
}


kotlin {
    jvm()
    js()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib"

            }
        }

        jvmMain {
            dependencies {
                implementation "com.github.ajalt.clikt:clikt:3.1.0"
                implementation "com.github.ajalt:mordant:1.2.1"
                implementation "com.jakewharton.picnic:picnic:0.5.0"
                implementation 'com.github.kotlin-inquirer:kotlin-inquirer:v0.0.2-alpha'
            }
        }
    }
}
            """.trimIndent()
        }
        converter.convert()

        converter.closeConnection()
    }

    @Test
    fun morseTest() {
        val converter = MppProjectConverter(this)
        converter.connectToProject()

        converter.convertBuildScripts { _, _ ->
            return@convertBuildScripts """
plugins {
    id 'application'
    id 'org.jetbrains.kotlin.multiplatform' version '1.4.21'
}

mainClassName = 'com.ch8n.morse.MainKt'

group 'org.example'
version '1.0-SNAPSHOT'

repositories {
    mavenCentral()
}

allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}

//dependencies {
//    implementation "org.jetbrains.kotlin:kotlin-stdlib"
//    implementation "com.github.ajalt.clikt:clikt:3.1.0"
//    implementation "com.github.ajalt:mordant:1.2.1"
//    implementation "com.jakewharton.picnic:picnic:0.5.0"
//    implementation 'com.github.kotlin-inquirer:kotlin-inquirer:v0.0.2-alpha'
//}



kotlin {
    jvm()
    js()
    
    sourceSets {
        commonMain {
            dependencies {
                implementation "org.jetbrains.kotlin:kotlin-stdlib"

            }
        }

        jvmMain {
            dependencies {
                implementation "com.github.ajalt.clikt:clikt:3.1.0"
                implementation "com.github.ajalt:mordant:1.2.1"
                implementation "com.jakewharton.picnic:picnic:0.5.0"
                implementation 'com.github.kotlin-inquirer:kotlin-inquirer:v0.0.2-alpha'
            }
        }
    }
}
            """.trimIndent()
        }
        converter.convert()

        converter.closeConnection()
    }

    @Test
    fun jvmMultiProjectBuildtest() {
        val converter = MppProjectConverter(this)
        converter.connectToProject()

        converter.convertBuildScripts { module, _ ->
            return@convertBuildScripts when (module.name) {
                "mainModule" -> { """
plugins {
    kotlin("multiplatform") version "1.5.10"
}

kotlin {
    jvm()
    js()
}

                """.trimIndent()
                }
                "jvm" -> { """
plugins {
    kotlin("multiplatform") version "1.5.10"
}

kotlin {
    jvm()
    js()
     
    sourceSets {
        val commonMain by getting {
            dependencies{ 
                implementation(project(":mainModule"))
            }
        }
    }
}
                """.trimIndent()

                }

                "jvm_inside" -> { """
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js()
     
    sourceSets {
        val commonMain by getting {
            dependencies{ 
                implementation(project(":mainModule"))
            }
        }
    }
}
                """.trimIndent()
                }
                "jvm_twise" -> {"""
plugins {
    kotlin("multiplatform")
}

kotlin {
    jvm()
    js()
}
                """.trimIndent()
                }
                else -> error("unknown module: ${module.name}")
            }
        }
        converter.convert()

        converter.closeConnection()
    }
}