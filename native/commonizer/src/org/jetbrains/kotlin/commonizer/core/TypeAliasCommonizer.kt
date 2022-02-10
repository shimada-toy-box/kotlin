/*
 * Copyright 2010-2019 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.commonizer.core

import org.jetbrains.kotlin.commonizer.CommonizerSettings
import org.jetbrains.kotlin.commonizer.CommonizerTarget
import org.jetbrains.kotlin.commonizer.OptimisticNumberCommonizationEnabledKey
import org.jetbrains.kotlin.commonizer.allLeaves
import org.jetbrains.kotlin.commonizer.cir.*
import org.jetbrains.kotlin.commonizer.mergedtree.CirKnownClassifiers
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull

class TypeAliasCommonizer(
    typeCommonizer: TypeCommonizer,
    private val classifiers: CirKnownClassifiers,
    private val settings: CommonizerSettings,
) : NullableSingleInvocationCommonizer<CirTypeAlias> {

    private val typeCommonizer = typeCommonizer.withContext {
        withBackwardsTypeAliasSubstitutionEnabled(false)
    }

    override fun invoke(values: List<CirTypeAlias>): CirTypeAlias? {
        if (values.isEmpty()) return null

        val name = values.map { it.name }.distinct().singleOrNull() ?: return null

        val typeParameters = TypeParameterListCommonizer(typeCommonizer).commonize(values.map { it.typeParameters }) ?: return null

        val underlyingType = typeCommonizer.invoke(values.map { it.underlyingType }) as? CirClassOrTypeAliasType ?: return null

        val visibility = VisibilityCommonizer.lowering().commonize(values) ?: return null

        return CirTypeAlias.create(
            name = name,
            typeParameters = typeParameters,
            visibility = visibility,
            underlyingType = underlyingType,
            expandedType = underlyingType.expandedType(),
            annotations = listOfNotNull(
                createUnsafeNumberAnnotationIfNecessary(classifiers.classifierIndices.targets, settings, values) {
                    expandedType.classifierId
                }
            )
        )
    }
}

internal fun <T : CirHasAnnotations> createUnsafeNumberAnnotationIfNecessary(
    targets: List<CommonizerTarget>,
    settings: CommonizerSettings,
    values: List<T>,
    getIdOfPotentiallyUnsafeType: T.() -> CirEntityId?,
): CirAnnotation? {
    val isOptimisticCommonizationEnabled = settings.getSetting(OptimisticNumberCommonizationEnabledKey)

    if (!isOptimisticCommonizationEnabled)
        return null

    val typeIds = values.map { annotated -> annotated.getIdOfPotentiallyUnsafeType() }

    // All typealias have to be potentially substitutable (aka have to be some kind of number type)
    if (!typeIds.all { it != null && OptimisticNumbersTypeCommonizer.isOptimisticallySubstitutable(it) }) {
        return null
    }

    val actualPlatformTypes = mutableMapOf<String, CirEntityId>()
    values.forEachIndexed forEach@{ index, annotated ->
        val existingAnnotation = annotated.annotations.firstIsInstanceOrNull<UnsafeNumberAnnotation>()
        if (existingAnnotation != null) {
            actualPlatformTypes.putAll(existingAnnotation.actualPlatformTypes)
            return@forEach
        }

        targets[index].allLeaves().forEach { target ->
            actualPlatformTypes[target.name] = annotated.getIdOfPotentiallyUnsafeType()
                ?: throw IllegalStateException("Expect class or type alias type")
        }
    }

    if (actualPlatformTypes.values.distinct().size > 1) {
        return UnsafeNumberAnnotation(actualPlatformTypes)
    }

    return null
}

private class UnsafeNumberAnnotation(val actualPlatformTypes: Map<String, CirEntityId>) : CirAnnotation {
    override val type: CirClassType = UnsafeNumberAnnotation.type
    override val annotationValueArguments: Map<CirName, CirAnnotation> = emptyMap()

    override val constantValueArguments: Map<CirName, CirConstantValue> = mapOf(
        CirName.create("actualPlatformTypes") to CirConstantValue.ArrayValue(
            actualPlatformTypes.map { (platform, type) -> CirConstantValue.StringValue("$platform: ${type.toQualifiedNameString()}") }
        )
    )

    companion object {
        private val type = CirClassType.createInterned(
            classId = CirEntityId.create("kotlinx/cinterop/UnsafeNumber"),
            outerType = null,
            arguments = emptyList(),
            isMarkedNullable = false
        )

        val empty = UnsafeNumberAnnotation(emptyMap())
    }
}