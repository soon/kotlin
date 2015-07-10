package org.jetbrains.kotlin.gradle.tasks.compilers

import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.gradle.api.GradleException
import org.gradle.api.Nullable
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.tasks.WorkResult
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageLocation
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.gradle.compile.BaseCompiler
import org.jetbrains.kotlin.gradle.compile.BaseKotlinCompileSpec
import org.jetbrains.kotlin.gradle.tasks.compilers.util.updateFromCommonSpec
import org.jetbrains.kotlin.gradle.tasks.compilers.util.updateFromJsSpec
import org.jetbrains.kotlin.gradle.tasks.compilers.util.updateFromJvmSpec
import java.io.File
import java.io.Serializable


// copy from gradle's internal package
public class CompileResult(private val didWork: Boolean, Nullable public val exception: Throwable?) : WorkResult, Serializable {

    override fun getDidWork(): Boolean {
        return this.didWork
    }

    public fun isSuccess(): Boolean {
        return this.exception == null
    }
}


public abstract class KotlinGenericCompiler<T: CommonCompilerArguments> : BaseCompiler<BaseKotlinCompileSpec<T>>, Serializable {

    abstract protected fun compiler(): CLICompiler<in T>
    abstract protected fun createBlankArgs(): T
    open protected fun afterCompileHook(spec: BaseKotlinCompileSpec<T>, args: T, logger: Logger) {}
    abstract protected fun updateTargetSpecificArgs(spec: BaseKotlinCompileSpec<T>, args: T, logger: Logger)

    override fun invoke(spec: BaseKotlinCompileSpec<T>): WorkResult {

        val logger: Logger = Logging.getLogger(this.javaClass)

        logger.debug("Starting ${javaClass} task")
        val args = createBlankArgs()
        val sources = getKotlinSources(spec)
        if (sources.isEmpty()) {
            logger.warn("No Kotlin files found, skipping Kotlin compiler task")
            return CompileResult(true, null)
        }

        args.updateFromCommonSpec(spec, sources)
        updateTargetSpecificArgs(spec, args, logger)
        callCompiler(args, logger)
        afterCompileHook(spec, args, logger)
        return CompileResult(true, null)
    }

    private fun getKotlinSources(spec: BaseKotlinCompileSpec<T>): List<File> = spec.source.filter { it.isKotlinFile() }

    private fun File.isKotlinFile(): Boolean {
        return when (FilenameUtils.getExtension(getName()).toLowerCase()) {
            "kt", "kts" -> true
            else -> false
        }
    }

    private fun callCompiler(args: T, logger: Logger) {
        val messageCollector = GradleMessageCollector(logger)
        logger.debug("Calling compiler nn1")
        val exitCode = compiler().exec(messageCollector, Services.EMPTY, args)

        when (exitCode) {
            ExitCode.COMPILATION_ERROR -> throw GradleException("Compilation error. See log for more details")
            ExitCode.INTERNAL_ERROR -> throw GradleException("Internal compiler error. See log for more details")
        }
    }
}


public open class BaseKotlinJVMCompiler : KotlinGenericCompiler<K2JVMCompilerArguments>(), Serializable {
    override fun compiler(): CLICompiler<K2JVMCompilerArguments> = K2JVMCompiler()
    override fun createBlankArgs() = K2JVMCompilerArguments()

    override fun updateTargetSpecificArgs(spec: BaseKotlinCompileSpec<K2JVMCompilerArguments>, args: K2JVMCompilerArguments, logger: Logger) {
        args.updateFromJvmSpec(spec, logger)
    }

    override fun afterCompileHook(spec: BaseKotlinCompileSpec<K2JVMCompilerArguments>, args: K2JVMCompilerArguments, logger: Logger) {
        logger.debug("Copying resulting files to classes")

        // Copy kotlin classes to all classes directory
        val outputDirFile = File(args.destination!!)
        if (outputDirFile.exists()) {
            FileUtils.copyDirectory(outputDirFile, spec.destinationDir)
        }
    }

}


public open class BaseKotlinJSCompiler() : KotlinGenericCompiler<K2JSCompilerArguments>() {
    override fun compiler(): CLICompiler<K2JSCompilerArguments> = K2JSCompiler()

    override fun createBlankArgs(): K2JSCompilerArguments {
        val args = K2JSCompilerArguments()
        args.libraryFiles = arrayOf<String>()  // defaults to null
        return args
    }

    override fun updateTargetSpecificArgs(spec: BaseKotlinCompileSpec<K2JSCompilerArguments>, args: K2JSCompilerArguments, logger: Logger) {
        args.updateFromJsSpec(spec, logger)
    }
}


//public class KotlinSimpleJVMCompiler<T: K2JVMCompilerArguments> : Compiler<KotlinCompileSpec<T>>, Serializable {
//
//    private val logger = Logging.getLogger(this.javaClass)
//
//    override fun execute(spec: KotlinCompileSpec<T>): WorkResult {
//        val messageCollector = GradleMessageCollector(logger)
//        val compilerCfg = CompilerConfiguration()
//
//        spec.getSource().forEach { compilerCfg.addKotlinSourceRoot(it.toString()) }
//        spec.getSource().forEach { compilerCfg.addJavaSourceRoot(it) }
//        compilerCfg.addJvmClasspathRoots(spec.getClasspath().toList())
//        compilerCfg.addJvmClasspathRoots(PathUtil.getJdkClassesRoots())
//        compilerCfg.put<MessageCollector>(CLIConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
//
//        return runCompiler(compilerCfg, spec.getDestinationDir(), messageCollector, spec.kotlinCompilerArgs.includeRuntime)
//    }
//
//    private fun runCompiler(compilerCfg: CompilerConfiguration, destFolder: File, messageCollector: MessageCollector, includeRuntime: Boolean): WorkResult {
//
//        val rootDisposable = Disposer.newDisposable()
//
//        if (!destFolder.exists())
//            destFolder.mkdirs()
//
//        try {
//            val environment = KotlinCoreEnvironment.createForProduction(rootDisposable, compilerCfg, EnvironmentConfigFiles.JVM_CONFIG_FILES)
//            return SimpleWorkResult(KotlinToJVMBytecodeCompiler.compileBunchOfSources(environment, null, destFolder, includeRuntime))
//        } catch (e: Exception) {
//            logger.error("Compiler exception $e")
//            return SimpleWorkResult(false)
//        }
//    }
//}


class GradleMessageCollector(val logger: Logger) : MessageCollector {
    public override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageLocation) {
        val text = with(StringBuilder()) {
            append(when (severity) {
                in CompilerMessageSeverity.VERBOSE -> "v"
                in CompilerMessageSeverity.ERRORS -> "e"
                CompilerMessageSeverity.INFO -> "i"
                CompilerMessageSeverity.WARNING -> "w"
                else -> throw IllegalArgumentException("Unknown CompilerMessageSeverity: $severity")
            })
            append(": ")

            val path = location.path
            if (path != null) {
                append(path)
                append(": ")
                val line = location.line
                val column = location.column
                if (line > 0 && column > 0) append("($line, $column): ")
            }
            append(message)
            toString()
        }
        when (severity) {
            in CompilerMessageSeverity.VERBOSE -> logger.debug(text)
            in CompilerMessageSeverity.ERRORS -> logger.error(text)
            CompilerMessageSeverity.INFO -> logger.info(text)
            CompilerMessageSeverity.WARNING -> logger.warn(text)
            else -> throw IllegalArgumentException("Unknown CompilerMessageSeverity: $severity")
        }
    }
}


