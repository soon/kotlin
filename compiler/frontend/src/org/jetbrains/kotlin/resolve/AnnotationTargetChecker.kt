/*
 * Copyright 2010-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.resolve

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.ClassKind
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.diagnostics.Errors
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getAnnotationEntries
import org.jetbrains.kotlin.resolve.constants.ArrayValue
import org.jetbrains.kotlin.resolve.constants.EnumValue
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.TypeUtils
import java.lang.annotation.ElementType
import java.util.*
import org.jetbrains.kotlin.descriptors.annotations.AnnotationTarget
import kotlin.annotation

public object AnnotationTargetChecker {

    private val PROPERTY_EXTENDED_TARGETS = listOf(
            AnnotationTarget.FIELD, AnnotationTarget.PROPERTY_GETTER, AnnotationTarget.PROPERTY_SETTER)
    private val PARAMETER_EXTENDED_TARGETS = PROPERTY_EXTENDED_TARGETS + AnnotationTarget.PROPERTY

    public fun check(annotated: JetAnnotated, trace: BindingTrace, descriptor: ClassDescriptor? = null) {
        if (annotated is JetTypeParameter) return // TODO: support type parameter annotations
        val actualTargets = getActualTargetList(annotated, descriptor)
        for (entry in annotated.getAnnotationEntries()) {
            checkAnnotationEntry(entry, actualTargets, trace)
        }
        if (annotated is JetCallableDeclaration) {
            annotated.getTypeReference()?.let { check(it, trace) }
        }
        if (annotated is JetFunction) {
            for (parameter in annotated.getValueParameters()) {
                if (!parameter.hasValOrVar()) {
                    check(parameter, trace)
                    if (annotated is JetFunctionLiteral) {
                        parameter.getTypeReference()?.let { check(it, trace) }
                    }
                }
            }
        }
        if (annotated is JetClassOrObject) {
            for (initializer in annotated.getAnonymousInitializers()) {
                check(initializer, trace)
            }
        }
    }

    public fun checkExpression(expression: JetExpression, trace: BindingTrace) {
        for (entry in expression.getAnnotationEntries()) {
            checkAnnotationEntry(entry, targetList(AnnotationTarget.EXPRESSION), trace)
        }
        if (expression is JetFunctionLiteralExpression) {
            for (parameter in expression.getValueParameters()) {
                parameter.getTypeReference()?.let { check(it, trace) }
            }
        }
    }

    public fun possibleTargetSet(classDescriptor: ClassDescriptor): Set<AnnotationTarget>? {
        val targetEntryDescriptor = classDescriptor.getAnnotations().findAnnotation(KotlinBuiltIns.FQ_NAMES.target)
                                    ?: return null
        val valueArguments = targetEntryDescriptor.getAllValueArguments()
        val valueArgument = valueArguments.entrySet().firstOrNull()?.getValue() as? ArrayValue ?: return null
        return valueArgument.value.filterIsInstance<EnumValue>().map {
            AnnotationTarget.valueOrNull(it.value.getName().asString())
        }.filterNotNull().toSet()
    }

    private fun possibleTargetSet(entry: JetAnnotationEntry, trace: BindingTrace): Set<AnnotationTarget> {
        val descriptor = trace.get(BindingContext.ANNOTATION, entry) ?: return AnnotationTarget.DEFAULT_TARGET_SET
        // For descriptor with error type, all targets are considered as possible
        if (descriptor.getType().isError()) return AnnotationTarget.ALL_TARGET_SET
        val classDescriptor = TypeUtils.getClassDescriptor(descriptor.getType()) ?: return AnnotationTarget.DEFAULT_TARGET_SET
        return possibleTargetSet(classDescriptor) ?: AnnotationTarget.DEFAULT_TARGET_SET
    }

    private fun checkAnnotationEntry(entry: JetAnnotationEntry, actualTargets: ExtendedTargetList, trace: BindingTrace) {
        val possibleTargets = possibleTargetSet(entry, trace)
        val target = entry.getUseSiteTarget()?.getAnnotationUseSiteTarget()

        if (actualTargets.base.any {
            it in possibleTargets && (target == null || AnnotationTarget.USE_SITE_MAPPING[target] == it)
        }) return

        if (target != null && actualTargets.extended.any {
            it in possibleTargets && AnnotationTarget.USE_SITE_MAPPING[target] == it
        }) return

        trace.report(Errors.WRONG_ANNOTATION_TARGET.on(entry, actualTargets.base.firstOrNull()?.description ?: "unidentified target"))
    }

    private fun getActualTargetList(annotated: JetAnnotated, descriptor: ClassDescriptor?): ExtendedTargetList {
        return when (annotated) {
            is JetClassOrObject -> {
                if (annotated is JetEnumEntry) {
                    targetList(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
                }
                else if (descriptor?.getKind() == ClassKind.ANNOTATION_CLASS) {
                    targetList(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.CLASSIFIER)
                }
                else {
                    targetList(AnnotationTarget.CLASSIFIER)
                }
            }
            is JetProperty -> targetList(PROPERTY_EXTENDED_TARGETS,
                                         if (annotated.isLocal()) AnnotationTarget.LOCAL_VARIABLE else AnnotationTarget.PROPERTY)
            is JetParameter -> targetList(PARAMETER_EXTENDED_TARGETS,
                                          if (annotated.hasValOrVar()) AnnotationTarget.PROPERTY else AnnotationTarget.VALUE_PARAMETER)
            is JetConstructor<*> -> targetList(AnnotationTarget.CONSTRUCTOR)
            is JetFunction -> targetList(listOf(AnnotationTarget.VALUE_PARAMETER), AnnotationTarget.FUNCTION)
            is JetPropertyAccessor -> {
                targetList(if (annotated.isGetter()) AnnotationTarget.PROPERTY_GETTER else AnnotationTarget.PROPERTY_SETTER)
            }
            is JetPackageDirective -> targetList(AnnotationTarget.PACKAGE)
            is JetTypeReference -> targetList(AnnotationTarget.TYPE)
            is JetFile -> targetList(AnnotationTarget.FILE)
            is JetTypeParameter -> targetList(AnnotationTarget.TYPE_PARAMETER)
            else -> targetList()
        }
    }

    private class ExtendedTargetList(val base: List<AnnotationTarget>, val extended: List<AnnotationTarget>)

    private fun targetList(vararg target: AnnotationTarget): ExtendedTargetList {
        return ExtendedTargetList(listOf(*target), emptyList())
    }

    private fun targetList(extended: List<AnnotationTarget>, vararg target: AnnotationTarget): ExtendedTargetList {
        return ExtendedTargetList(listOf(*target), extended)
    }
}