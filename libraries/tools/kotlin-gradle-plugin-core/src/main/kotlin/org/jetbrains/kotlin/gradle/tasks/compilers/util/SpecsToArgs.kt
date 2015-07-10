package org.jetbrains.kotlin.gradle.tasks.compilers.util

import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JSCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.compile.BaseKotlinCompileSpec
import java.util.*


val ANNOTATIONS_PLUGIN_NAME = "org.jetbrains.kotlin.kapt"

private fun java.io.File.isJavaFile() = extension.equals(com.intellij.ide.highlighter.JavaFileType.INSTANCE.getDefaultExtension(), ignoreCase = true)


private fun BaseKotlinCompileSpec<K2JVMCompilerArguments>.getJavaSourceRoots(): Set<java.io.File> =
        source
            .filter { it.isJavaFile() }
            .map { findSrcDirRoot(it) }
            .filterNotNull()
            .toSet()


fun BaseKotlinCompileSpec<K2JVMCompilerArguments>.findSrcDirRoot(file: java.io.File): java.io.File? {
    for (source in srcDirsSources) {
        for (root in source.getSrcDirs()) {
            if (com.intellij.openapi.util.io.FileUtil.isAncestor(root, file, false)) {
                return root
            }
        }
    }
    return null
}


internal fun<T: CommonCompilerArguments> T.updateFromCommonSpec(spec: BaseKotlinCompileSpec<T>, sources: List<java.io.File>) {
    freeArgs = sources.map { it.getAbsolutePath() }
    suppressWarnings = spec.kotlinCompilerArgs.suppressWarnings
    verbose = spec.kotlinCompilerArgs.verbose
    version = spec.kotlinCompilerArgs.version
    noInline = spec.kotlinCompilerArgs.noInline
}


internal fun K2JVMCompilerArguments.updateFromJvmSpec(spec: BaseKotlinCompileSpec<K2JVMCompilerArguments>, logger: org.gradle.api.logging.Logger) {
    // show kotlin compiler where to look for java source files
    logger.kotlinDebug("sources = ${spec.source}")
    val javaSourceRoots = spec.getJavaSourceRoots().map { it.getAbsolutePath() }
    //        logger.kotlinDebug("java sources roots = $javaSourceRoots")
    freeArgs = (freeArgs + javaSourceRoots.filterNotNull()).toSet().toList()
    logger.kotlinDebug("args.freeArgs = ${freeArgs}")

    if (spec.kotlinCompilerArgs.classpath?.isEmpty() ?: true) {
        classpath = spec.classpath.filter({ it != null && it.exists() }).joinToString(java.io.File.pathSeparator)
        logger.kotlinDebug("args.classpath = ${classpath}")
    }

    destination = if (spec.kotlinCompilerArgs.destination?.isEmpty() ?: true)
        spec.destinationDir?.getPath()
    else spec.kotlinCompilerArgs.destination
    logger.kotlinDebug("args.destination = ${destination}")

    pluginClasspaths = spec.kotlinCompileLaunchingOptions.pluginClasspaths ?: arrayOf()
    logger.kotlinDebug("args.pluginClasspaths = ${pluginClasspaths.joinToString(java.io.File.pathSeparator)}")
    val basePluginOptions = spec.kotlinCompileLaunchingOptions.compilerPluginArguments ?: arrayOf()

    val pluginOptions = arrayListOf(*basePluginOptions)

    handleKaptProperties(spec, pluginOptions)

    this.pluginOptions = pluginOptions.toTypedArray()
    logger.kotlinDebug("args.pluginOptions = ${pluginOptions.joinToString(java.io.File.pathSeparator)}")

    val embeddedAnnotations = spec.kotlinCompileLaunchingOptions.embeddedAnnotations
    val userAnnotations = spec.kotlinCompilerArgs.annotations?.split(java.io.File.pathSeparatorChar)?.toList() ?: emptyList()
    val allAnnotations: List<String> = if (spec.kotlinCompilerArgs.noJdkAnnotations || embeddedAnnotations == null) userAnnotations
                                       else userAnnotations.plus(embeddedAnnotations.map { it.getPath() })
    if (allAnnotations.any()) {
        annotations = allAnnotations.join(java.io.File.pathSeparator)
        logger.kotlinDebug("args.annotations = ${annotations}")
    }

    noStdlib = true
    noJdkAnnotations = true
    noInline = spec.kotlinCompilerArgs.noInline
    noOptimize = spec.kotlinCompilerArgs.noOptimize
    noCallAssertions = spec.kotlinCompilerArgs.noCallAssertions
    noParamAssertions = spec.kotlinCompilerArgs.noParamAssertions
}


private fun handleKaptProperties(spec: BaseKotlinCompileSpec<K2JVMCompilerArguments>, pluginOptions: ArrayList<String>) {
    spec.kotlinCompileLaunchingOptions.kaptAnnotationsFile?.let {
        if (it.exists()) it.delete()
        pluginOptions.add("plugin:$ANNOTATIONS_PLUGIN_NAME:output=" + it)
    }

    spec.kotlinCompileLaunchingOptions.kaptStubsDir?.let {
        pluginOptions.add("plugin:$ANNOTATIONS_PLUGIN_NAME:stubs=" + it)
    }

    if (spec.kotlinCompileLaunchingOptions.kaptInheritedAnnotations)
        pluginOptions.add("plugin:$ANNOTATIONS_PLUGIN_NAME:inherited=true")
}


internal fun K2JSCompilerArguments.updateFromJsSpec(spec: BaseKotlinCompileSpec<K2JSCompilerArguments>, logger: org.gradle.api.logging.Logger) {
    noStdlib = true
    outputFile = spec.kotlinCompilerArgs.outputFile
    outputPrefix = spec.kotlinCompilerArgs.outputPrefix
    outputPostfix = spec.kotlinCompilerArgs.outputPostfix
    metaInfo = spec.kotlinCompilerArgs.metaInfo

    //        val kotlinJsLibsFromDependencies =
    //                getProject().getConfigurations().getByName("compile")
    //                        .filter { LibraryUtils.isKotlinJavascriptLibrary(it) }
    //                        .map { it.getAbsolutePath() }
    //
    //        args.libraryFiles = (spec.kotlinCompilerArgs.libraryFiles + kotlinJsLibsFromDependencies).copyToArray()
    libraryFiles = spec.kotlinCompilerArgs.libraryFiles
    target = spec.kotlinCompilerArgs.target
    sourceMap = spec.kotlinCompilerArgs.sourceMap

    if (outputFile == null) {
        throw org.gradle.api.GradleException("compileKotlin2Js.kotlinOptions.outputFile should be specified.")
    }

    val outputDir = java.io.File(outputFile).directory
    if (!outputDir.exists()) {
        if (!outputDir.mkdirs()) {
            throw org.gradle.api.GradleException("Failed to create output directory ${outputDir} or one of its ancestors")
        }
    }

    logger.debug("Kotlin2JsCompiler set libraryFiles to ${libraryFiles.join(",")}")
    logger.debug("Kotlin2JsCompiler set outputFile to ${outputFile}")
}


fun org.gradle.api.logging.Logger.kotlinDebug(message: String) {
    this.debug("[KOTLIN] $message")
}