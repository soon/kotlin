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

package kotlin.reflect.jvm.internal

import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.descriptors.CallableDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.load.java.sources.JavaSourceElement
import org.jetbrains.kotlin.load.java.structure.reflect.ReflectJavaClass
import org.jetbrains.kotlin.load.java.structure.reflect.createArrayType
import org.jetbrains.kotlin.platform.JavaToKotlinClassMap
import org.jetbrains.kotlin.resolve.DescriptorUtils
import org.jetbrains.kotlin.serialization.deserialization.descriptors.DeserializedClassDescriptor
import org.jetbrains.kotlin.types.JetType
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.Variance
import java.lang.reflect.*
import kotlin.reflect.KType
import kotlin.reflect.KotlinReflectionInternalError

class KTypeImpl(val type: JetType) : KType {
    override val isMarkedNullable: Boolean
        get() = type.isMarkedNullable
}

fun computeJavaType(type: JetType): Type? {
    if (KotlinBuiltIns.isArray(type)) {
        val elementTypeProjection = type.arguments.single()
        val componentType =
                if (elementTypeProjection.projectionKind == Variance.IN_VARIANCE) javaClass<Any>()
                else computeJavaType(elementTypeProjection.type) ?: throw KotlinReflectionInternalError("Malformed array type: $type")
        return (componentType as? Class<*>)?.createArrayType() ?: GenericArrayTypeImpl(componentType)
    }

    val classifier = type.constructor.declarationDescriptor
    if (classifier is TypeParameterDescriptor) return TypeVariableImpl(classifier)

    if (classifier !is ClassDescriptor) return null

    val arguments = type.arguments
    if (arguments.isNotEmpty()) return ParameterizedTypeImpl(classifier, arguments)

    return computeJavaClass(classifier)
}

fun computeJavaClass(descriptor: ClassDescriptor): Class<*> {
    when (descriptor) {
        is DeserializedClassDescriptor -> {
            // val source = descriptor.source
            // TODO: if source has KotlinJvmBinaryClass...
            TODO()
        }
        is JavaClassDescriptor -> return ((descriptor.source as JavaSourceElement).javaElement as ReflectJavaClass).element
    }

    // TODO: primitives, primitive arrays (see JetTypeMapper#mapBuiltinType)

    val classId = JavaToKotlinClassMap.INSTANCE.mapKotlinToJava(DescriptorUtils.getFqName(descriptor))
    if (classId != null) {
        val builtInClass = Class.forName(classId.asSingleFqName().asString())
        if (builtInClass != null) return builtInClass
    }

    throw KotlinReflectionInternalError("Class is not supported in Kotlin reflection: $descriptor")
}

class TypeVariableImpl(val descriptor: TypeParameterDescriptor) : TypeVariable<TypeVariableImpl.GenericDeclarationImpl> {
    class GenericDeclarationImpl(val parameters: List<TypeParameterDescriptor>) : GenericDeclaration {
        override fun getTypeParameters(): Array<out TypeVariable<*>> =
                parameters.map { computeJavaType(it.defaultType) as TypeVariableImpl }.toTypedArray()
    }

    override fun getBounds(): Array<out Type> =
            descriptor.upperBounds.map(::computeJavaType).filterNotNull().toTypedArray()

    override fun getGenericDeclaration(): GenericDeclarationImpl =
            GenericDeclarationImpl(with(descriptor.containingDeclaration) {
                when (this) {
                    is CallableDescriptor -> typeParameters
                    is ClassDescriptor -> typeConstructor.parameters
                    else -> throw KotlinReflectionInternalError("Unknown container for type parameter $name: $this")
                }
            })

    override fun getName(): String =
            descriptor.name.asString()
}

class ParameterizedTypeImpl(val descriptor: ClassDescriptor, val arguments: List<TypeProjection>) : ParameterizedType {
    override fun getRawType(): Type =
            computeJavaClass(descriptor)

    override fun getActualTypeArguments(): Array<out Type> =
            arguments.map { projection ->
                val type = computeJavaType(projection.type) ?: throw MalformedParameterizedTypeException()
                val variance = projection.projectionKind
                when {
                    variance == Variance.IN_VARIANCE -> WildcardTypeImpl(null, type)
                    variance == Variance.OUT_VARIANCE -> WildcardTypeImpl(type, null)
                    projection.isStarProjection -> WildcardTypeImpl(null, null)
                    else -> type
                }
            }.toTypedArray()

    override fun getOwnerType(): Type? = null // TODO: O<T>.I<S>
}

class WildcardTypeImpl(private val upperBound: Type?, private val lowerBound: Type?) : WildcardType {
    override fun getUpperBounds(): Array<out Type> =
            arrayOf(upperBound ?: javaClass<Any>())

    override fun getLowerBounds(): Array<out Type>? =
            lowerBound?.let { arrayOf(it) } ?: emptyArray()
}

class GenericArrayTypeImpl(private val componentType: Type) : GenericArrayType {
    override fun getGenericComponentType(): Type = componentType
}
