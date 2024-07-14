/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.internal.SwiftExportTaskParameters
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.internal.SwiftExportAction
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.internal.SwiftExportedModule
import org.jetbrains.kotlin.gradle.utils.LazyResolvedConfiguration
import org.jetbrains.kotlin.gradle.utils.newInstance
import javax.inject.Inject

@DisableCachingByDefault(because = "Swift Export is experimental, so no caching for now")
internal abstract class SwiftExportTask @Inject constructor(
    private val workerExecutor: WorkerExecutor,
    private val objectFactory: ObjectFactory,
) : DefaultTask() {
    internal abstract class DependencyModule {
        @get:Input
        abstract val moduleVersion: Property<ModuleVersionIdentifier>

        @get:Input
        @get:Optional
        abstract val moduleName: Property<String>

        @get:Input
        @get:Optional
        abstract val flattenPackage: Property<String>
    }

    internal abstract class DependencyInput {
        @get:Input
        abstract val configurationName: Property<String>

        @get:Input
        abstract val moduleName: Property<String>

        @get:Input
        abstract val flattenPackage: Property<String>

        @get:InputFile
        abstract val libraryFile: RegularFileProperty

        @get:Nested
        abstract val exportedModules: ListProperty<DependencyModule>
    }

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val swiftExportClasspath: ConfigurableFileCollection

    @get:Nested
    abstract val parameters: SwiftExportTaskParameters

    @get:Nested
    abstract val dependencyInput: DependencyInput

    @TaskAction
    fun run() {
        val swiftModules = swiftExportedModules()

        val swiftExportQueue = workerExecutor.classLoaderIsolation { workerSpec ->
            workerSpec.classpath.from(swiftExportClasspath)
        }

        swiftExportQueue.submit(SwiftExportAction::class.java) { workParameters ->
            workParameters.bridgeModuleName.set(parameters.bridgeModuleName)
            workParameters.konanDistribution.set(parameters.konanDistribution)
            workParameters.outputPath.set(parameters.outputPath)
            workParameters.stableDeclarationsOrder.set(parameters.stableDeclarationsOrder)
            workParameters.swiftModules.set(swiftModules)
            workParameters.swiftModulesFile.set(parameters.swiftModulesFile)
        }
    }

    private val configuration by lazy {
        LazyResolvedConfiguration(project.configurations.getByName(dependencyInput.configurationName.get()))
    }

    private fun swiftExportedModules(): List<SwiftExportedModule> {
        return configuration.allResolvedDependencies.mapNotNull { resolvedDependency ->
            findAndCreateSwiftExportedModule(dependencyInput.exportedModules.get(), resolvedDependency, configuration)
        }.toMutableList().apply {
            add(
                objectFactory.newInstance<SwiftExportedModule>().apply {
                    moduleName.set(dependencyInput.moduleName)
                    flattenPackage.set(dependencyInput.flattenPackage)
                    artifacts.from(dependencyInput.libraryFile)
                }
            )
        }
    }

    private fun findAndCreateSwiftExportedModule(
        exportedModules: List<DependencyModule>,
        resolvedDependency: ResolvedDependencyResult,
        configuration: LazyResolvedConfiguration,
    ): SwiftExportedModule? {
        val resolvedModule = resolvedDependency.selected.moduleVersion ?: return null
        val module = exportedModules.firstOrNull {
            val moduleVersion = it.moduleVersion.get()
            resolvedModule.name == moduleVersion.name &&
                    resolvedModule.group == moduleVersion.group &&
                    resolvedModule.version == moduleVersion.version
        }

        return if (module != null) {
            val dependencyArtifacts = configuration.getArtifacts(resolvedDependency).map { it.file }

            objectFactory.newInstance<SwiftExportedModule>().apply {
                moduleName.set(module.moduleName.orNull ?: module.moduleVersion.map { it.name }.get())
                flattenPackage.set(module.flattenPackage)
                artifacts.from(dependencyArtifacts)
            }
        } else {
            null
        }
    }
}