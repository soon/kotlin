/*
 * Copyright 2010-2016 JetBrains s.r.o.
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

package org.jetbrains.kotlin.idea.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.diagnostics.Diagnostic
import org.jetbrains.kotlin.idea.refactoring.changeSignature.KotlinChangeSignatureConfiguration
import org.jetbrains.kotlin.idea.refactoring.changeSignature.runChangeSignature
import org.jetbrains.kotlin.idea.search.usagesSearch.constructor
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.getNonStrictParentOfType

class AddDataClassPrimaryConstructorParametersFix(element: KtClass) : KotlinQuickFixAction<KtClass>(element) {
    override fun getText() = familyName
    override fun getFamilyName() = "Add primary constructor parameters"

    override fun invoke(project: Project, editor: Editor?, file: KtFile) {
        val constructorDescriptor = element.constructor ?: return
        val configuration = KotlinChangeSignatureConfiguration.Empty

        runChangeSignature(project, constructorDescriptor, configuration, element, text)
    }

    companion object : KotlinSingleIntentionActionFactory() {
        public override fun createAction(diagnostic: Diagnostic): AddDataClassPrimaryConstructorParametersFix? {
            return diagnostic.psiElement.getNonStrictParentOfType<KtClass>()?.let(::AddDataClassPrimaryConstructorParametersFix)
        }
    }
}
