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

package kotlin.jvm.internal

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KProperty2

public class ClassReference(public override val jClass: Class<Any>) : KClass<Any>, KJvmDeclarationContainer {
    public override val simpleName: String?
        get() = throw KotlinReflectionNotSupportedError()

    public override val qualifiedName: String?
        get() = throw KotlinReflectionNotSupportedError()

    public override val properties: Collection<KProperty1<Any, *>>
        get() = throw KotlinReflectionNotSupportedError()

    public override val extensionProperties: Collection<KProperty2<Any, *, *>>
        get() = throw KotlinReflectionNotSupportedError()

    override fun toString(): String = jClass.toString()
}