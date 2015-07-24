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

package org.jetbrains.kotlin.idea.caches

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.HashSet
import com.intellij.util.indexing.*
import com.intellij.util.io.ExternalIntegerKeyDescriptor
import org.jetbrains.kotlin.idea.decompiler.isKotlinJvmCompiledFile
import java.util.ArrayList

public object JarMetaInformationIndex : ScalarIndexExtension<Int>() {
    override fun dependsOnFileContent(): Boolean = true
    override fun getKeyDescriptor() = ExternalIntegerKeyDescriptor()
    override fun getName(): ID<Int, Void> = ID.create<Int, Void>(JarMetaInformationIndex::class.qualifiedName)
    override fun getVersion(): Int = 0
    override fun getInputFilter() = FileBasedIndex.InputFilter() { file ->
        val url = file.getUrl()
        url.startsWith("jar://") && url.endsWith(".class")
    }

    private val collectors: MutableList<JarInformationCollector<*>> = ArrayList()

    public fun register(collector: JarInformationCollector<*>) {
        collectors.add(collector)
    }


    override fun getIndexer() = INDEXER

    private val INDEXER = DataIndexer<Int, Void, FileContent>() { inputData: FileContent ->
        val jarFile = findJarFile(inputData.getFile())
        if (jarFile != null) {
            collectors.forEach { collector ->
                jarFile.putUserData(collector.key, null)
            }
        }

        mapOf()
    }


    // Sdk list can be outdated if some new jdks are added
    val allJDKRoots = ProjectJdkTable.getInstance().getAllJdks().flatMapTo(HashSet<VirtualFile>()) { jdk ->
        jdk.rootProvider.getFiles(OrderRootType.CLASSES).toList()
    }

    public fun <T: Any> getValue(collector: JarInformationCollector<T>, file: VirtualFile): T? {
        val jarFile = findJarFile(file) ?: return null

        if (VfsUtilCore.isUnder(jarFile, allJDKRoots)) return collector.countForSdkJar(jarFile)

        val kotlinState = jarFile.getUserData(collector.key)
        if (kotlinState != null) {
            return kotlinState
        }

        // println("Scheduled: $jarFile ${jarFile.hashCode()}")
        scheduleCountJarState(collector, jarFile)

        return null
    }

    private fun findJarFile(file: VirtualFile): VirtualFile? {
        if (!file.getUrl().startsWith("jar://")) return null

        var jarFile = file
        while (jarFile.getParent() != null) jarFile = jarFile.getParent()

        return jarFile
    }

    private fun <T: Any> scheduleCountJarState(collector: JarInformationCollector<T>, jarFile: VirtualFile) {
        var result = collector.init
        jarFile.putUserData(collector.key, result)

        ApplicationManager.getApplication().executeOnPooledThread {
            VfsUtilCore.processFilesRecursively(jarFile) { file ->
                result = collector.count(result, file)

                // Continue while no result found
                result != collector.stopAt
            }

            // println("Finished: $jarFile $result ${jarFile.hashCode()}")
            jarFile.putUserData(collector.key, result)
        }
    }


    interface JarInformationCollector<T> {
        val key: Key<T>
        val init: T
        val stopAt: T

        fun count(result: T, nextFile: VirtualFile): T
        fun countForSdkJar(file: VirtualFile): T
    }
}

public object HasCompiledKotlinInJar: JarMetaInformationIndex.JarInformationCollector<HasCompiledKotlinInJar.JarKotlinState> {
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

