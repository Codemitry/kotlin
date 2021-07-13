package org.jetbrains.kotlin.mppconverter

import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.ProjectScope
import org.jetbrains.kotlin.idea.quickfix.createFromUsage.callableBuilder.getReturnTypeReference
import org.jetbrains.kotlin.idea.util.ifTrue
import org.jetbrains.kotlin.lexer.KtTokens.PRIVATE_KEYWORD
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvable
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.plugins.groovy.GroovyFileType
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import java.io.File


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

fun PsiElement.canConvertToCommon(context: BindingContext? = null): Boolean {
    when (this) {
        is KtClass -> {

            if (this.hasModifier(PRIVATE_KEYWORD)) return false

            if (this.getSuperTypeList()?.isResolvable() != true) return false

            if (this.isEnum() && (this.hasPrimaryConstructor() || this.secondaryConstructors.isNotEmpty())) return false

            if (this.primaryConstructor?.isResolvable() != true) return false

            this.secondaryConstructors.any { !it.isResolvable() }.ifTrue { return false }

            this.declarations.any { !it.canConvertToCommon() }.ifTrue { return false }
        }

        is KtObjectDeclaration -> {
            if (this.hasModifier(PRIVATE_KEYWORD)) return false

            if (this.getSuperTypeList()?.isResolvable() != true) return false

            this.declarations.any { !it.canConvertToCommon() }.ifTrue { return false }
        }
        is KtFunction -> {
            if (this.hasModifier(PRIVATE_KEYWORD)) return false

            if (!this.signatureIsResolvable()) return false
        }
        is KtProperty -> {
            if (this.hasModifier(PRIVATE_KEYWORD)) return false
            if (!this.isResolvable()) return false
        }

        is KtFile -> {
            return this.declarations.all { it.canConvertToCommon(context) }
        }
    }

    return true
}


fun KtFunction.signatureIsResolvable(): Boolean {
    this.receiverTypeReference?.let { if (!it.isResolvable()) return false }
    this.valueParameters.forEach { if (!it.isResolvable()) return false }
    this.getReturnTypeReference()?.let { if (!it.isResolvable()) return false } // if return type declared explicitly

    // TODO: remove body expression check from here
//    this.bodyExpression?.isResolvable()?.ifFalse { return false }
//    this.bodyExpression?.getType(analyzeWithContent())?.let { if (it.dependsOnJvm()) return true }

    return true
}



fun KtFile.packageToRelativePath(): String {
    return if (packageDirective?.isRoot != false)
        ""
    else
        packageFqName.asString().replace(".", "/")
}

fun KtFile.createDirsAndWriteFile(to: String) {
    File(to, this.name).also {
        it.parentFile.mkdirs()
        it.createNewFile()
        it.writeText(this.text)
    }
}

/**
 * creates copy of the file at dir and returns it
 */
fun File.copyTo(dir: String): File =
    File(dir, name).apply {
        parentFile.mkdirs()
        createNewFile()
        writeText(this@copyTo.readText())
    }

fun Project.createTmpGroovyFile(content: CharSequence, isPhysical: Boolean = false, context: PsiElement? = null): GroovyFile {
    return GroovyPsiElementFactory.getInstance(this).createGroovyFile(content, isPhysical, context)
}

fun Project.createTmpKotlinScriptFile(filename: String = "tmp.kts", content: String): KtFile {
    return KtPsiFactory(this).createFile(filename, content)
}