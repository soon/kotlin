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

// TODO move to kotlin.reflect.jvm once we resolve name clash with kotlin-reflect.jar properly
package kotlin.jvm.reflect.fixme

import kotlin.jvm.internal.Reflection
import kotlin.jvm.internal.Intrinsic
import kotlin.jvm.internal.KJvmDeclarationContainer
import kotlin.reflect.KClass
import java.lang.Class

/**
 * Returns a Java [Class] instance corresponding to the given [KClass] instance.
 */
@Intrinsic("kotlin.KClass.java.property")
public val <T> KClass<T>.java: Class<T>
    get() = (this as KJvmDeclarationContainer).jClass as Class<T>

/**
 * Returns a [KClass] instance corresponding to the given Java [Class] instance.
 */
public val <T> Class<T>.kotlin: KClass<T>
    get() = Reflection.createKotlinClass(this) as KClass<T>

