/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

package org.jetbrains.kotlin.backend.konan.llvm

import kotlinx.cinterop.cValuesOf
import kotlinx.cinterop.toByte
import llvm.*
import org.jetbrains.kotlin.backend.konan.InternalLoweredEnum
import org.jetbrains.kotlin.backend.konan.ir.llvmSymbolOrigin
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.*
import org.jetbrains.kotlin.types.Variance

private fun ConstPointer.add(index: Int): ConstPointer {
    return constPointer(LLVMConstGEP(llvm, cValuesOf(Int32(index).llvm), 1)!!)
}

// Must match OBJECT_TAG_PERMANENT_CONTAINER in C++.
private fun StaticData.permanentTag(typeInfo: ConstPointer): ConstPointer {
    // Only pointer arithmetic via GEP works on constant pointers in LLVM.
    return typeInfo.bitcast(int8TypePtr).add(1).bitcast(kTypeInfoPtr)
}

private fun StaticData.objHeader(typeInfo: ConstPointer): Struct {
    return Struct(runtime.objHeaderType, permanentTag(typeInfo))
}

private fun StaticData.arrayHeader(typeInfo: ConstPointer, length: Int): Struct {
    assert (length >= 0)
    return Struct(runtime.arrayHeaderType, permanentTag(typeInfo), Int32(length))
}

internal fun StaticData.createKotlinStringLiteral(value: String): ConstPointer {
    val elements = value.toCharArray().map(::Char16)
    val objRef = createConstKotlinArray(context.ir.symbols.string.owner, elements)
    return objRef
}

private fun StaticData.createRef(objHeaderPtr: ConstPointer) = objHeaderPtr.bitcast(kObjHeaderPtr)

internal fun StaticData.createConstKotlinArray(arrayClass: IrClass, elements: List<LLVMValueRef>) =
        createConstKotlinArray(arrayClass, elements.map { constValue(it) }).llvm

internal fun StaticData.createConstKotlinArray(arrayClass: IrClass, elements: List<ConstValue>): ConstPointer {
    val typeInfo = arrayClass.typeInfoPtr

    val bodyElementType: LLVMTypeRef = elements.firstOrNull()?.llvmType ?: int8Type
    // (use [0 x i8] as body if there are no elements)
    val arrayBody = ConstArray(bodyElementType, elements)

    val compositeType = structType(runtime.arrayHeaderType, arrayBody.llvmType)

    val global = this.createGlobal(compositeType, "")

    val objHeaderPtr = global.pointer.getElementPtr(0)
    val arrayHeader = arrayHeader(typeInfo, elements.size)

    global.setInitializer(Struct(compositeType, arrayHeader, arrayBody))
    global.setConstant(true)

    return createRef(objHeaderPtr)
}

internal fun StaticData.createConstKotlinObject(type: IrClass, vararg fields: ConstValue, name: String = ""): ConstPointer {
    val typeInfo = type.typeInfoPtr
    val objHeader = objHeader(typeInfo)

    val global = this.placeGlobal(name, Struct(objHeader, *fields))
    global.setConstant(true)

    val objHeaderPtr = global.pointer.getElementPtr(0)

    return createRef(objHeaderPtr)
}

internal fun StaticData.createInitializer(type: IrClass, vararg fields: ConstValue): ConstValue =
        Struct(objHeader(type.typeInfoPtr), *fields)

internal fun StaticData.createConstKotlinObject(type: IrClass, fields: Map<String, ConstValue>, name: String = ""): ConstPointer =
        createConstKotlinObject(type, *context.getLayoutBuilder(type).fields.map {
            fields[it.name.asString()] ?: throw IllegalStateException("need field ${it.name} to create const ${type.name} object")
        }.also { require(it.size == fields.size) }.toTypedArray(), name = name)

/**
 * Creates static instance of `kotlin.collections.ArrayList<elementType>` with given values of fields.
 *
 * @param array value for `array: Array<E>` field.
 * @param length value for `length: Int` field.
 */
