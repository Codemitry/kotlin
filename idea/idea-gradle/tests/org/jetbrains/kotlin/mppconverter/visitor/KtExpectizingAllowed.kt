/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter.visitor

import org.jetbrains.kotlin.idea.util.ifTrue
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.mppconverter.isResolvableSignature
import org.jetbrains.kotlin.mppconverter.isResolvableType
import org.jetbrains.kotlin.mppconverter.resolvers.isNotResolvable
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvable
import org.jetbrains.kotlin.psi.*

class KtExpectizingAllowed {
}

fun KtDeclaration.isExpectizingAllowed(): Boolean {
    if (this.hasModifier(KtTokens.PRIVATE_KEYWORD)) return false

    when (this) {
        is KtClass -> {
            this.getSuperTypeList()?.isNotResolvable()?.ifTrue { return false }

            if (this.isEnum() && (this.hasPrimaryConstructor() || this.secondaryConstructors.isNotEmpty())) return false

            this.primaryConstructor?.isNotResolvable()?.ifTrue { return false }

            this.secondaryConstructors.any { !it.isResolvable() }.ifTrue { return false }

            this.declarations.any { it.isExpectizingDenied() }.ifTrue { return false }
        }
        is KtObjectDeclaration -> {
            this.primaryConstructor?.isNotResolvable()?.ifTrue { return false }

            this.declarations.any { it.isExpectizingDenied() }.ifTrue { return false }
        }
        is KtFunction -> {
            if (!this.isResolvableSignature()) return false
        }
        is KtProperty -> {
            if (!this.isResolvableType()) return false
        }
        is KtTypeAlias -> {
            if (this.isNotResolvable()) return false
        }
        else -> System.err.println("Unknown declaration: ${this.text}\n------------\n\n")
    }

    return true
}

fun KtDeclaration.isExpectizingDenied(): Boolean = !isExpectizingAllowed()