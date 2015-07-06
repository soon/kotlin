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

package org.jetbrains.kotlin.codegen.intrinsics

import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.Callable
import org.jetbrains.kotlin.codegen.ExpressionCodegen
import org.jetbrains.kotlin.codegen.StackValue
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.psi.JetClassLiteralExpression
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.org.objectweb.asm.Type
import org.jetbrains.org.objectweb.asm.commons.InstructionAdapter
import kotlin.reflect.KClass

public class KClassJavaProperty : IntrinsicPropertyGetter() {
    public override fun generate(resolvedCall: ResolvedCall<*>?, codegen: ExpressionCodegen, returnType: Type, receiver: StackValue): StackValue {
        val underlyingClass = resolvedCall!!.getExtensionReceiver().getType().getArguments()[0].getType()
        val underlyingType = codegen.getState().getTypeMapper().mapType(underlyingClass)

        val extensionReceiver = resolvedCall.getExtensionReceiver()

        if (extensionReceiver is ExpressionReceiver && extensionReceiver.getExpression() is JetClassLiteralExpression) {
            return StackValue.operation(returnType) { iv ->
                generateStaticTypeConst(iv, underlyingType)
                coerceToJavaLangClass(iv, returnType)
            }
        }
        else {
            return StackValue.operation(returnType) { iv ->
                generateDynamicTypeQuery(iv, receiver)
                coerceToJavaLangClass(iv, returnType)
            }
        }
    }

    private fun coerceToJavaLangClass(iv: InstructionAdapter, returnType: Type) {
        StackValue.coerce(AsmTypes.getType(javaClass<Class<Any>>()), returnType, iv)
    }

    private fun generateStaticTypeConst(iv: InstructionAdapter, type: Type) {
        if (AsmUtil.isPrimitive(type)) {
            iv.getstatic(AsmUtil.boxType(type).getInternalName(), "TYPE", "Ljava/lang/Class;")
        }
        else {
            iv.tconst(type)
        }
    }

    private fun generateDynamicTypeQuery(iv: InstructionAdapter, receiver: StackValue) {
        receiver.put(AsmTypes.getType(javaClass<KClass<Any>>()), iv)
        iv.invokeinterface("kotlin/jvm/internal/KJvmDeclarationContainer", "getjClass", "()Ljava/lang/Class;")
    }

}