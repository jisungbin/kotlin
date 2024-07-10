/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.ir.backend.js.lower

import org.jetbrains.kotlin.backend.common.*
import org.jetbrains.kotlin.backend.common.lower.createIrBuilder
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.backend.js.JsIrBackendContext
import org.jetbrains.kotlin.ir.backend.js.transformers.irToJs.translateJsCodeIntoStatementList
import org.jetbrains.kotlin.ir.backend.js.utils.emptyScope
import org.jetbrains.kotlin.ir.builders.*
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.*
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrCompositeImpl
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.util.*
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.js.backend.ast.*
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

/**
 * TODO
 */
class JsDeleteDuplicatedCodeOutliningLowering(private val context: JsIrBackendContext) : BodyLoweringPass {
    override fun lower(irBody: IrBody, container: IrDeclaration) {
        val jsCodeOutlining = mutableSetOf<IrSimpleFunction>()
        irBody.transformChildrenVoid(object : IrElementTransformerVoid() {
            override fun visitSimpleFunction(declaration: IrSimpleFunction): IrStatement {
                if (declaration.origin != JsCodeOutliningLowering.OUTLINED_JS_CODE_ORIGIN) return super.visitSimpleFunction(declaration)
                if (jsCodeOutlining.add(declaration.originalFunction as IrSimpleFunction)) return declaration
                return IrCompositeImpl(declaration.startOffset, declaration.endOffset, context.irBuiltIns.unitType)
            }

            override fun visitCall(expression: IrCall): IrExpression {
                val jsCodeFun = jsCodeOutlining.find { it.symbol == expression.symbol.owner.originalFunction.symbol }
                if (jsCodeFun == null) return super.visitCall(expression)

                expression.symbol = jsCodeFun.symbol
                return super.visitCall(expression)
            }
        })
    }
}
