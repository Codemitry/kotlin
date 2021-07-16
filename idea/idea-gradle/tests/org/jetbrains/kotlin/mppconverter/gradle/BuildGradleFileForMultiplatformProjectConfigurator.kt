/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.gradle

@Deprecated("Always generate kts", ReplaceWith("Use org.jetbrains.kotlin.mppconverter.gradle.generator.BuildScriptGenerator"))
class BuildGradleFileForMultiplatformProjectConfigurator constructor(
    val group: String?,
    val name: String?,
    val version: String?,

    val repositoriesSection: String?,
    val plugins: List<String>?,

    val commonMainDependencies: List<Dependency>?,
    val commonTestDependencies: List<Dependency>?,

    val jvmMainDependencies: List<Dependency>?,
    val jvmTestDependencies: List<Dependency>?,
) {

    private constructor(builder: Builder) : this(
        builder.group,
        builder.name,
        builder.version,
        builder.repositoriesSection,
        builder.plugins,
        builder.commonMainDependencies,
        builder.commonTestDependencies,
        builder.jvmMainDependencies,
        builder.jvmTestDependencies
    )

    class Builder {
        var group: String? = null
        var name: String? = null
        var version: String? = null

        var plugins: List<String>? = null
        var repositoriesSection: String? = null

        var commonMainDependencies: List<Dependency>? = null
        var commonTestDependencies: List<Dependency>? = null

        var jvmMainDependencies: List<Dependency>? = null
        var jvmTestDependencies: List<Dependency>? = null

        fun group(group: String) = apply { this.group = group }
        fun name(name: String) = apply { this.name = name }
        fun version(version: String) = apply { this.version = version }

        fun plugins(plugins: List<String>) = apply { this.plugins = plugins }
        fun repositoriesSection(repositoriesSection: String) = apply { this.repositoriesSection = repositoriesSection }

        fun commonMainDependencies(commonMainDependencies: List<Dependency>) = apply { this.commonMainDependencies = commonMainDependencies }
        fun commonTestDependencies(commonTestDependencies: List<Dependency>) = apply { this.commonTestDependencies = commonTestDependencies }

        fun jvmMainDependencies(jvmMainDependencies: List<Dependency>) = apply { this.jvmMainDependencies = jvmMainDependencies }
        fun jvmTestDependencies(jvmTestDependencies: List<Dependency>) = apply { this.jvmTestDependencies = jvmTestDependencies }

//        fun jsMainDependencies(jsMainDependencies: List<Dependency>) = apply { this.jsMainDependencies = jsMainDependencies }
//        fun jsTestDependencies(jsTestDependencies: List<Dependency>) = apply { this.jsTestDependencies = jsTestDependencies }
        
        fun build() = BuildGradleFileForMultiplatformProjectConfigurator(this)

    }

    class Dependency(val name: String, val configuration: String)

    override fun toString(): String {
        return text
    }

val text = """
plugins {
${plugins?.joinToString(System.lineSeparator()) { "\t$it" } ?: ""}
    kotlin("multiplatform") version "1.5.10"
}
${if (group != null) "group = \"${group}\"" else ""}
${if (name != null) "name = \"${name}\"" else ""}
${if (version != null) "version = \"${version}\"" else ""}

$repositoriesSection

kotlin {
    jvm {
    }
    
    js { 
        browser()  // or nodejs()
    }
    
    
    targets.all {
        compilations.all {
            kotlinOptions {
                allWarningsAsErrors = true
            }
        }
    }
    
    sourceSets {
        val commonMain by getting {
            dependencies {
${commonMainDependencies?.joinToString(System.lineSeparator()) { "\t\t\t\t${it.configuration}(\"${it.name}\")" } ?: ""}
            }
        }
        val commonTest by getting {
            dependencies { 
${commonTestDependencies?.joinToString(System.lineSeparator()) { "\t\t\t\t${it.configuration}(\"${it.name}\")" } ?: ""}
            }
        }
        
        val jvmMain by getting {
            dependencies { 
${jvmMainDependencies?.joinToString(System.lineSeparator()) { "\t\t\t\t${it.configuration}(\"${it.name}\")" } ?: ""}
            }
        }
        val jvmTest by getting {
            dependencies {
${jvmTestDependencies?.joinToString(System.lineSeparator()) { "\t\t\t\t${it.configuration}(\"${it.name}\")" } ?: ""}
            }
        }
        
        val jsMain by getting {
            dependencies { 
            }
        }
        val jsTest by getting {
            dependencies {
            }
        }
    }
}

    """.trimIndent()

}