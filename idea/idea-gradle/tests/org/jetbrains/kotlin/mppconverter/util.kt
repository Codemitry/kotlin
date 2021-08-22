package org.jetbrains.kotlin.mppconverter

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.idea.imports.importableFqName
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.isTypeParameter
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory


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


fun KtFile.packageToRelativePath(): String {
    return if (packageDirective?.isRoot != false)
        ""
    else
        packageFqName.asString().replace(".", "/")
}

fun Project.createTmpGroovyFile(content: CharSequence, isPhysical: Boolean = false, context: PsiElement? = null): GroovyFile {
    return GroovyPsiElementFactory.getInstance(this).createGroovyFile(content, isPhysical, context)
}

fun Project.createTmpKotlinScriptFile(filename: String = "tmp.kts", content: String): KtFile {
    return KtPsiFactory(this).createFile(filename, content)
}

/**
 * @see org.jetbrains.kotlin.js.descriptorUtils.getJetTypeFqName
 */
fun KotlinType.getDeepJetTypeFqName(printTypeArguments: Boolean): String {
    val declaration = requireNotNull(constructor.declarationDescriptor) {
        "declarationDescriptor is null for constructor = $constructor with ${constructor.javaClass}"
    }
//    if (declaration is TypeParameterDescriptor) {
//        return StringUtil.join(declaration.upperBounds, { type -> type.getDeepJetTypeFqName(printTypeArguments) }, "&")
//    }

    val typeArguments = arguments

    val argumentsAsString = buildString {
        if (typeArguments.isEmpty() || !printTypeArguments) return@buildString
        append("<")
        append(
            typeArguments.joinToString(",") {
                when {
                    it.isStarProjection || it.type.isTypeParameter() -> it.toString()
                    else -> it.type.getDeepJetTypeFqName(printTypeArguments)
                }
            }
        )
        append(">")
    }

    return declaration.importableFqName?.asString() + if (isMarkedNullable) "?" else "" + argumentsAsString
}

fun KtElement.addWithEndedNL(elem: PsiElement) {
    add(elem)
    add(KtPsiFactory(this).createNewLine(2))
}
