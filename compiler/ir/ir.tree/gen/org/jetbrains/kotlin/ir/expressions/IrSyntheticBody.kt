/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

//This file was generated automatically
//DO NOT MODIFY IT MANUALLY

package org.jetbrains.kotlin.ir.expressions

import org.jetbrains.kotlin.ir.IrElementBase
import org.jetbrains.kotlin.ir.visitors.IrElementTransformer
import org.jetbrains.kotlin.ir.visitors.IrElementVisitor

/**
 * A leaf IR tree element.
 * @sample org.jetbrains.kotlin.ir.generator.IrTree.syntheticBody
 */
abstract class IrSyntheticBody : IrElementBase(), IrBody {
    abstract val kind: IrSyntheticBodyKind

    override fun <R, D> accept(visitor: IrElementVisitor<R, D>, data: D): R =
        visitor.visitSyntheticBody(this, data)

    override fun <D> transform(transformer: IrElementTransformer<D>, data: D):
            IrSyntheticBody = accept(transformer, data) as IrSyntheticBody
}
