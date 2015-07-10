package org.jetbrains.kotlin.gradle.tasks

import org.codehaus.groovy.runtime.MethodClosure
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.SourceDirectorySet
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.ExtraPropertiesExtension
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.compile.AbstractCompile
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.doc.KDocArguments
import org.jetbrains.kotlin.doc.KDocCompiler
import org.jetbrains.kotlin.gradle.compile.KotlinCompileLaunchingOptions
import org.jetbrains.kotlin.gradle.compile.BaseKotlinCompileSpec
import org.jetbrains.kotlin.gradle.tasks.compilers.GradleMessageCollector
import org.jetbrains.kotlin.utils.LibraryUtils
import java.io.File
import java.io.Serializable
import java.util.*
import kotlin.properties.Delegates

val DEFAULT_ANNOTATIONS = "org.jebrains.kotlin.gradle.defaultAnnotations"

abstract class AbstractKotlinCompile<T : CommonCompilerArguments>() : AbstractCompile() {

    abstract protected fun createSpec(): BaseKotlinCompileSpec<T>
    abstract protected fun createBlankArgs(): T

    open protected fun configure(spec: BaseKotlinCompileSpec<T>) {}

    public var kotlinOptions: T = createBlankArgs()
    public var kotlinDestinationDir: File? = getDestinationDir()

    private val logger = Logging.getLogger(this.javaClass)
    override fun getLogger() = logger

    @TaskAction
    override fun compile() {
        getLogger().debug("Starting ${javaClass} task")

        val spec = createSpec()
        spec.source = getSource().toArrayList()
        spec.destinationDir = getDestinationDir()
        spec.classpath = getClasspath().toArrayList()
        // not in api 1.6
        //        spec.setSourceCompatibility(getSourceCompatibility())
        //        spec.setTargetCompatibility(getTargetCompatibility())
        spec.kotlinCompilerArgs = kotlinOptions
        configure(spec)
        runCompiler(spec)
    }

    abstract protected fun runCompiler(spec: BaseKotlinCompileSpec<T>)
}


public abstract class BaseKotlinCompile() : AbstractKotlinCompile<K2JVMCompilerArguments>() {

    public open class Spec : BaseKotlinCompileSpec<K2JVMCompilerArguments>, Serializable {
        override var source: ArrayList<File> = arrayListOf()
        override var classpath: ArrayList<File> = arrayListOf()
        override var destinationDir: File? = null
        override var kotlinCompilerArgs: K2JVMCompilerArguments = K2JVMCompilerArguments()
        override val kotlinCompileLaunchingOptions = KotlinCompileLaunchingOptions()
        override var srcDirsSources: HashSet<SourceDirectorySet> = hashSetOf()
    }

    override fun createBlankArgs(): K2JVMCompilerArguments = K2JVMCompilerArguments()

    val srcDirsSources = HashSet<SourceDirectorySet>()

    override fun configure(spec: BaseKotlinCompileSpec<K2JVMCompilerArguments>) {
        spec.kotlinCompilerArgs.destination = if (kotlinOptions.destination?.isEmpty() ?: true) kotlinDestinationDir?.getPath()
        else kotlinOptions.destination

        val extraProperties = getExtensions().getExtraProperties()
        spec.kotlinCompileLaunchingOptions.pluginClasspaths = extraProperties.getOrNull<Array<String>>("compilerPluginClasspaths") ?: arrayOf()
        spec.kotlinCompileLaunchingOptions.compilerPluginArguments = extraProperties.getOrNull<Array<String>>("compilerPluginArguments") ?: arrayOf()
        spec.kotlinCompileLaunchingOptions.kaptAnnotationsFile = extraProperties.getOrNull<File>("kaptAnnotationsFile")
        spec.kotlinCompileLaunchingOptions.kaptStubsDir = extraProperties.getOrNull<File>("kaptStubsDir")
        spec.kotlinCompileLaunchingOptions.kaptInheritedAnnotations = extraProperties.getOrNull<Boolean>("kaptInheritedAnnotations") ?: false
        spec.kotlinCompileLaunchingOptions.embeddedAnnotations = getAnnotations(getProject(), getLogger()).toArrayList()
        spec.srcDirsSources = srcDirsSources
    }

    // override setSource to track source directory sets
    override fun setSource(source: Any?) {
        srcDirsSources.clear()
        if (source is SourceDirectorySet) srcDirsSources.add(source)
        super.setSource(source)
    }

    // override source to track source directory sets
    override fun source(vararg sources: Any?): SourceTask? {
        sources.forEach { if (it is SourceDirectorySet) srcDirsSources.add(it) }
        return super.source(sources)
    }
}


public abstract class BaseKotlin2JsCompile() : AbstractKotlinCompile<K2JSCompilerArguments>() {

