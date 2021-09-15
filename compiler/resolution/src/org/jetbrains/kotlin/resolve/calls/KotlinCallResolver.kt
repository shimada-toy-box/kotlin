/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.resolve.calls

import org.jetbrains.kotlin.config.LanguageFeature
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus
import org.jetbrains.kotlin.resolve.calls.components.*
import org.jetbrains.kotlin.resolve.calls.context.CheckArgumentTypesMode
import org.jetbrains.kotlin.resolve.calls.inference.ConstraintSystemBuilder
import org.jetbrains.kotlin.resolve.calls.inference.NewConstraintSystem
import org.jetbrains.kotlin.resolve.calls.inference.model.ConstraintStorage
import org.jetbrains.kotlin.resolve.calls.model.*
import org.jetbrains.kotlin.resolve.calls.tower.*
import org.jetbrains.kotlin.resolve.descriptorUtil.OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION_FQ_NAME
import org.jetbrains.kotlin.resolve.scopes.receivers.ReceiverValueWithSmartCastInfo
import org.jetbrains.kotlin.types.UnwrappedType


class KotlinCallResolver(
    private val towerResolver: TowerResolver,
    private val kotlinCallCompleter: KotlinCallCompleter,
    private val overloadingConflictResolver: NewOverloadingConflictResolver,
    private val callableReferenceResolver: CallableReferenceResolver,
    private val callComponents: KotlinCallComponents
) {
    fun resolveCallableReference(
        scopeTower: ImplicitScopeTower,
        kotlinCall: KotlinCall,
        factory: CandidateFactory<ResolutionCandidate>
    ): Set<CallableReferenceCandidate> {
        val processor = createCallableReferenceProcessor(factory as CallableReferencesCandidateFactory)
        val candidates = towerResolver.runResolve(scopeTower, processor, useOrder = true, name = kotlinCall.name)

        return callableReferenceResolver.callableReferenceOverloadConflictResolver.chooseMaximallySpecificCandidates(
            candidates,
            CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS,
            discriminateGenerics = false // we can't specify generics explicitly for callable references
        ) as Set<CallableReferenceCandidate>
    }

    fun createFactory(
        scopeTower: ImplicitScopeTower,
        kotlinCall: KotlinCall,
        resolutionCallbacks: KotlinResolutionCallbacks,
        argument: CallableReferenceKotlinCallArgument?,
        expectedType: UnwrappedType?,
        baseConstraintSystem: NewConstraintSystem? = null,
        diagnosticsHolder: KotlinDiagnosticsHolder? = null
    ): CandidateFactory<ResolutionCandidate> {
        return if (kotlinCall.callKind == KotlinCallKind.CALLABLE_REFERENCE) {
            val pp = argument ?: CallableReferenceCall(kotlinCall, resolutionCallbacks.transformToLhsResult(kotlinCall), kotlinCall.name)

            CallableReferencesCandidateFactory(
                pp, callComponents, scopeTower, expectedType, resolutionCallbacks, callableReferenceResolver, baseSystem = baseConstraintSystem, diagnosticsHolder = diagnosticsHolder
            )
        } else {
            SimpleCandidateFactory(
                callComponents, scopeTower, kotlinCall, resolutionCallbacks, callableReferenceResolver
            )
        }
    }

    fun resolveCall(
        scopeTower: ImplicitScopeTower,
        resolutionCallbacks: KotlinResolutionCallbacks,
        kotlinCall: KotlinCall,
        expectedType: UnwrappedType?,
        collectAllCandidates: Boolean,
        baseConstraintSystem: NewConstraintSystem? = null,
        argument: CallableReferenceKotlinCallArgument? = null,
        createFactoryProviderForInvoke: (() -> CandidateFactoryProviderForInvoke<ResolutionCandidate>)? = null,
    ): CallResolutionResult {
        val candidateFactory = createFactory(scopeTower, kotlinCall, resolutionCallbacks, argument, expectedType, baseConstraintSystem)
        val candidates = resolveCallWithoutCompletion(
            scopeTower, resolutionCallbacks, kotlinCall, expectedType, collectAllCandidates, baseConstraintSystem, argument, null, candidateFactory, createFactoryProviderForInvoke
        )

        if (collectAllCandidates) {
            return kotlinCallCompleter.createAllCandidatesResult(candidates, expectedType, resolutionCallbacks)
        }

        return if (kotlinCall.callKind == KotlinCallKind.CALLABLE_REFERENCE) {
            kotlinCallCompleter.runCompletion(candidateFactory, candidates, expectedType, resolutionCallbacks)
        } else {
            choseMostSpecific(candidateFactory, resolutionCallbacks, expectedType, candidates)
        }
    }

    fun resolveCallWithoutCompletion(
        scopeTower: ImplicitScopeTower,
        resolutionCallbacks: KotlinResolutionCallbacks,
        kotlinCall: KotlinCall,
        expectedType: UnwrappedType?,
        collectAllCandidates: Boolean,
        baseConstraintSystem: NewConstraintSystem? = null,
        argument: CallableReferenceKotlinCallArgument? = null,
        diagnosticsHolder: KotlinDiagnosticsHolder? = null,
        candidateFactory: CandidateFactory<ResolutionCandidate>? = null,
        createFactoryProviderForInvoke: (() -> CandidateFactoryProviderForInvoke<ResolutionCandidate>)? = null,
    ): Collection<ResolutionCandidate> {
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

        kotlinCall.checkCallInvariants()

        assert(kotlinCall.callKind == KotlinCallKind.CALLABLE_REFERENCE || baseConstraintSystem == null) {
            "Base constraint system dependent resolution isn't supported for any calls except callable references"
        }

        val candidateFactory = candidateFactory ?: createFactory(scopeTower, kotlinCall, resolutionCallbacks, argument, expectedType, baseConstraintSystem, diagnosticsHolder)
        val processor = when (kotlinCall.callKind) {
            KotlinCallKind.VARIABLE -> {
                createVariableAndObjectProcessor(scopeTower, kotlinCall.name, candidateFactory, kotlinCall.explicitReceiver?.receiver)
            }
            KotlinCallKind.FUNCTION -> {
                createFunctionProcessor(
                    scopeTower,
                    kotlinCall.name,
                    candidateFactory,
                    createFactoryProviderForInvoke!!(),
                    kotlinCall.explicitReceiver?.receiver
                )
            }
            KotlinCallKind.CALLABLE_REFERENCE -> {
                createCallableReferenceProcessor(candidateFactory as CallableReferencesCandidateFactory)
            }
            KotlinCallKind.INVOKE -> {
                createProcessorWithReceiverValueOrEmpty(kotlinCall.explicitReceiver?.receiver) {
                    createCallTowerProcessorForExplicitInvoke(
                        scopeTower,
                        candidateFactory,
                        kotlinCall.dispatchReceiverForInvokeExtension?.receiver as ReceiverValueWithSmartCastInfo,
                        it
                    )
                }
            }
            KotlinCallKind.UNSUPPORTED -> throw UnsupportedOperationException()
        }

        if (collectAllCandidates) {
            val allCandidates = towerResolver.collectAllCandidates(scopeTower, processor, kotlinCall.name)
            return allCandidates
        }

        val candidates = towerResolver.runResolve(
            scopeTower,
            processor,
            useOrder = kotlinCall.callKind != KotlinCallKind.UNSUPPORTED,
            name = kotlinCall.name
        )

        return if (kotlinCall.callKind == KotlinCallKind.CALLABLE_REFERENCE) {
            callableReferenceResolver.callableReferenceOverloadConflictResolver.chooseMaximallySpecificCandidates(
                candidates as Collection<CallableReferenceCandidate>,
                CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS,
                discriminateGenerics = false // we can't specify generics explicitly for callable references
            ) as Set<CallableReferenceCandidate>
        } else candidates
    }

    fun resolveGivenCandidates(
        scopeTower: ImplicitScopeTower,
        resolutionCallbacks: KotlinResolutionCallbacks,
        kotlinCall: KotlinCall,
        expectedType: UnwrappedType?,
        givenCandidates: Collection<GivenCandidate>,
        collectAllCandidates: Boolean
    ): CallResolutionResult {
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled()

        kotlinCall.checkCallInvariants()
        val candidateFactory = SimpleCandidateFactory(
            callComponents, scopeTower, kotlinCall, resolutionCallbacks, callableReferenceResolver
        )

        val resolutionCandidates = givenCandidates.map { candidateFactory.createCandidate(it).forceResolution() }

        if (collectAllCandidates) {
            val allCandidates = towerResolver.runWithEmptyTowerData(
                KnownResultProcessor(resolutionCandidates),
                TowerResolver.AllCandidatesCollector(),
                useOrder = false
            )
            return kotlinCallCompleter.createAllCandidatesResult(allCandidates, expectedType, resolutionCallbacks)

        }
        val candidates = towerResolver.runWithEmptyTowerData(
            KnownResultProcessor(resolutionCandidates),
            TowerResolver.SuccessfulResultCollector(),
            useOrder = true
        )
        return choseMostSpecific(candidateFactory, resolutionCallbacks, expectedType, candidates)
    }

    private fun choseMostSpecific(
        candidateFactory: CandidateFactory<ResolutionCandidate>,
        resolutionCallbacks: KotlinResolutionCallbacks,
        expectedType: UnwrappedType?,
        candidates: Collection<ResolutionCandidate>
    ): CallResolutionResult {
        var refinedCandidates = candidates
        if (!callComponents.languageVersionSettings.supportsFeature(LanguageFeature.RefinedSamAdaptersPriority)) {
            val nonSynthesized = candidates.filter { !it.resolvedCall.candidateDescriptor.isSynthesized }
            if (!nonSynthesized.isEmpty()) {
                refinedCandidates = nonSynthesized
            }
        }

        var maximallySpecificCandidates = overloadingConflictResolver.chooseMaximallySpecificCandidates(
            refinedCandidates,
            CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS,
            discriminateGenerics = true // todo
        )

        if (
            maximallySpecificCandidates.size > 1 &&
            callComponents.languageVersionSettings.supportsFeature(LanguageFeature.OverloadResolutionByLambdaReturnType) &&
            candidates.all { resolutionCallbacks.inferenceSession.shouldRunCompletion(it) }
        ) {
            val candidatesWithAnnotation =
                candidates.filter { it.resolvedCall.candidateDescriptor.annotations.hasAnnotation(OVERLOAD_RESOLUTION_BY_LAMBDA_ANNOTATION_FQ_NAME) }
            val candidatesWithoutAnnotation = candidates - candidatesWithAnnotation
            if (candidatesWithAnnotation.isNotEmpty()) {
                val newCandidates = kotlinCallCompleter.chooseCandidateRegardingOverloadResolutionByLambdaReturnType(maximallySpecificCandidates, resolutionCallbacks)
                maximallySpecificCandidates = overloadingConflictResolver.chooseMaximallySpecificCandidates(
                    newCandidates,
                    CheckArgumentTypesMode.CHECK_VALUE_ARGUMENTS,
                    discriminateGenerics = true
                )

                if (maximallySpecificCandidates.size > 1 && candidatesWithoutAnnotation.any { it in maximallySpecificCandidates }) {
                    maximallySpecificCandidates = maximallySpecificCandidates.toMutableSet().apply { removeAll(candidatesWithAnnotation) }
                    maximallySpecificCandidates.singleOrNull()?.addDiagnostic(CandidateChosenUsingOverloadResolutionByLambdaAnnotation())
                }
            }
        }

        return kotlinCallCompleter.runCompletion(candidateFactory, maximallySpecificCandidates, expectedType, resolutionCallbacks)
    }
}

