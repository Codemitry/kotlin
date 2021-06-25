package org.jetbrains.kotlin.mppconverter

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.idea.caches.project.toDescriptor
import org.jetbrains.kotlin.idea.caches.resolve.analyze
import org.jetbrains.kotlin.idea.caches.resolve.analyzeWithContent
import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.idea.core.targetDescriptors
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getReturnTypeReference
import org.jetbrains.kotlin.idea.search.usagesSearch.descriptor
import org.jetbrains.kotlin.idea.util.ifTrue
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import org.jetbrains.kotlin.lexer.KtTokens.PRIVATE_KEYWORD
import org.jetbrains.kotlin.mppconverter.visitor.KtActualMakerVisitorVoid
import org.jetbrains.kotlin.mppconverter.visitor.KtRealizationEraserVisitorVoid
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.bindingContextUtil.getAbbreviatedTypeOrType
import org.jetbrains.kotlin.resolve.calls.callUtil.getType
import org.jetbrains.kotlin.resolve.descriptorUtil.module
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.containsError


fun KtClassBody.addInside(element: PsiElement) {
    addAfter(element, lBrace)
    addAfter(KtPsiFactory(element).createNewLine(), lBrace)
}

fun KtSecondaryConstructor.deleteDelegationAndBody() {
    this.getDelegationCallOrNull()?.delete()
    this.bodyBlockExpression?.delete()
    this.colon?.delete()
}

fun createKtParameterFromProperty(paramProperty: KtParameter): KtParameter {
    val factory = KtPsiFactory(paramProperty)

    val pattern = buildString {
        append("a: Type")

        if (paramProperty.hasDefaultValue())
            append(" = value")
    }
    val ktParameter = factory.createParameter(pattern)

    ktParameter.setName(paramProperty.nameAsSafeName.identifier)
    paramProperty.typeReference?.let { typeRef -> ktParameter.setTypeReference(typeRef) }

    paramProperty.defaultValue?.let { defValue -> ktParameter.defaultValue?.replace(defValue) }

    return ktParameter
}

fun createKtPropertyWithoutInitializer(oldParamProperty: KtParameter): KtProperty {
    val factory = KtPsiFactory(oldParamProperty)
    val ktProperty = factory.createProperty("val a: Type")

    ktProperty.setName(oldParamProperty.nameAsSafeName.identifier)
    oldParamProperty.typeReference?.let { typeRef -> ktProperty.setTypeReference(typeRef) }
    oldParamProperty.valOrVarKeyword?.let { varOrVal -> ktProperty.valOrVarKeyword.replace(varOrVal) }

    return ktProperty
}

fun KtParameter.removeInitializer() {
    defaultValue?.delete()
    equalsToken?.delete()
}


fun KtClass.replaceConstructorPropertiesWithParameters() {
    primaryConstructorParameters.forEach { param ->
        param.replace(createKtParameterFromProperty(param))
    }
}


fun KtClass.copyConstructorPropertiesToBody() {
    primaryConstructorParameters.forEach { param ->

        if (param.isPropertyParameter()) {
            getOrCreateBody().apply {
                addInside(createKtPropertyWithoutInitializer(param))
            }
        }
    }
}



fun PsiElement.toExpect(): PsiElement {
    when (this) {
        is KtFile -> {
            this.clearJvmDependentImports()
            declarations.filter { it.isJvmDependent() }.forEach { it.toExpect() }
        }
        else -> accept(KtRealizationEraserVisitorVoid())
    }
    return this
}


fun PsiElement.toActual(): PsiElement {
    when (this) {
        is KtFile -> {
            for (declaration in declarations) {
                if (declaration.isJvmDependent()) {
                    declaration.toActual()
                } else {
                    declaration.delete()
                }
            }
        }
        else -> {
            accept(KtActualMakerVisitorVoid())
        }
    }

    return this
}

fun PsiElement.canConvertToCommon(context: BindingContext? = null): Boolean {
    when (this) {
        is KtClass -> {

            if (this.hasModifier(PRIVATE_KEYWORD)) return false

            if (this.getSuperTypeList()?.dependsOnJvm() == true) return false

            if (this.isEnum() && (this.hasPrimaryConstructor() || this.secondaryConstructors.isNotEmpty())) return false

            if (this.primaryConstructor?.dependsOnJvm() == true) return false

            this.secondaryConstructors.any { it.dependsOnJvm() }.ifTrue { return false }

            this.declarations.any { !it.canConvertToCommon() }.ifTrue { return false }
        }

        is KtObjectDeclaration -> {
            if (this.hasModifier(PRIVATE_KEYWORD)) return false

            if (this.getSuperTypeList()?.dependsOnJvm() == true) return false

            this.declarations.any { !it.canConvertToCommon() }.ifTrue { return false }
        }
        is KtFunction -> {
            if (this.hasModifier(PRIVATE_KEYWORD)) return false

            if (this.signatureDependsOnJvm()) return false
        }
        is KtProperty -> {
            if (this.hasModifier(PRIVATE_KEYWORD)) return false
            if (this.dependsOnJvm()) return false
        }

        is KtFile -> {
            return this.declarations.all { it.canConvertToCommon(context) }
        }
    }

    return true
}


