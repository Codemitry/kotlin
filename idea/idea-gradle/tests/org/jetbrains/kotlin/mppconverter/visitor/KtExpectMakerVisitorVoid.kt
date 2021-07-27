package org.jetbrains.kotlin.mppconverter.visitor

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.getResolutionFacade
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.imports.canResolve
import org.jetbrains.kotlin.idea.refactoring.fqName.fqName
import org.jetbrains.kotlin.lexer.KtTokens.DATA_KEYWORD
import org.jetbrains.kotlin.lexer.KtTokens.EXPECT_KEYWORD
import org.jetbrains.kotlin.mppconverter.createKtParameterFromProperty
import org.jetbrains.kotlin.mppconverter.createKtPropertyWithoutInitializer
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvable
import org.jetbrains.kotlin.mppconverter.visitor.KtExpectMakerVisitorVoid.removeUnresolvableImports
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPrivate
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
        imports.filter { !it.canResolve(it.getResolutionFacade()) }.forEach { it.delete() }
    }

}

private fun KtDeclaration.makeExpect() = accept(KtExpectMakerVisitorVoid)

fun KtFile.getFileWithExpects(path: String): KtFile {
    val copiedVFile = virtualFile.copy(this, VfsUtil.createDirectories(path), name)
    val copiedKtFile = copiedVFile.toPsiFile(project) as KtFile

    return copiedKtFile.toFileWithExpects()
}

fun KtFile.toFileWithExpects(): KtFile = apply {

    declarations.forEach {
        if (it.isResolvable()) {
            if (it.isPrivate())
                /* TODO replace it with extension in future */ ;
        } else {
            if (it.isExpectizingAllowed())
                it.makeExpect()
            else
                it.delete()
        }
    }

    importList?.removeUnresolvableImports()
}