/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.typespecifiyng

import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.mppconverter.getDeepJetTypeFqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
import org.jetbrains.kotlin.types.KotlinType

object KtExplicitTypeSpecifierVisitor : KtTreeVisitorVoid() {

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        if (property.typeReference != null) return

        if (property.delegateExpressionOrInitializer is KtObjectLiteralExpression)
            return
        if (property.getter?.initializer is KtObjectLiteralExpression)
            return

        val typeReference = property.resolveToDescriptorIfAny()?.type?.let { KtPsiFactory(property).createFqType(it) }
        property.typeReference = typeReference
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        if (function.typeReference != null) return

        if (function.initializer is KtObjectLiteralExpression)
            return

        val typeReference = function.resolveToDescriptorIfAny()?.returnType?.let { KtPsiFactory(function).createFqType(it) }
        function.typeReference = typeReference
    }


    override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression) {
        super.visitObjectLiteralExpression(expression)

        var property: KtProperty? = null
        var function: KtNamedFunction? = null

        when (expression.context) {
            is KtProperty -> {
                property = expression.context as KtProperty
            }
            is KtPropertyAccessor -> {
                property = (expression.context as KtPropertyAccessor).property
            }
            is KtPropertyDelegate -> {
                property = (expression.context as KtPropertyDelegate).context as KtProperty
            }
            is KtNamedFunction -> {
                function = expression.context as KtNamedFunction
                if (function.isLocal || function.isPrivate())
                    return
            }
        }

        if (property?.isLocal == true || property?.isPrivate() == true)
            return

        val objectType = when {
            property != null -> property.resolveToDescriptorIfAny()?.returnType ?: error("Type of property has not resolved! (Explicit Type Specifier)")
            function != null -> function.resolveToDescriptorIfAny()?.returnType ?: error("Type of function has not resolved! (Explicit Type Specifier)")
            else -> return // expression may be not assignable
        }

        val typeReference = KtPsiFactory(expression).createFqType(objectType)

        property?.let { if (it.typeReference == null) it.typeReference = typeReference }
        function?.let { if (it.typeReference == null) it.typeReference = typeReference }

    }


    private fun KtPsiFactory.createFqType(type: KotlinType): KtTypeReference {
        val fqReturnTypeName = type.getDeepJetTypeFqName(true) + if (type.isMarkedNullable) "?" else ""
        return createType(fqReturnTypeName)
    }
}

fun KtFile.acceptExplicitTypeSpecifier() = this.accept(KtExplicitTypeSpecifierVisitor)