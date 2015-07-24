
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

package org.jetbrains.kotlin.idea.framework

import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.idea.caches.JarMetaInformationIndex
import org.jetbrains.kotlin.idea.decompiler.isKotlinJvmCompiledFile
import org.jetbrains.kotlin.js.JavaScript
import org.jetbrains.kotlin.utils.KotlinJavascriptMetadataUtils
import kotlin.platform.platformStatic

public object KotlinJavaScriptLibraryDetectionUtil {

    platformStatic
    public fun isKotlinJavaScriptLibrary(library: Library): Boolean =
            isKotlinJavaScriptLibrary(library.getFiles(OrderRootType.CLASSES).toList())

    platformStatic
    public fun isKotlinJavaScriptLibrary(classesRoots: List<VirtualFile>): Boolean {
        // Prevent clashing with java runtime
        if (JavaRuntimeDetectionUtil.getJavaRuntimeVersion(classesRoots) != null) return false

        return classesRoots.any { !VfsUtilCore.processFilesRecursively(it, { isJsFileWithMetadata(it) }) }
    }

    private fun isJsFileWithMetadata(file: VirtualFile): Boolean =
            !file.isDirectory() &&
            JavaScript.EXTENSION == file.getExtension() &&
            KotlinJavascriptMetadataUtils.hasMetadata(String(file.contentsToByteArray(false)))

    public object KotlinJarJavascriptInfoIndex: JarMetaInformationIndex.JarInformationCollector<HasCompiledKotlinInJar.JarKotlinState> {
        public enum class JarKotlinState {
            HAS_KOTLIN,
            NO_KOTLIN,
            COUNTING
        }

        init {
            // TODO: bad - escaping this
            JarMetaInformationIndex.register(this)
        }

        override val key = Key.create<HasCompiledKotlinInJar.JarKotlinState>(HasCompiledKotlinInJar::class.simpleName!!)
        override val init = JarKotlinState.COUNTING
        override val stopAt = JarKotlinState.HAS_KOTLIN

        override fun count(result: JarKotlinState, nextFile: VirtualFile): JarKotlinState {
            if (result == JarKotlinState.HAS_KOTLIN) return JarKotlinState.HAS_KOTLIN

            return if (isKotlinJvmCompiledFile(nextFile)) JarKotlinState.HAS_KOTLIN else JarKotlinState.NO_KOTLIN
        }

        override fun countForSdkJar(file: VirtualFile): JarKotlinState = JarKotlinState.NO_KOTLIN
    }

}
