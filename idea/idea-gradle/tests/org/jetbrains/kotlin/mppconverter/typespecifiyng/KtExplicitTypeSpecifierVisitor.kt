/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.typespecifiyng

import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.project.builtIns
import org.jetbrains.kotlin.mppconverter.getDeepJetTypeFqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.types.KotlinType

object KtExplicitTypeSpecifierVisitor : KtTreeVisitorVoid() {
    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        if (property.typeReference != null) return

        val fqTypeName = property.resolveToDescriptorIfAny()?.type?.getDeepJetTypeFqName(true)
            ?: error("null in specifying property type in explicitTypeSpecifier")
        val typeReference = KtPsiFactory(property).createType(fqTypeName)
        property.typeReference = typeReference
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        if (function.typeReference != null) return

        val fqReturnTypeName = function.resolveToDescriptorIfAny()?.returnType?.getDeepJetTypeFqName(true)
            ?: error("null in specifying function type in explicitTypeSpecifier")
        val typeReference = KtPsiFactory(function).createType(fqReturnTypeName)
        function.typeReference = typeReference
    }


    override fun visitObjectLiteralExpression(expression: KtObjectLiteralExpression) {

        val objectType = expression.getTypeBySuper()?.getDeepJetTypeFqName(true)?.let { KtPsiFactory(expression).createType(it) }

        when (expression.context) {
            is KtProperty -> {
                val property = expression.context as KtProperty
                if (property.typeReference == null) {
                    property.typeReference = objectType
                }
            }
            is KtPropertyAccessor -> {
                val property = (expression.context as KtPropertyAccessor).property
                if (property.typeReference == null) {
                    property.typeReference = objectType
                }
            }
            is KtPropertyDelegate -> {
                val property = (expression.context as KtPropertyDelegate).context as KtProperty
                if (property.typeReference == null) {
                    property.typeReference = objectType
                }
            }
            is KtFunction -> {
                val function = expression.context as KtFunction
                if (function.typeReference == null) {
                    function.typeReference = objectType
                }
            }
        }

        super.visitObjectLiteralExpression(expression)
    }

}

fun KtObjectLiteralExpression.getTypeBySuper(): KotlinType? = when {
    objectDeclaration.superTypeListEntries.isEmpty() -> builtIns.anyType
    objectDeclaration.superTypeListEntries.size > 1 -> builtIns.anyType
    objectDeclaration.superTypeListEntries.size == 1 -> objectDeclaration.superTypeListEntries.first().typeReference?.getAbbreviatedTypeOrType(analyze())
    else -> null
}

fun KtFile.acceptExplicitTypeSpecifier() = this.accept(KtExplicitTypeSpecifierVisitor)