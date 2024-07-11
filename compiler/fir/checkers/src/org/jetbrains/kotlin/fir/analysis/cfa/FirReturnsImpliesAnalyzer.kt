/*
 * Copyright 2010-2022 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.cfa

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.analysis.cfa.util.previousCfgNodes
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.cfa.FirControlFlowChecker
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.contracts.description.ConeConditionalEffectDeclaration
import org.jetbrains.kotlin.fir.contracts.description.ConeReturnsEffectDeclaration
import org.jetbrains.kotlin.fir.contracts.effects
import org.jetbrains.kotlin.fir.declarations.FirContractDescriptionOwner
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.expressions.FirExpression
import org.jetbrains.kotlin.fir.expressions.FirReturnExpression
import org.jetbrains.kotlin.fir.expressions.FirWhenExpression
import org.jetbrains.kotlin.fir.resolve.dfa.*
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.BlockExitNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.CFGNode
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.JumpNode
import org.jetbrains.kotlin.fir.resolve.fullyExpandedType
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertyAccessorSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirValueParameterSymbol
import org.jetbrains.kotlin.fir.types.*
import org.jetbrains.kotlin.types.AbstractTypeChecker

object FirReturnsImpliesAnalyzer : FirControlFlowChecker(MppCheckerKind.Common) {

    override fun analyze(graph: ControlFlowGraph, reporter: DiagnosticReporter, context: CheckerContext) {
        val logicSystem = object : LogicSystem(context.session.typeContext) {
            override val variableStorage: VariableStorageImpl
                get() = throw IllegalStateException("shouldn't be called")
        }
        analyze(graph, reporter, context, logicSystem)
    }

    private fun analyze(graph: ControlFlowGraph, reporter: DiagnosticReporter, context: CheckerContext, logicSystem: LogicSystem) {
        // Not quadratic since we don't traverse the graph, we only care about (declaration, exit node) pairs.
        for (subGraph in graph.subGraphs) {
            analyze(subGraph, reporter, context)
        }

        val function = graph.declaration as? FirFunction ?: return
        if (function !is FirContractDescriptionOwner) return
        val contractDescription = function.contractDescription ?: return
        val effects = contractDescription.effects ?: return

        // Creating variables can be needed in two cases:
        //   1. trivial contracts: `returns() implies (x is T)` when `x`'s original type is `T`
        //   2. tautological contracts: `returnsNotNull() implies (x != null)` with a `return x`
        // In both cases `x` must not have been used in data flow analysis in any way, otherwise it would already have a variable.
        val variableStorage = function.controlFlowGraphReference?.dataFlowInfo?.variableStorage as? VariableStorageImpl ?: return
        val argumentVariables = Array(function.valueParameters.size + 1) { i ->
            val parameterSymbol = if (i > 0) {
                function.valueParameters[i - 1].symbol
            } else {
                if (function.symbol is FirPropertyAccessorSymbol) {
                    context.containingProperty?.symbol
                } else {
                    null
                } ?: function.symbol
            }
            parameterSymbol.correspondingParameterType?.let {
                variableStorage.getOrCreateLocalVariable(parameterSymbol, it, isReceiver = i == 0)
            }
        }

        for (firEffect in effects) {
            val coneEffect = firEffect.effect as? ConeConditionalEffectDeclaration ?: continue
            val returnValue = coneEffect.effect as? ConeReturnsEffectDeclaration ?: continue
            val wrongCondition = graph.exitNode.previousCfgNodes.any {
                isWrongConditionOnNode(it, coneEffect, returnValue, function, logicSystem, variableStorage, argumentVariables, context)
            }
            if (wrongCondition) {
                reporter.reportOn(firEffect.source, FirErrors.WRONG_IMPLIES_CONDITION, context)
            }
        }
    }

    private fun isWrongConditionOnNode(
        node: CFGNode<*>,
        effectDeclaration: ConeConditionalEffectDeclaration,
        effect: ConeReturnsEffectDeclaration,
        function: FirFunction,
        logicSystem: LogicSystem,
        variableStorage: VariableStorageImpl,
        argumentVariables: Array<RealVariable?>,
        context: CheckerContext
    ): Boolean {
        val builtinTypes = context.session.builtinTypes
        val typeContext = context.session.typeContext

        val isReturn = node is JumpNode && node.fir is FirReturnExpression
        @Suppress("USELESS_CAST") // K2 warning suppression, TODO: KT-62472
        val resultExpression = if (isReturn) (node.fir as FirReturnExpression).result else node.fir

        val expressionType = (resultExpression as? FirExpression)?.resolvedType?.fullyExpandedType(context.session)
        if (expressionType == builtinTypes.nothingType.type) return false

        if (isReturn && resultExpression is FirWhenExpression) {
            return node.collectBranchExits().any {
                isWrongConditionOnNode(it, effectDeclaration, effect, function, logicSystem, variableStorage, argumentVariables, context)
            }
        }

        var flow = node.flow
        val operation = effect.value.toOperation()
        if (operation != null) {
            if (resultExpression is FirLiteralExpression) {
                if (!operation.isTrueFor(resultExpression.value)) return false
            } else {
                if (expressionType != null && !operation.canBeTrueFor(context.session, expressionType)) return false
                val resultVar =
                    variableStorage.getOrCreateIfReal(resultExpression, unwrapAlias = { variable, _ -> flow.unwrapVariable(variable) })
                if (resultVar != null) {
                    val impliedByReturnValue = logicSystem.approveOperationStatement(flow, OperationStatement(resultVar, operation))
                    if (impliedByReturnValue.isNotEmpty()) {
                        flow = flow.fork().also { logicSystem.addTypeStatements(it, impliedByReturnValue) }.freeze()
                    }
                }
            }
        }

        val conditionStatements = logicSystem.approveContractStatement(
            effectDeclaration.condition, argumentVariables, substitutor = null
        ) { logicSystem.approveOperationStatement(flow, it) } ?: return true

        return !conditionStatements.values.all { requirement ->
            val originalType = requirement.variable.originalType ?: return@all true
            val requiredType = requirement.smartCastedType(typeContext, originalType)
            val actualType = flow.getTypeStatement(requirement.variable).smartCastedType(typeContext, originalType)
            actualType.isSubtypeOf(typeContext, requiredType)
        }
    }

    private fun Operation.canBeTrueFor(session: FirSession, type: ConeKotlinType): Boolean = when (this) {
        Operation.EqTrue, Operation.EqFalse ->
            AbstractTypeChecker.isSubtypeOf(session.typeContext, session.builtinTypes.booleanType.type, type)
        Operation.EqNull -> type.canBeNull(session)
        Operation.NotEqNull -> !type.isNullableNothing
    }

    private fun Operation.isTrueFor(value: Any?) = when (this) {
        Operation.EqTrue -> value == true
        Operation.EqFalse -> value == false
        Operation.EqNull -> value == null
        Operation.NotEqNull -> value != null
    }

    private fun CFGNode<*>.collectBranchExits(nodes: MutableList<CFGNode<*>> = mutableListOf()): List<CFGNode<*>> {
        if (this is BlockExitNode) {
            nodes += previousCfgNodes
        } else previousCfgNodes.forEach { it.collectBranchExits(nodes) }
        return nodes
    }

    private val CheckerContext.containingProperty: FirProperty?
        get() = (containingDeclarations.lastOrNull { it is FirProperty } as? FirProperty)

    private val FirBasedSymbol<*>.correspondingParameterType: ConeKotlinType?
        get() = when (this) {
            is FirValueParameterSymbol -> resolvedReturnType
            is FirCallableSymbol<*> -> resolvedReceiverTypeRef?.coneType
            else -> null
        }
}