    public open class Spec : BaseKotlinCompileSpec<K2JSCompilerArguments> {
        override var source: ArrayList<File> = arrayListOf()
        override var classpath: ArrayList<File> = arrayListOf()
        override var destinationDir: File? = null
        override var kotlinCompilerArgs: K2JSCompilerArguments = K2JSCompilerArguments()
        override val kotlinCompileLaunchingOptions = KotlinCompileLaunchingOptions()
        override var srcDirsSources: HashSet<SourceDirectorySet> = hashSetOf()
    }

    override fun createBlankArgs(): K2JSCompilerArguments {
        val args = K2JSCompilerArguments()
        args.libraryFiles = arrayOf<String>()  // defaults to null
        return args
    }

    public val outputFile: String?
        get() = kotlinOptions.outputFile

    public val sourceMapDestinationDir: File
        get() = File(outputFile).directory

    public val sourceMap: Boolean
        get() = kotlinOptions.sourceMap

    init {
        getOutputs().file(MethodClosure(this, "getOutputFile"))
    }

    override fun configure(spec: BaseKotlinCompileSpec<K2JSCompilerArguments>) {
        val kotlinJsLibsFromDependencies =
                getProject().getConfigurations().getByName("compile")
                        .filter { LibraryUtils.isKotlinJavascriptLibrary(it) }
                        .map { it.getAbsolutePath() }

        spec.kotlinCompilerArgs.libraryFiles = (kotlinOptions.libraryFiles + kotlinJsLibsFromDependencies).copyToArray()
    }
}


public open class KDoc() : SourceTask() {

    private val logger = Logging.getLogger(this.javaClass)
    override fun getLogger() = logger

    public var kdocArgs: KDocArguments = KDocArguments()

    public var destinationDir: File? = null

    init {
        // by default, output dir is not defined in options
        kdocArgs.docConfig.docOutputDir = ""
    }

    TaskAction fun generateDocs() {

        // \todo rewrite to dokk

        val args = KDocArguments()
        val cfg = args.docConfig

        val kdocOptions = kdocArgs.docConfig

        cfg.docOutputDir = if ((kdocOptions.docOutputDir.length() == 0) && (destinationDir != null)) {
            destinationDir!!.path
        } else {
            kdocOptions.docOutputDir
        }
        cfg.title = kdocOptions.title
        cfg.sourceRootHref = kdocOptions.sourceRootHref
        cfg.projectRootDir = kdocOptions.projectRootDir
        cfg.warnNoComments = kdocOptions.warnNoComments

        cfg.packagePrefixToUrls.putAll(kdocOptions.packagePrefixToUrls)
        cfg.ignorePackages.addAll(kdocOptions.ignorePackages)
        cfg.packageDescriptionFiles.putAll(kdocOptions.packageDescriptionFiles)
        cfg.packageSummaryText.putAll(kdocOptions.packageSummaryText)

        // KDoc compiler does not accept list of files as input. Try to pass directories instead.
        args.freeArgs = getSource().map { it.getParentFile()!!.getAbsolutePath() }
        // Drop compiled sources to temp. Why KDoc compiles anything after all?!
        args.destination = getTemporaryDir()?.getAbsolutePath()

        getLogger().warn(args.freeArgs.toString())
        val embeddedAnnotations = getAnnotations(getProject(), getLogger())
        val userAnnotations = (kdocArgs.annotations ?: "").split(File.pathSeparatorChar).toList()
        val allAnnotations = if (kdocArgs.noJdkAnnotations) userAnnotations else userAnnotations.plus(embeddedAnnotations.map { it.getPath() })
        args.annotations = allAnnotations.joinToString(File.pathSeparator)

        args.noStdlib = true
        args.noJdkAnnotations = true


        val compiler = KDocCompiler()

        val messageCollector = GradleMessageCollector(getLogger())
        val exitCode = compiler.exec(messageCollector, Services.EMPTY, args)

        when (exitCode) {
            ExitCode.COMPILATION_ERROR -> throw GradleException("Failed to generate kdoc. See log for more details")
            ExitCode.INTERNAL_ERROR -> throw GradleException("Internal generation error. See log for more details")
        }

    }
}

private fun <T: Any> ExtraPropertiesExtension.getOrNull(id: String): T? {
    try {
        @suppress("UNCHECKED_CAST")
        return get(id) as? T
    }
    catch (e: ExtraPropertiesExtension.UnknownPropertyException) {
        return null
    }
}

fun getAnnotations(project: Project, logger: Logger): Collection<File> {
    @suppress("UNCHECKED_CAST")
    val annotations = project.getExtensions().getByName(DEFAULT_ANNOTATIONS) as Collection<File>

    if (!annotations.isEmpty()) {
        logger.info("using default annontations from [${annotations.map { it.getPath() }}]")
        return annotations
    } else {
        throw GradleException("Default annotations not found in Kotlin gradle plugin classpath")
    }
}

