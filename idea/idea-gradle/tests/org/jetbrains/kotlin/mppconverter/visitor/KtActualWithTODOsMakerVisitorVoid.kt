/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.visitor

import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.mppconverter.removeInitializer
import org.jetbrains.kotlin.psi.*

object KtActualWithTODOsMakerVisitorVoid : KtTreeVisitorVoid() {

    override fun visitNamedFunction(function: KtNamedFunction) {
        function.addModifier(ACTUAL_KEYWORD)

        function.bodyBlockExpression?.replace(createTODOCallExpressionInBody(function.project))
        function.initializer?.replace(createTODOCallExpression(function.project))
    }

    override fun visitProperty(property: KtProperty) {
        property.addModifier(ACTUAL_KEYWORD)
        property.delegateExpressionOrInitializer?.replace(createTODOCallExpression(property.project))

        property.setter?.let { setter ->
            setter.initializer?.replace(createTODOCallExpression(property.project))
            setter.bodyBlockExpression?.replace(createTODOCallExpressionInBody(property.project))
        }

        property.getter?.let { getter ->
            getter.initializer?.replace(createTODOCallExpression(property.project))
            getter.bodyBlockExpression?.replace(createTODOCallExpressionInBody(property.project))
        }
    }

    // class

    override fun visitClass(klass: KtClass) {
        klass.addModifier(ACTUAL_KEYWORD)

        if (klass is KtEnumEntry)
            return

        super.visitClass(klass)
    }

    override fun visitObjectDeclaration(declaration: KtObjectDeclaration) {
        declaration.addModifier(ACTUAL_KEYWORD)

        super.visitObjectDeclaration(declaration)
    }

    override fun visitPrimaryConstructor(constructor: KtPrimaryConstructor) {
        super.visitPrimaryConstructor(constructor)

        // to prevent situation, when: "class Nameactual constructor()"
        // class Name constructor() -> class Nameactual constructor() -> class Nameprivate actual constructor() -> class Name actual constructor()
        val supportingModifier = if (constructor.hasModifier(PRIVATE_KEYWORD)) PUBLIC_KEYWORD else PRIVATE_KEYWORD

        constructor.addModifier(ACTUAL_KEYWORD)
        constructor.addModifier(supportingModifier)
        constructor.removeModifier(supportingModifier)

        constructor.valueParameterList?.parameters?.forEach {
            if (it.hasValOrVar()) {
                it.addModifier(ACTUAL_KEYWORD)
            }
            it.removeInitializer()
        }

    }


    override fun visitSecondaryConstructor(constructor: KtSecondaryConstructor) {
        super.visitSecondaryConstructor(constructor)

        constructor.addModifier(ACTUAL_KEYWORD)

        constructor.getDelegationCallOrNull()?.let { delegationCall ->
            delegationCall.valueArgumentList?.arguments?.forEach { it.delete() }
        }

        constructor.bodyBlockExpression?.replace(createTODOCallExpressionInBody(constructor.project))

    }

}

fun KtDeclaration.makeActualWithTODOs() = accept(KtActualWithTODOsMakerVisitorVoid)
fun KtDeclaration.toActualWithTODOs() = apply { makeActualWithTODOs() }


fun KtPsiFactory.createCallExpression(text: String): KtCallExpression {
    val property = createProperty("val x = $text")
    return property.initializer as KtCallExpression
}

fun KtPsiFactory.createBodyWithExpression(text: String): KtBlockExpression {
    return createFunction("fun foo() { $text }").bodyBlockExpression!!
}

fun createTODOCallExpression(project: Project): KtExpression = KtPsiFactory(project).createCallExpression("TODO(\"Not yet implemented\")")

fun createTODOCallExpressionInBody(project: Project): KtBlockExpression =
    KtPsiFactory(project).createBodyWithExpression("TODO(\"Not yet implemented\")")