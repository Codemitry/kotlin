/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.visitor

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.refactoring.createTempCopy
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.containsError

class KtDependsOnJvmVisitor(val project: Project) : KtVisitor<Boolean, Unit>() {
    override fun visitKtElement(element: KtElement, data: Unit): Boolean {
        return element.acceptChildren(this, data) { it.any { it } }
    }

    override fun visitKtFile(file: KtFile, data: Unit): Boolean {
        super.visitKtFile(file, data)
        return file.acceptChildren(this, data) { it.any { it } }
    }

    override fun visitTypeReference(typeReference: KtTypeReference, data: Unit?): Boolean {
        val type = typeReference.getAbbreviatedTypeOrType()
        return if (type == null || type.containsError()) {
            val jvmType = typeReference.getAbbreviatedTypeOrTypeWithJvmAnalyzer() ?: error("type of KtTypeReference is null inside jvm check") // when?
            jvmType.isResolvable()
        } else {
            type.isNotResolvable()
        }
    }

    private fun KotlinType.isResolvable(): Boolean {
        if (this.containsError()) return false
        val descriptor = this.constructor.declarationDescriptor ?: error("declaration descriptor of constructor of KotlinType is null")

        return true
    }

    private fun KotlinType.isNotResolvable(): Boolean = !isResolvable()

    private fun KtTypeReference.getAbbreviatedTypeOrTypeWithJvmAnalyzer(): KotlinType? {
        // create property with this type reference and analyze it
        val jvmFile = project.createJvmTmpCopyKtFile(containingKtFile.packageDirective?.text, containingKtFile.importList?.text, "val a: $text")
        val typeReference = (jvmFile.children.last() as KtProperty).typeReference!!
        return typeReference.getAbbreviatedTypeOrType()
    }

    private fun KtTypeReference.getAbbreviatedTypeOrType(): KotlinType? = getAbbreviatedTypeOrType(analyze())

//    private fun KtProperty.getTypeWithJvmAnalyzer(bindingContext: BindingContext = analyze()): KotlinType? {
//        val tmpFile = project.createJvmTmpCopyKtFile(
//            "${containingKtFile.packageDirective?.text ?: ""}\n\n${containingKtFile.importList?.text ?: ""}\n\n${this.text}"
//        )
//        val jvmProperty = tmpFile.children.last() as KtProperty
//        return jvmProperty.getType(bindingContext)
//    }


    private val Project.jvmTmpKtFile: KtFile
        get() = allKotlinFiles().find { it.virtualFilePath.contains("jvmMain/kotlin/__jvm/tmp.kt") }
            ?: error("jvm tmp file does not found at specified path!")

    private fun Project.createJvmTmpCopyKtFile(text: String): KtFile {
        return project.jvmTmpKtFile.createTempCopy(text)
    }

    private fun Project.createJvmTmpCopyKtFile(pack: String? = null, imports: String? = null, text: String): KtFile {
        return createJvmTmpCopyKtFile(
            "$pack${pack?.let { "\n\n" }}" +
                    "$imports${imports?.let { "\n\n" }}" +
                    text
        )
    }

    private fun <R, D> KtElement.acceptChildren(visitor: KtVisitor<R, D>, data: D, returnCondition: (List<R>) -> R): R {
        return returnCondition(children.mapNotNull { (it as? KtElement)?.accept(visitor, data) })
    }

}

fun KtFile.dependsOnJvm(): Boolean {
    return accept(KtDependsOnJvmVisitor(project), Unit)
}

