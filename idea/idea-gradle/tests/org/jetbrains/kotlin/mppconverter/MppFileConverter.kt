package org.jetbrains.kotlin.mppconverter

import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.BindingContext
import java.io.File

class MppFileConverter(private val ktFile: KtFile) {
    private var context: BindingContext = ktFile.analyze()

    fun convert(commonSources: String, jvmSources: String) {
        val commonSourcesDir = File(commonSources)
        val jvmSourcesDir = File(jvmSources)

        if (!commonSourcesDir.isDirectory) throw IllegalArgumentException("Directory $commonSourcesDir does not exist")
        if (!jvmSourcesDir.isDirectory) throw IllegalArgumentException("Directory $jvmSourcesDir does not exist")

        if (ktFile.isJvmDependent()) {
            if (ktFile.canConvertToCommon()) {
                val commonFile = makeCommonTargetFile()
                commonFile.clearJvmDependentImports()
                commonFile.createDirsAndWriteFile("${commonSources}/${commonFile.packageToRelativePath()}")

                val jvmFile = makeJvmTargetFile()
                jvmFile.createDirsAndWriteFile("${jvmSources}/${jvmFile.packageToRelativePath()}")
            } else {
                ktFile.createDirsAndWriteFile("${jvmSources}/${ktFile.packageToRelativePath()}")
            }
        } else {
            ktFile.createDirsAndWriteFile("${commonSources}/${ktFile.packageToRelativePath()}")
        }
    }

    private fun makeCommonTargetFile(): KtFile {
        val copy = ktFile.copy() as KtFile
        context = copy.analyzeWithContent()
        copy.toExpect()
        return copy
    }


    private fun makeJvmTargetFile(): KtFile {
        val copy = ktFile.copy() as KtFile
        context = copy.analyzeWithContent()
        copy.toActual()

        return copy

    }


    private fun KtFile.packageToRelativePath(): String {
        return if (ktFile.packageDirective?.isRoot == true)
            ""
        else
            ktFile.packageFqName.asString().replace(".", "/")
    }

    private fun KtFile.createDirsAndWriteFile(to: String) {
        File(to, this.name).also {
            it.parentFile.mkdirs()
            it.createNewFile()
            it.writeText(this.text)
        }
    }

}
