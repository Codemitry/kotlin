package org.jetbrains.kotlin.mppconverter.visitor

import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.mppconverter.removeInitializer
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvable
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPrivate

object KtActualMakerVisitorVoid : KtTreeVisitorVoid() {

    override fun visitNamedFunction(function: KtNamedFunction) {
        function.addModifier(ACTUAL_KEYWORD)
    }

    override fun visitProperty(property: KtProperty) {
        if (property.isLocal)
            return

        property.addModifier(ACTUAL_KEYWORD)
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
    }


    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)
        if (klass is KtEnumEntry)
            return

        klass.addModifier(ACTUAL_KEYWORD)
    }
}

private fun KtDeclaration.makeActual() {
    accept(KtActualMakerVisitorVoid)
}

fun KtFile.getFileWithActuals(): KtFile = (this.copy() as KtFile).apply {
    declarations.forEach { declaration ->

        if (declaration.isResolvable()) {
            if (!declaration.isPrivate()) declaration.delete() // remove duplicates with common. This way let leave private declarations
        } else {
            if (declaration.isExpectizingAllowed())
                declaration.makeActual()
        }
    }
}