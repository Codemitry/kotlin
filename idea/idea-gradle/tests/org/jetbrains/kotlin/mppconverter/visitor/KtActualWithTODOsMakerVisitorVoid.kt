/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.visitor

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.lexer.KtTokens.ACTUAL_KEYWORD
import org.jetbrains.kotlin.mppconverter.canConvertToCommon
import org.jetbrains.kotlin.mppconverter.resolvers.isNotResolvable
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvable
import org.jetbrains.kotlin.psi.*

object KtActualWithTODOsMakerVisitorVoid : KtTreeVisitorVoid() {

    override fun visitNamedFunction(function: KtNamedFunction) {
        function.addModifier(ACTUAL_KEYWORD)

        function.bodyBlockExpression?.let { bodyBlock ->
            if (bodyBlock.isNotResolvable()) {
                bodyBlock.replace(createTODOCallExpressionInBody(function.project))
            }
        }

        function.initializer?.let { initializer ->
            if (initializer.isNotResolvable()) {
                initializer.replace(createTODOCallExpression(function.project))
            }
        }
    }

    override fun visitClass(klass: KtClass) {
        klass.addModifier(ACTUAL_KEYWORD)
        super.visitClass(klass)
    }

    override fun visitProperty(property: KtProperty) {
        property.initializer?.let { initializer ->
            if (initializer.isNotResolvable()) {
                initializer.replace(createTODOCallExpression(property.project))
            }
        }

        property.setter?.let { setter ->
            if (setter.isNotResolvable()) {
                setter.initializer?.replace(createTODOCallExpression(property.project))
                setter.bodyBlockExpression?.replace(createTODOCallExpressionInBody(property.project))
            }
        }

        property.getter?.let { getter ->
            if (getter.isNotResolvable()) {
                getter.initializer?.replace(createTODOCallExpression(property.project))
                getter.bodyBlockExpression?.replace(createTODOCallExpressionInBody(property.project))
            }
        }
    }

}

private fun KtDeclaration.makeActualWithTODOs() {
    accept(KtActualWithTODOsMakerVisitorVoid)
}

fun KtFile.getFileWithActualsWithTODOs(): KtFile = (this.copy() as KtFile).apply {
    for (declaration in declarations) {
        if (declaration.isResolvable()) {
            declaration.delete()
        } else {
            if (declaration.canConvertToCommon())
                declaration.makeActualWithTODOs()
            else
                declaration.delete()
        }
    }
}

fun KtPsiFactory.createCallExpression(text: String): KtCallExpression {
    val property = createProperty("val x = $text")
    return property.initializer as KtCallExpression
}

fun KtPsiFactory.createBodyWithExpression(text: String): KtBlockExpression {
    return createFunction("fun foo() { $text }").bodyBlockExpression!!
}

fun createTODOCallExpression(project: Project): KtExpression = KtPsiFactory(project).createCallExpression("TODO(\"Not yet implemented\")")

fun createTODOCallExpressionInBody(project: Project): KtBlockExpression = KtPsiFactory(project).createBodyWithExpression("TODO(\"Not yet implemented\")")