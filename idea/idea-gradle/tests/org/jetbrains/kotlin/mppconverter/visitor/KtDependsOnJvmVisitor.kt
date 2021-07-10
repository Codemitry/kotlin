/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.visitor

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.refactoring.createTempCopy
import org.jetbrains.kotlin.idea.test.allKotlinFiles
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.containsError

@Deprecated("Will be removed soon. Replaced with KtResolverVisitor with jvm analyzer for all file")
class KtDependsOnJvmVisitor(val project: Project) : KtVisitor<Boolean, Unit>() {
    override fun visitKtElement(element: KtElement, data: Unit): Boolean {
        return element.acceptChildren(this, data) { it.any { it } }
    }

    override fun visitKtFile(file: KtFile, data: Unit): Boolean {
        super.visitKtFile(file, data)
        return file.acceptChildren(this, data) { it.any { it } }
    }

    override fun visitImportList(importList: KtImportList, data: Unit?): Boolean {
        // mock. import expressions are not resolvable
        return false
    }

    override fun visitTypeReference(typeReference: KtTypeReference, data: Unit?): Boolean {
        val type = typeReference.getAbbreviatedTypeOrType()
        return if (type == null || type.containsError()) {
            true  // uncomment and delete "true"
//            val jvmType = typeReference.getAbbreviatedTypeOrTypeWithJvmAnalyzer() ?: error("type of KtTypeReference is null inside jvm check") // when?
//            jvmType.isResolvable()
        } else {
            type.isNotResolvable()
        }
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression, data: Unit?): Boolean {
//        return super.visitDotQualifiedExpression(expression, data)
        // TODO how to resolve imports?
        return false
    }

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression, data: Unit?): Boolean {
        return super.visitCallableReferenceExpression(expression, data)
//        return false
    }

    override fun visitCallExpression(expression: KtCallExpression, data: Unit?): Boolean {
        if (expression.getResolvedCall()?.resultingDescriptor != null &&
            expression.getType()?.isResolvable() == true) {
            return false
        } else {
            return true  // TODO: make jvm-resolving check instead true returning
            // create this in jvm tmp
        }
    }


    override fun visitArrayAccessExpression(expression: KtArrayAccessExpression, data: Unit?): Boolean {
        // getType is null
        return super.visitArrayAccessExpression(expression, data)
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression, data: Unit?): Boolean {
        println()
//        if (expression.containingKtFile.name == "Measure.kt")
//            println("")

//        val descriptors = expression.resolveMainReferenceToDescriptors()
//
//        if (descriptors.isNotEmpty()) {
//            return false
//        } else {
//            println()
//            // mpp analyzer can't analyze the reference expression, try jvm analyzer
//        }
//        return super.visitReferenceExpression(expression, data)
        return false
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

    private fun KtElement.getResolvedCall(): ResolvedCall<out CallableDescriptor>? = getResolvedCall(analyze())

    private fun KtExpression.getType(): KotlinType? = getType(analyze())

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

@Deprecated("Will be removed soon. Replaced with KtResolverVisitor with jvm analyzer for all file")
fun KtFile.dependsOnJvm(): Boolean {
    return accept(KtDependsOnJvmVisitor(project), Unit)
}


@Deprecated("Will be removed soon. See KtResolverVisitor")
fun KtElement.dependsOnJvm(): Boolean {
    return accept(KtDependsOnJvmVisitor(project), Unit)
}