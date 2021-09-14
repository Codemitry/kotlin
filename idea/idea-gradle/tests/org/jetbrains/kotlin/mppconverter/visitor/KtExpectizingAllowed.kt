/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.visitor

import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.util.hasInlineModifier
import org.jetbrains.kotlin.idea.util.hasPrivateModifier
import org.jetbrains.kotlin.idea.util.ifTrue
import org.jetbrains.kotlin.lexer.KtTokens.LATEINIT_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.VALUE_KEYWORD
import org.jetbrains.kotlin.mppconverter.resolvers.acceptChildren
import org.jetbrains.kotlin.mppconverter.resolvers.isNotResolvable
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvable
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvableSignature
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.psi.psiUtil.isTopLevelKtOrJavaMember

private object KtExpectizingCheckVisitor : KtVisitor<Boolean, Unit>() {

    override fun visitKtElement(element: KtElement, data: Unit): Boolean {
        return element.acceptChildren(this, data) { it.all { it } }
    }

    override fun visitProperty(property: KtProperty, data: Unit): Boolean {
        property.typeReference?.isNotResolvable?.ifTrue { return false }
        property.typeParameterList?.isNotResolvable?.ifTrue { return false }

        if (property.hasModifier(LATEINIT_KEYWORD)) return false

        return property.resolveToDescriptorIfAny()?.type?.isResolvable(property.project) == true
    }

    override fun visitNamedFunction(function: KtNamedFunction, data: Unit): Boolean = function.isResolvableSignature()

    override fun visitTypeAlias(typeAlias: KtTypeAlias, data: Unit): Boolean = typeAlias.isResolvable

    override fun visitObjectDeclaration(dcl: KtObjectDeclaration, data: Unit): Boolean {
        dcl.getSuperTypeList()?.isNotResolvable?.ifTrue { return false }
        dcl.typeParameterList?.isNotResolvable?.ifTrue { return false }

        dcl.primaryConstructor?.isNotResolvable?.ifTrue { return false }
        return dcl.declarations.all { it.accept(KtExpectizingCheckVisitor, Unit) }
    }

    override fun visitClass(klass: KtClass, data: Unit): Boolean {
        klass.getSuperTypeList()?.isNotResolvable?.ifTrue { return false }
        klass.typeParameterList?.isNotResolvable?.ifTrue { return false }

        if (klass.isEnum() && (klass.hasPrimaryConstructor() || klass.secondaryConstructors.isNotEmpty())) return false

        // deprecated modifier inline
        if (klass.hasInlineModifier() || klass.hasModifier(VALUE_KEYWORD)) return false

        klass.primaryConstructor?.isNotResolvable?.ifTrue { return false }
        klass.secondaryConstructors.any { it.isNotResolvable }.ifTrue { return false }

        return klass.declarations.all { it.accept(KtExpectizingCheckVisitor, Unit) }
    }

    override fun visitDeclaration(dcl: KtDeclaration, data: Unit): Boolean {
        if (dcl.isPrivate()) return false
        return super.visitDeclaration(dcl, data)
    }

}

fun KtDeclaration.isExpectizingAllowed(): Boolean {
    if (!this.isTopLevelKtOrJavaMember()) return false
    if (this.isPrivate()) return false

    return accept(KtExpectizingCheckVisitor, Unit)
}

fun KtDeclaration.isExpectizingDenied(): Boolean = !isExpectizingAllowed()


@Deprecated("Use KtDeclaration.isExpectizingAllowed()")
fun KtFile.isExpectizingAllowed(): Boolean = declarations.any { !it.hasPrivateModifier() && it.isExpectizingAllowed() }

@Deprecated("Use KtDeclaration.isExpectizingDenied()")
fun KtFile.isExpectizingDenied(): Boolean = declarations.all { it.hasPrivateModifier() || it.isExpectizingDenied() }