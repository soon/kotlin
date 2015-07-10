package org.jetbrains.kotlin.gradle.tasks

import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.compile.BaseKotlinCompileSpec
import org.jetbrains.kotlin.gradle.compile.KotlinCompileSpec
import org.jetbrains.kotlin.gradle.compile.KotlinCompilerFactory


public open class KotlinCompile() : BaseKotlinCompile() {

    public class Spec : KotlinCompileSpec<K2JVMCompilerArguments>, BaseKotlinCompile.Spec()

    override fun createSpec(): BaseKotlinCompileSpec<K2JVMCompilerArguments> = Spec()

    override fun runCompiler(spec: BaseKotlinCompileSpec<K2JVMCompilerArguments>) {
        val specImpl = spec as Spec
        val compiler = KotlinCompilerFactory<K2JVMCompilerArguments>(getProject(), CompilerDaemonManager.getInstance()).newCompiler(specImpl)
        compiler.execute(specImpl)
    }
}


public open class Kotlin2JsCompile() : BaseKotlin2JsCompile() {

    public class Spec : KotlinCompileSpec<K2JSCompilerArguments>, BaseKotlin2JsCompile.Spec()

    override fun createSpec(): BaseKotlinCompileSpec<K2JSCompilerArguments> = Spec()

    override fun runCompiler(spec: BaseKotlinCompileSpec<K2JSCompilerArguments>) {
        val specImpl = spec as Spec
        val compiler = KotlinCompilerFactory<K2JSCompilerArguments>(getProject(), CompilerDaemonManager.getInstance()).newCompiler(specImpl)
        compiler.execute(specImpl)
    }
}

