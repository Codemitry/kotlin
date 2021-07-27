/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.visitor

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.mppconverter.canConvertToCommon
import org.jetbrains.kotlin.mppconverter.removeInitializer
import org.jetbrains.kotlin.mppconverter.resolvers.isNotResolvable
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvable
import org.jetbrains.kotlin.mppconverter.visitor.KtExpectMakerVisitorVoid.removeUnresolvableImports
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

    override fun visitProperty(property: KtProperty) {
        property.addModifier(ACTUAL_KEYWORD)
        property.delegateExpressionOrInitializer?.let { expression ->
            if (expression.isNotResolvable()) {
                expression.replace(createTODOCallExpression(property.project))
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

    // class

    override fun visitClass(klass: KtClass) {
        klass.addModifier(ACTUAL_KEYWORD)

        if (klass is KtEnumEntry)
            return

        super.visitClass(klass)
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
            if (delegationCall.isNotResolvable()) {
                delegationCall.valueArgumentList?.arguments?.forEach { it.delete() }
            }
        }

        constructor.bodyBlockExpression?.let { bodyBlock ->
            if (bodyBlock.isNotResolvable()) {
                bodyBlock.replace(createTODOCallExpressionInBody(constructor.project))
            }
        }

    }

}

private fun KtDeclaration.makeActualWithTODOs() {
    accept(KtActualWithTODOsMakerVisitorVoid)
}

fun KtFile.getFileWithActualsWithTODOs(path: String, newName: String = name): KtFile {
    val actualContent = (copy() as KtFile).toFileWithActualsWithTODOs().text

    val actualVFile = virtualFile.copy(this, VfsUtil.createDirectories(path), newName)
    VfsUtil.saveText(actualVFile, actualContent)

    return actualVFile.toPsiFile(project) as KtFile
}

fun KtFile.toFileWithActualsWithTODOs(): KtFile = apply {
    for (declaration in declarations) {
        if (declaration.isResolvable()) {
            if (declaration.canConvertToCommon()) // remove duplicates with common. This way let leave private declarations
                declaration.delete()
        } else {
            if (declaration.canConvertToCommon())
                declaration.makeActualWithTODOs()
            else
                declaration.delete()
        }
    }
    importList?.removeUnresolvableImports()
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