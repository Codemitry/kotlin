package org.jetbrains.kotlin.mppconverter.visitor

import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.lexer.KtTokens.DATA_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.EXPECT_KEYWORD
import org.jetbrains.kotlin.mppconverter.copyConstructorPropertiesToBody
import org.jetbrains.kotlin.mppconverter.deleteDelegationAndBody
import org.jetbrains.kotlin.mppconverter.replaceConstructorPropertiesWithParameters
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe


class KtExpectMakerVisitorVoid : KtTreeVisitorVoid() {
    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        if (property.isTopLevel) {
            property.addModifier(EXPECT_KEYWORD)
        }

        if (property.hasInitializer()) {
            // type is not shown explicitly
            if (property.typeReference == null) {
                val factory = KtPsiFactory(property)
                val type =
                    property.resolveToDescriptorIfAny()?.type?.constructor?.declarationDescriptor?.fqNameSafe?.asString() /*property.initializer?.getType(context)?.toString() */
                        ?: error("type of variable is null!")
                property.typeReference = factory.createType(type)
            }
            property.initializer?.delete()
            property.equalsToken?.delete()
        }

        property.delegate?.delete()
        property.setter?.delete()
        property.getter?.delete()
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        super.visitNamedFunction(function)

        // when fun have one expression without a block & type
        if (!function.hasBlockBody()) {
            if (function.hasBody()) {
                if (!function.hasDeclaredReturnType()) {
                    val factory = KtPsiFactory(function)
                    val returnType =
                        function.bodyExpression?.getType(function.analyzeWithContent())?.fqName?.asString()
                            ?: error("return type must not be null")
                    function.typeReference = factory.createType(returnType)
                }

                function.bodyExpression?.delete()
                function.equalsToken?.delete()
            }
        } else {
            function.bodyBlockExpression?.delete()
        }

        if (function.isTopLevel) {
            function.addModifier(EXPECT_KEYWORD)
        }

    }

    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        if (klass.isTopLevel()) {
            klass.addModifier(EXPECT_KEYWORD)
        }

        if (klass.isData()) klass.removeModifier(DATA_KEYWORD)

        klass.copyConstructorPropertiesToBody()
        klass.replaceConstructorPropertiesWithParameters()
        klass.secondaryConstructors.forEach {
            it.deleteDelegationAndBody()
        }

        klass.getAnonymousInitializers().forEach { it.delete() }

        // ...

        // TODO: remove supertype initializer
        // TODO: remove delegation implementation
    }
}