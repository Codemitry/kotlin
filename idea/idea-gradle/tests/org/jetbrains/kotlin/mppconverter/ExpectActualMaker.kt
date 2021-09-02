/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.mppconverter

import com.intellij.openapi.vfs.LocalFileSystem
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.mppconverter.resolvers.isResolvable
import org.jetbrains.kotlin.mppconverter.visitor.KtExpectMakerVisitorVoid.removeUnresolvableImports
import org.jetbrains.kotlin.mppconverter.visitor.isExpectizingAllowed
import org.jetbrains.kotlin.mppconverter.visitor.toActual
import org.jetbrains.kotlin.mppconverter.visitor.toActualWithTODOs
import org.jetbrains.kotlin.mppconverter.visitor.toExpect
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

class ExpectActualMaker(val file: KtFile) {
    lateinit var expectFile: KtFile
        private set

    lateinit var actualFile: KtFile
        private set

    lateinit var actualTODOFile: KtFile
        private set

    fun generateFiles(
        expectFilePath: String,
        expectFileName: String = file.name,
        actualFilePath: String,
        actualFileName: String = file.name,
        actualTODOFilePath: String,
        actualTODOFileName: String = file.name
    ) {
        val expectIOFile = File(expectFilePath, expectFileName)
        expectIOFile.parentFile.mkdirs()
        expectIOFile.createNewFile()
        val expectVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(expectIOFile)!!
        expectFile = expectVFile.toPsiFile(file.project) as KtFile

        val actualIOFile = File(actualFilePath, actualFileName)
        actualIOFile.parentFile.mkdirs()
        actualIOFile.createNewFile()
        val actualVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(actualIOFile)!!
        actualFile = actualVFile.toPsiFile(file.project) as KtFile

        val actualTODOIOFile = File(actualTODOFilePath, actualTODOFileName)
        actualTODOIOFile.parentFile.mkdirs()
        actualTODOIOFile.createNewFile()
        val actualTODOVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(actualTODOIOFile)!!
        actualTODOFile = actualTODOVFile.toPsiFile(file.project) as KtFile


        file.packageDirective?.let {
            expectFile.addWithEndedNL(it, 1)

            actualFile.packageDirective?.delete()

            if (actualFile.children.isNotEmpty())
                actualFile.addBeforeWithEndedNL(it.copy(), actualFile.children[0], 1)
            else
                actualFile.addWithEndedNL(it.copy(), 1)

            actualTODOFile.addWithEndedNL(it, 1)
        }
        file.importList?.let {
            expectFile.addWithEndedNL(it, 1)

            actualFile.importList?.delete()

            if (actualFile.children.isNotEmpty())
                actualFile.addBeforeWithEndedNL(it.copy(), actualFile.children[0], 1)
            else
                actualFile.addWithEndedNL(it.copy(), 1)

            actualTODOFile.addWithEndedNL(it, 1)
        }

        file.declarations.forEach { dcl ->
            if (dcl.isResolvable) {
                expectFile.addWithEndedNL(dcl.copy(), 1)
            } else {
                if (dcl.isExpectizingAllowed()) {
                    expectFile.addWithEndedNL((dcl.copy() as KtDeclaration).toExpect(), 1)
                    actualFile.addWithEndedNL((dcl.copy() as KtDeclaration).toActual(), 1)
                    actualTODOFile.addWithEndedNL((dcl.copy() as KtDeclaration).toActualWithTODOs(), 1)
                } else {
                    actualFile.addWithEndedNL(dcl.copy(), 1)
                }
            }
            dcl.delete()

        }

        expectFile.removeUnresolvableImports()
        actualTODOFile.removeUnresolvableImports()
    }
}