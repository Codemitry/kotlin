package org.jetbrains.kotlin.mppconverter

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.mppconverter.visitor.KtActualMakerVisitorVoid
import org.jetbrains.kotlin.mppconverter.visitor.KtExpectMakerVisitorVoid
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class MppFileConverter(private val ktFile: KtFile) {

    fun convert(commonSources: String, jvmSources: String) {
        val commonSourcesDir = File(commonSources)
        val jvmSourcesDir = File(jvmSources)

        if (!commonSourcesDir.isDirectory) throw IllegalArgumentException("Directory $commonSourcesDir does not exist")
        if (!jvmSourcesDir.isDirectory) throw IllegalArgumentException("Directory $jvmSourcesDir does not exist")

        if (ktFile.isJvmDependent()) {
            if (ktFile.canConvertToCommon()) {
                val commonFile = ktFile.makeCommonTargetFile()
                commonFile.createDirsAndWriteFile("${commonSources}/${commonFile.packageToRelativePath()}")

                val jvmFile = ktFile.makeJvmTargetFile()
                jvmFile.createDirsAndWriteFile("${jvmSources}/${jvmFile.packageToRelativePath()}")
            } else {
                ktFile.createDirsAndWriteFile("${jvmSources}/${ktFile.packageToRelativePath()}")
            }
        } else {
            ktFile.createDirsAndWriteFile("${commonSources}/${ktFile.packageToRelativePath()}")
        }
    }

    private fun KtFile.makeCommonTargetFile(): KtFile = (this.copy() as KtFile).apply {
        toExpect()
    }

    private fun KtFile.makeJvmTargetFile(): KtFile = (this.copy() as KtFile).apply {
        toActual()
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


    private fun PsiElement.toExpect(): PsiElement {
        when (this) {
            is KtFile -> {
                this.clearJvmDependentImports()
                declarations.filter { it.isJvmDependent() }.forEach { it.toExpect() }
            }
            else -> accept(KtExpectMakerVisitorVoid())
        }
        return this
    }


    private fun PsiElement.toActual(): PsiElement {
        when (this) {
            is KtFile -> {
                for (declaration in declarations) {
                    if (declaration.isJvmDependent()) {
                        declaration.toActual()
                    } else {
                        declaration.delete()
                    }
                }
            }
            else -> {
                accept(KtActualMakerVisitorVoid())
            }
        }

        return this
    }

}