fun KtPrimaryConstructor.dependsOnJvm(): Boolean{
    return valueParameters.any { it.dependsOnJvm() }
}

fun KtSecondaryConstructor.dependsOnJvm(): Boolean {
    return valueParameters.any { it.dependsOnJvm() }
}


fun KtFunction.signatureDependsOnJvm(): Boolean {
    this.receiverTypeReference?.let { if (it.dependsOnJvm()) return true }
    this.valueParameters.forEach { if (it.dependsOnJvm()) return true }
    this.getReturnTypeReference()?.let { if (it.dependsOnJvm()) return true } // if return type declared explicitly

    this.bodyExpression?.getType(analyzeWithContent())?.let { if (it.dependsOnJvm()) return true }

    return false
}

fun KtForExpression.dependsOnJvm(): Boolean {
    this.loopRange?.getType(analyze())?.dependsOnJvm()?.ifTrue { return true }
    return false // just mock. TODO: any different cases? Check body
}

fun KtTypeReference.dependsOnJvm(context: BindingContext? = null): Boolean {
    val context = context ?: this.analyze()
    val type = getAbbreviatedTypeOrType(context) ?: error("type of KtTypeReference is null") // if type hasn't defined, let it is jvm

//    if (type.isError || type.containsError()) return true // error when type is jvm because analyzer can't determine jvm types in common module

//    if (type.constructor.declarationDescriptor?.isBoringBuiltinClass() == true) return false
//    if (type.constructor.declarationDescriptor?.module?.name?.asString() == "<built-ins module>") return true

    return type.dependsOnJvm()
}

fun KtParameter.dependsOnJvm(): Boolean {

    this.typeReference?.let { return it.dependsOnJvm() }

    // type not declared explicitly

    if (this.isLoopParameter && parent is KtForExpression) {
        return (parent as KtForExpression).dependsOnJvm()
    }

    val type = this.descriptor?.type ?: return true // it can't analyze because no jvm-dependencies in common target
    return type.dependsOnJvm()
}

fun KotlinType.dependsOnJvm(): Boolean {
    if (this.isError || this.containsError()) return true // jvm types can't be analyzed in common target
    val descriptor = this.constructor.declarationDescriptor ?: error("declaration descriptor of constructor of KotlinType is null")

    // TODO: check type's parameters with .arguments

    // if non-parameterized built-in type
    if (this.arguments.isEmpty() && KotlinBuiltIns.isBuiltIn(descriptor)) return false


    return false // TODO maybe another cases?
}

fun KtProperty.dependsOnJvm(): Boolean {
    this.receiverTypeReference?.let { if (it.dependsOnJvm()) return true }
    typeConstraints.forEach { constraint -> constraint.boundTypeReference?.let { if (it.dependsOnJvm()) return true } }

    if (this.resolveToDescriptorIfAny()?.type?.dependsOnJvm() == true) return true

    return false
}


fun KtImportDirective.dependsOnJvm(): Boolean {
    if (this.targetDescriptors().isEmpty()) return true  // have not analyzed because they are jvm-dependent

    return false
}

fun KtSuperTypeList.dependsOnJvm(): Boolean {
    return entries.any { superType -> superType.typeReference?.dependsOnJvm() == true }
}


fun PsiElement.isJvmDependent(): Boolean {
    when (this) {
        is KtTypeReference -> {
            return this.dependsOnJvm()
        }

        is KtCallExpression -> {
            return this.getType(analyze())?.dependsOnJvm() ?: return true  // if type has not analyzed, it is jvm-dependent
        }

        is KtParameter -> {
            return this.dependsOnJvm()
        }

        is KtProperty -> {
            return this.dependsOnJvm()
        }

        is KtForExpression -> {
            return this.dependsOnJvm()
        }

        is KtFile -> {
            return declarations.any { it.isJvmDependent() }
        }

        is KtClass -> {
            if (this.getSuperTypeList()?.dependsOnJvm() == true) return true

            if (this.primaryConstructor?.dependsOnJvm() == true) return true

            this.secondaryConstructors.any { it.dependsOnJvm() }.ifTrue { return true }

            this.declarations.any { it.isJvmDependent() }.ifTrue { return true }
        }

        is KtObjectDeclaration -> {
            if (this.getSuperTypeList()?.dependsOnJvm() == true) return true
        }
    }


    return children.any { it.isJvmDependent() }
}

fun KtFile.clearJvmDependentImports() {
    this.importDirectives.forEach {
        if (it.dependsOnJvm())
            it.delete()
    }
}

// TODO: think how provide project to util: KotlinType.dependsOnJvm()
fun KotlinType.declaredIn(project: Project): Boolean {
    val descriptor = constructor.declarationDescriptor ?: error("Declaration descriptor of KotlinType is null")
    return project.allModules().map { it.toDescriptor() }.contains(descriptor.module)
}

