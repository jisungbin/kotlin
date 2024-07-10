/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.fir.analysis.cfa

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.cfa.FirControlFlowChecker
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.diagnostics.FirErrors
import org.jetbrains.kotlin.fir.declarations.FirControlFlowGraphOwner
import org.jetbrains.kotlin.fir.references.toResolvedVariableSymbol
import org.jetbrains.kotlin.fir.resolve.dfa.RealVariable
import org.jetbrains.kotlin.fir.resolve.dfa.ResultStatement
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.ControlFlowGraph
import org.jetbrains.kotlin.fir.resolve.dfa.cfg.QualifiedAccessNode
import org.jetbrains.kotlin.fir.resolve.dfa.dataFlowInfo
import org.jetbrains.kotlin.name.StandardClassIds

object FirResultAnalyzer : FirControlFlowChecker(MppCheckerKind.Common) {
    override fun analyze(graph: ControlFlowGraph, reporter: DiagnosticReporter, context: CheckerContext) {
        val variableInfo = (graph.declaration as? FirControlFlowGraphOwner)?.controlFlowGraphReference?.dataFlowInfo ?: return
        for (node in graph.nodes) {
            if (node !is QualifiedAccessNode) continue
            val fir = node.fir

            val callableId = fir.calleeReference.toResolvedVariableSymbol()?.callableId ?: continue
            val required = when (callableId) {
                StandardClassIds.Callables.result -> ResultStatement.SUCCESS
                StandardClassIds.Callables.exception -> ResultStatement.FAILURE
                else -> continue
            }

            val realVariable = fir.dispatchReceiver?.let { variableInfo.variableStorage.get(it) { v, _ -> v } } as? RealVariable
            val actual = realVariable?.let { node.flow.getTypeStatement(it) }?.resultStatement ?: ResultStatement.UNKNOWN
            if (required != actual) {
                reporter.reportOn(fir.source, FirErrors.UNSAFE_RESULT, context)
            }
        }
    }

}