internal fun StaticData.createConstArrayList(array: ConstPointer, length: Int): ConstPointer {
    val arrayListClass = context.ir.symbols.arrayList.owner

    return createConstKotlinObject(arrayListClass, mapOf(
            "array" to array,
            "offset" to Int32(0),
            "length" to Int32(length),
            "backing" to NullPointer(kObjHeader),
            "modCount" to Int32(0),
            "root" to NullPointer(kObjHeader),
            "isReadOnly" to Int1(1)
    ))
}

internal fun StaticData.createUniqueInstance(
        kind: UniqueKind, bodyType: LLVMTypeRef, typeInfo: ConstPointer): ConstPointer {
    assert (getStructElements(bodyType).size == 1) // ObjHeader only.
    val objHeader = when (kind) {
        UniqueKind.UNIT -> objHeader(typeInfo)
        UniqueKind.EMPTY_ARRAY -> arrayHeader(typeInfo, 0)
    }
    val global = this.placeGlobal(kind.llvmName, objHeader, isExported = true)
    global.setConstant(true)
    return global.pointer
}

internal fun ContextUtils.unique(kind: UniqueKind): ConstPointer {
    val descriptor = when (kind) {
        UniqueKind.UNIT -> context.ir.symbols.unit.owner
        UniqueKind.EMPTY_ARRAY -> context.ir.symbols.array.owner
    }
    return if (isExternal(descriptor)) {
        constPointer(importGlobal(
                kind.llvmName, context.llvm.runtime.objHeaderType, origin = descriptor.llvmSymbolOrigin
        ))
    } else {
        context.llvmDeclarations.forUnique(kind).pointer
    }
}

internal val ContextUtils.theUnitInstanceRef: ConstPointer
    get() = this.unique(UniqueKind.UNIT)


internal fun StaticData.createKTypeObject(type: IrType) : ConstPointer{
    if (type !is IrSimpleType) throw NotImplementedError()

    val classifier = type.classifier as? IrClassSymbol ?: throw NotImplementedError()

    val argumentVairance = mutableListOf<ConstValue>()
    val argumentType = mutableListOf<ConstValue>()
    for (argument in type.arguments) {
        val variance: ConstValue
        val projectionType: ConstValue
        when (argument) {
            is IrStarProjection -> {
                variance = Int32(-1)
                projectionType = NullPointer(kObjHeader)
            }
            is IrTypeProjection -> {
                val loweredEnum = context.specialDeclarationsFactory.getLoweredEnum(context.ir.symbols.kVariance.owner) as InternalLoweredEnum
                val varianceName = when (argument.variance) {
                    Variance.INVARIANT -> context.ir.symbols.kVarianceInvariant.owner.name
                    Variance.IN_VARIANCE -> context.ir.symbols.kVarianceIn.owner.name
                    Variance.OUT_VARIANCE -> context.ir.symbols.kVarianceOut.owner.name
                }
                variance = Int32(loweredEnum.entriesMap[varianceName]!!.ordinal)
                projectionType = kotlinTypeObject(argument.type)
            }
            else -> throw NotImplementedError()
        }
        argumentVairance.add(variance)
        argumentType.add(projectionType)
    }

    val arguments = createConstKotlinObject(context.ir.symbols.kTypeProjectionSpecialList.owner, mapOf(
            "varianceOrdinal" to createConstKotlinArray(context.ir.symbols.int.owner, argumentVairance),
            "type" to createConstKotlinArray(context.ir.symbols.kTypeImpl.owner, argumentType)
    ))

    return createConstKotlinObject(context.ir.symbols.kTypeImpl.owner, mapOf(
            "classifier" to createConstKotlinObject(context.ir.symbols.kClassImpl.owner, classifier.owner.typeInfoPtr),
            "arguments" to arguments,
            "isMarkedNullable" to Int1(type.isNullable().toByte())
    ))
}