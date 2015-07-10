package org.jetbrains.kotlin.gradle.tasks

import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.compile.BaseKotlinCompileSpec
import org.jetbrains.kotlin.gradle.tasks.BaseKotlin2JsCompile
import org.jetbrains.kotlin.gradle.tasks.BaseKotlinCompile
import org.jetbrains.kotlin.gradle.compile.KotlinCompileSpec
import org.jetbrains.kotlin.gradle.compile.KotlinCompilerFactory
import java.io.Serializable


public open class KotlinCompile() : BaseKotlinCompile() {

    public class Spec : KotlinCompileSpec<K2JVMCompilerArguments>, BaseKotlinCompile.Spec(), Serializable

    override fun createSpec(): BaseKotlinCompileSpec<K2JVMCompilerArguments> = Spec()

    override fun runCompiler(spec: BaseKotlinCompileSpec<K2JVMCompilerArguments>) {
        val specImpl = spec as Spec
        val compilerDaemonManager = getServices().get(javaClass<CompilerDaemonManager>())
        val compiler = KotlinCompilerFactory<K2JVMCompilerArguments>(getProject(), compilerDaemonManager).newCompiler(specImpl)
        compiler.execute(specImpl)
    }
}


public open class Kotlin2JsCompile() : BaseKotlin2JsCompile() {

    public class Spec : KotlinCompileSpec<K2JSCompilerArguments>, BaseKotlin2JsCompile.Spec(), Serializable

    override fun createSpec(): BaseKotlinCompileSpec<K2JSCompilerArguments> = Spec()

    override fun runCompiler(spec: BaseKotlinCompileSpec<K2JSCompilerArguments>) {
        val specImpl = spec as Spec
        val compilerDaemonManager = getServices().get(javaClass<CompilerDaemonManager>())
        val compiler = KotlinCompilerFactory<K2JSCompilerArguments>(getProject(), compilerDaemonManager).newCompiler(specImpl)
        compiler.execute(specImpl)
    }
}

