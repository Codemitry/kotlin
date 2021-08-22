/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.visitor

import org.jetbrains.kotlin.idea.util.hasPrivateModifier
import org.jetbrains.kotlin.idea.util.ifTrue
import org.jetbrains.kotlin.lexer.KtTokens.PRIVATE_KEYWORD
import org.jetbrains.kotlin.mppconverter.resolvers.*
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember

private object KtExpectizingCheckVisitor : KtVisitor<Boolean, Unit>() {

    override fun visitKtElement(element: KtElement, data: Unit): Boolean {
        return element.acceptChildren(this, data) { it.all { it } }
    }

    override fun visitProperty(property: KtProperty, data: Unit): Boolean = property.isResolvableType()

    override fun visitNamedFunction(function: KtNamedFunction, data: Unit): Boolean = function.isResolvableSignature()

    override fun visitTypeAlias(typeAlias: KtTypeAlias, data: Unit): Boolean = typeAlias.isResolvable()

    override fun visitObjectDeclaration(dcl: KtObjectDeclaration, data: Unit): Boolean {
        dcl.primaryConstructor?.isNotResolvable()?.ifTrue { return false }
        return dcl.declarations.all { it.accept(KtExpectizingCheckVisitor, Unit) }
    }

    override fun visitClass(klass: KtClass, data: Unit): Boolean {
        klass.getSuperTypeList()?.isNotResolvable()?.ifTrue { return false }

        if (klass.isEnum() && (klass.hasPrimaryConstructor() || klass.secondaryConstructors.isNotEmpty())) return false

        klass.primaryConstructor?.isNotResolvable()?.ifTrue { return false }
        klass.secondaryConstructors.any { it.isNotResolvable() }.ifTrue { return false }

        return klass.declarations.all { it.accept(KtExpectizingCheckVisitor, Unit) }
    }

    override fun visitDeclaration(dcl: KtDeclaration, data: Unit): Boolean {
        if (dcl.hasModifier(PRIVATE_KEYWORD)) return false
        return super.visitDeclaration(dcl, data)
    }

}

fun KtDeclaration.isExpectizingAllowed(): Boolean {
    if (!this.isTopLevelKtOrJavaMember()) return false

    return accept(KtExpectizingCheckVisitor, Unit)
}

fun KtDeclaration.isExpectizingDenied(): Boolean = !isExpectizingAllowed()

fun KtFile.isExpectizingAllowed(): Boolean = declarations.any { !it.hasPrivateModifier() && it.isExpectizingAllowed() }
fun KtFile.isExpectizingDenied(): Boolean = declarations.all { it.hasPrivateModifier() || it.isExpectizingDenied() }