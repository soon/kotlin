package org.jetbrains.kotlin.gradle.compile

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.compile.CompileSpec
import org.gradle.api.internal.tasks.compile.Compiler
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonFactory
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager
import org.gradle.api.internal.tasks.compile.daemon.DaemonForkOptions
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.WorkResult
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.tasks.compilers.BaseKotlinJSCompiler
import org.jetbrains.kotlin.gradle.tasks.compilers.BaseKotlinJVMCompiler
import java.io.File


public interface KotlinCompileSpec<T : CommonCompilerArguments>: CompileSpec, BaseKotlinCompileSpec<T>


public class KotlinJVMCompiler : Compiler<KotlinCompileSpec<K2JVMCompilerArguments>>, BaseKotlinJVMCompiler() {
    override fun execute(spec: KotlinCompileSpec<K2JVMCompilerArguments>): WorkResult = invoke(spec)
}


public class KotlinJSCompiler : Compiler<KotlinCompileSpec<K2JSCompilerArguments>>, BaseKotlinJSCompiler() {
    override fun execute(spec: KotlinCompileSpec<K2JSCompilerArguments>): WorkResult = invoke(spec)
}

// to 2.2
//public class CleaningKotlinCompiler<T : CommonCompilerArguments>(private val compiler: Compiler<KotlinCompileSpec<T>>, private val taskOutputs: TaskOutputsInternal) : Compiler<KotlinCompileSpec<T>> {
//
//    override fun execute(spec: KotlinCompileSpec<T>?): WorkResult? {
//        val cleaner = SimpleStaleClassCleaner(taskOutputs)
//        cleaner.setDestinationDir(spec?.getDestinationDir())
//        cleaner.setSource(spec?.getSource())
//        cleaner.execute()
//        return compiler.execute(spec)
//    }
//}
//


public class DaemonKotlinCompiler<T : CommonCompilerArguments>(
        private val project: Project,
        private val delegate: Compiler<KotlinCompileSpec<T>>,
        val compilerDaemonFactory: CompilerDaemonFactory)
: Compiler<KotlinCompileSpec<T>> {

    override fun execute(spec: KotlinCompileSpec<T>): WorkResult {
        val forkOptions = spec.kotlinCompileLaunchingOptions.forkOptions
        val daemonForkOptions = DaemonForkOptions(forkOptions.getMemoryInitialSize(), forkOptions.getMemoryMaximumSize(), forkOptions.getJvmArgs(), emptyList<File>(), setOf("com.sun.tools.javac"))
        val daemon = compilerDaemonFactory.getDaemon(project as ProjectInternal?, daemonForkOptions)
        val result = daemon.execute(delegate, spec)
        if (result.isSuccess()) {
            return result
        }
        throw result.getException()
    }
}


public class KotlinCompilerFactory<T : CommonCompilerArguments>(private val project: Project, val compilerDaemonManager: CompilerDaemonManager?) {

    private val logger = Logging.getLogger(this.javaClass)

    public fun newCompiler(spec: KotlinCompileSpec<T>): Compiler<KotlinCompileSpec<T>> {
        val kotlinOptions = spec.kotlinCompileLaunchingOptions
        val kotlinCompiler: Compiler<KotlinCompileSpec<T>> =
                when (spec.kotlinCompilerArgs) {
                    is K2JVMCompilerArguments -> KotlinJVMCompiler() as Compiler<KotlinCompileSpec<T>>
                    is K2JSCompilerArguments -> KotlinJSCompiler() as Compiler<KotlinCompileSpec<T>>
                    else -> throw Exception("Unsupported kotlin compiler arguments type '${spec.kotlinCompilerArgs.javaClass}'")
                }
//        if (kotlinOptions.isFork) {
//            if (compilerDaemonManager != null) return DaemonKotlinCompiler(project, kotlinCompiler, compilerDaemonManager)
//            logger.info("Daemon manager not found, falling back to in-process compiler")
//        }
        return kotlinCompiler
    }
}
