package org.jetbrains.kotlin.mppconverter.visitor

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.lexer.KtTokens.DATA_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.EXPECT_KEYWORD
import org.jetbrains.kotlin.mppconverter.canConvertToCommon
import org.jetbrains.kotlin.mppconverter.createKtParameterFromProperty
import org.jetbrains.kotlin.mppconverter.createKtPropertyWithoutInitializer
import org.jetbrains.kotlin.mppconverter.resolvers.isNotResolvable
import org.jetbrains.kotlin.mppconverter.visitor.KtExpectMakerVisitorVoid.removeUnresolvableImports
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe


object KtExpectMakerVisitorVoid : KtTreeVisitorVoid() {
    override fun visitProperty(property: KtProperty) {
        super.visitProperty(property)

        if (property.isTopLevel) {
            property.addModifier(EXPECT_KEYWORD)
        }

        if (property.hasInitializer()) {

            // if type is not shown explicitly
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

    // KtClass
    override fun visitClass(klass: KtClass) {
        super.visitClass(klass)

        if (klass.isTopLevel()) {
            klass.addModifier(EXPECT_KEYWORD)
        }

        if (klass.isData()) klass.removeModifier(DATA_KEYWORD)

        klass.copyConstructorPropertiesToBody()
        klass.primaryConstructor?.replacePropertiesWithParameters()
        klass.secondaryConstructors.forEach {
            it.deleteDelegationAndBody()
        }

        klass.getAnonymousInitializers().forEach { it.delete() }

        // ...

        // TODO: remove supertype initializer
        // TODO: remove delegation implementation
    }

    private fun KtPrimaryConstructor.replacePropertiesWithParameters() {
        this.valueParameters.forEach { param ->
            param.replace(createKtParameterFromProperty(param))
        }
    }

    private fun KtSecondaryConstructor.deleteDelegationAndBody() {
        this.getDelegationCallOrNull()?.delete()
        this.bodyBlockExpression?.delete()
        this.colon?.delete()
    }

    private fun KtClass.copyConstructorPropertiesToBody() {
        primaryConstructorParameters.forEach { param ->

            if (param.isPropertyParameter()) {
                getOrCreateBody().apply {
                    addInside(createKtPropertyWithoutInitializer(param))
                }
            }
        }
    }

    private fun KtClassBody.addInside(element: PsiElement) {
        addAfter(element, lBrace)
        addAfter(KtPsiFactory(element).createNewLine(), lBrace)
    }

    fun KtImportList.removeUnresolvableImports() {
        imports.filter { it.isNotResolvable() }.forEach { it.delete() }
    }

}

private fun KtDeclaration.makeExpect() = accept(KtExpectMakerVisitorVoid)

fun KtFile.getFileWithExpects(): KtFile = (this.copy() as KtFile).apply {
    declarations.filter { it.isNotResolvable() && it.canConvertToCommon() }.forEach { it.makeExpect() }
    declarations.filter { !it.canConvertToCommon() }.forEach { it.delete() }
    importList?.removeUnresolvableImports()
}