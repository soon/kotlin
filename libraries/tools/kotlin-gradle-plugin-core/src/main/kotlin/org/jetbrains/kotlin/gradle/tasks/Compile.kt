package org.jetbrains.kotlin.gradle.compile

import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.tasks.WorkResult
import org.gradle.api.tasks.compile.AbstractOptions
import org.gradle.api.tasks.compile.BaseForkOptions
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.tasks.compilers.BaseKotlinJSCompiler
import org.jetbrains.kotlin.gradle.tasks.compilers.KotlinGenericCompiler
import org.jetbrains.kotlin.gradle.tasks.compilers.BaseKotlinJVMCompiler
import java.io.File
import java.io.Serializable
import java.util.*


// base interfaces for specifications and compilers, used in place of analogous interfaces in gradle to avoid
// using internal classes and interfaces from gradle api in the common part. Particular gradle version specific implementations
// should implement also appropriate gradle internal interfaces to be usable with gradle api

// root interface for compile specification classes
public interface BaseSpec {}

// root interface for compilers; using invoke as a main method to avoid clash with gradle's compiler interface, which should
// be implemented too by descendants of this interface
public interface BaseCompiler<T: BaseSpec> {
    public fun invoke(spec: T): WorkResult
}


// gradle specific compile launching options
public class KotlinCompileLaunchingOptions : AbstractOptions(), Serializable {
    var failOnError: Boolean = true
    var listFiles: Boolean = false
    var pluginClasspaths: Array<String>? = null
    var compilerPluginArguments: Array<String>? = null
    var kaptAnnotationsFile: File? = null
    var kaptStubsDir: File? = null
    var kaptInheritedAnnotations: Boolean = false
    var embeddedAnnotations: ArrayList<File>? = null
    var isFork: Boolean = false
    var forkOptions: BaseForkOptions = BaseForkOptions()
    // todo: incremental, fork, etc
}


public interface BaseKotlinCompileSpec<T : CommonCompilerArguments> : BaseSpec {
    public var kotlinCompilerArgs: T
    public val kotlinCompileLaunchingOptions: KotlinCompileLaunchingOptions
    public var source: ArrayList<File>
    public var classpath: ArrayList<File>
    public var destinationDir: File?
    public var srcDirsSources: HashSet<SourceDirectorySet>
//    public val analysisMap: Map<File, File>
}

