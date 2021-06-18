package org.jetbrains.kotlin.mppconverter.visitor

import org.jetbrains.kotlin.lexer.KtTokens.*
import org.jetbrains.kotlin.mppconverter.deleteModifiersIncompatibleWithActual
import org.jetbrains.kotlin.mppconverter.removeInitializer
import org.jetbrains.kotlin.psi.*

class KtActualMakerVisitorVoid : KtTreeVisitorVoid() {

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        function.addModifier(ACTUAL_KEYWORD)
        function.deleteModifiersIncompatibleWithActual()
    }

    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        if (property.isLocal)
            return

        property.addModifier(ACTUAL_KEYWORD)
        property.deleteModifiersIncompatibleWithActual()
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
            it.deleteModifiersIncompatibleWithActual()
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
        klass.deleteModifiersIncompatibleWithActual()
    }
}