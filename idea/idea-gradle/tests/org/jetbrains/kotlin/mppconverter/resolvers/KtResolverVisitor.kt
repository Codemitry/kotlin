/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.resolvers

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToCall
import org.jetbrains.kotlin.idea.util.ifFalse
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.containsError

class KtResolverVisitor(val project: Project) : KtVisitor<Boolean, Unit>() {
    // each visit-function returns "true" when element is resolvable with the analyzer, and "false", when it can't resolve with current analyzer
    override fun visitKtElement(element: KtElement, data: Unit): Boolean {
        return element.acceptChildren(this, data) { it.all { it } }
    }

    override fun visitKtFile(file: KtFile, data: Unit): Boolean {
        super.visitKtFile(file, data)
        return file.acceptChildren(this, data) { it.all { it } }
    }

    override fun visitImportList(importList: KtImportList, data: Unit?): Boolean {
        // mock. import expressions should not be analyzed.
        // Also: Dot qualified expressions in imports have not types
        return true
    }

    override fun visitPackageDirective(directive: KtPackageDirective, data: Unit?): Boolean {
        // mock. package directive should not be analyzed
        // Also: package as expression has not descriptor or a type
        return true
    }

    override fun visitSimpleNameExpression(expression: KtSimpleNameExpression, data: Unit?): Boolean {
        // mock. operation expression?
        return true
    }

    override fun visitTypeReference(typeReference: KtTypeReference, data: Unit?): Boolean {
        val type = typeReference.getAbbreviatedTypeOrType() ?: return false
        return type.isResolvable()
    }

    override fun visitDotQualifiedExpression(expression: KtDotQualifiedExpression, data: Unit?): Boolean {
        val resolvedCall = expression.getResolvedCall() ?: return false
        val type = expression.getType() ?: return false
        return type.isResolvable()
    }

    override fun visitCallableReferenceExpression(expression: KtCallableReferenceExpression, data: Unit?): Boolean {
        val resolvedCall = expression.getResolvedCall() ?: return false
        val type = expression.getType() ?: return false
        return type.isResolvable()
    }

    override fun visitCallExpression(expression: KtCallExpression, data: Unit?): Boolean {
        val resolvedCall = expression.getResolvedCall() ?: return false
        resolvedCall.call.valueArgumentList?.isResolvable()?.ifFalse { return false }
        val type = expression.getType() ?: return false
        return type.isResolvable()
    }


    override fun visitArrayAccessExpression(expression: KtArrayAccessExpression, data: Unit?): Boolean {
        // getType of expression is null. => used expression.arrayExpression
        val resolvedCall = expression.resolveToCall() ?: return false
        val type = expression.arrayExpression?.getType() ?: return false
        return type.isResolvable()
    }

    override fun visitReferenceExpression(expression: KtReferenceExpression, data: Unit?): Boolean {
        val resolvedCall = expression.getResolvedCall() ?: return false
        val type = expression.getType() ?: return false
        return type.isResolvable()
    }


    private fun KtTypeReference.getAbbreviatedTypeOrType(): KotlinType? = getAbbreviatedTypeOrType(analyze())

    private fun KtElement.getResolvedCall(): ResolvedCall<out CallableDescriptor>? = getResolvedCall(analyze())

    private fun KtExpression.getType(): KotlinType? = getType(analyze())


    private fun <R, D> KtElement.acceptChildren(visitor: KtVisitor<R, D>, data: D, returnCondition: (List<R>) -> R): R {
        return returnCondition(children.mapNotNull { (it as? KtElement)?.accept(visitor, data) })
    }

}

fun KotlinType.isResolvable(): Boolean {
    if (this.containsError()) return false
    val descriptor = this.constructor.declarationDescriptor ?: error("declaration descriptor of constructor of KotlinType is null")

    return true
}

fun KtFile.isResolvable(): Boolean {
    return accept(KtResolverVisitor(project), Unit)
}

fun KtFile.isNotResolvable(): Boolean = !isResolvable()


fun KtElement.isResolvable(): Boolean {
    return accept(KtResolverVisitor(project), Unit)
}

fun KtElement.isNotResolvable(): Boolean = !isResolvable()