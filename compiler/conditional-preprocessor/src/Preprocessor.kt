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

package org.jetbrains.kotlin.preprocessor

import com.intellij.openapi.Disposable
import com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.idea.JetFileType
import org.jetbrains.kotlin.psi.*
import java.io.File


fun main(args: Array<String>) {
    require(args.size() == 1, "Please specify path to sources")

    val sourcePath = File(args.first())

    val configuration = CompilerConfiguration()
    val environment = KotlinCoreEnvironment.createForProduction(Disposable {  }, configuration, emptyList())

    val project = environment.project
    val psiFileFactory = PsiFileFactory.getInstance(project)
    val fileType = JetFileType.INSTANCE

    FileTreeWalk(sourcePath).forEach { sourceFile ->
        if (sourceFile.isFile && sourceFile.extension == fileType.defaultExtension) {
            val psiFile = psiFileFactory.createFileFromText(sourceFile.name, fileType, sourceFile.readText()) as JetFile
            processDeclarations(sourceFile.name, psiFile.declarations)
        }
    }

}

fun processDeclarations(context: String, declarations: List<JetDeclaration>) {
    for (declaration in declarations)
    {
        val annotations = declaration.annotationEntries
        val name = (declaration as? JetNamedDeclaration)?.nameAsSafeName
        println("$context, declaration: $name, annotations: ${annotations.joinToString() { it.text }}")
        if (declaration is JetDeclarationContainer) {
            processDeclarations("$context::$name", declaration.declarations)
        }
    }
}