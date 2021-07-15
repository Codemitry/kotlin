/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.typespecifiyng

import org.jetbrains.kotlin.idea.caches.resolve.resolveToDescriptorIfAny
import org.jetbrains.kotlin.mppconverter.getDeepJetTypeFqName
import org.jetbrains.kotlin.psi.*

object KtExplicitTypeSpecifierVisitor : KtTreeVisitorVoid() {
    override fun visitProperty(property: KtProperty) {
        if (property.typeReference != null) return

        val fqTypeName = property.resolveToDescriptorIfAny()?.type?.getDeepJetTypeFqName(true)
            ?: error("null in specifying property type in explicitTypeSpecifier")
        val typeReference = KtPsiFactory(property).createType(fqTypeName)
        property.typeReference = typeReference
    }

    override fun visitNamedFunction(function: KtNamedFunction) {
        if (function.typeReference != null) return

        val fqReturnTypeName = function.resolveToDescriptorIfAny()?.returnType?.getDeepJetTypeFqName(true)
            ?: error("null in specifying function type in explicitTypeSpecifier")
        val typeReference = KtPsiFactory(function).createType(fqReturnTypeName)
        function.typeReference = typeReference
    }
}

fun KtFile.acceptExplicitTypeSpecifier() = this.accept(KtExplicitTypeSpecifierVisitor)