/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.internal

import org.gradle.api.Project
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.jetbrains.kotlin.gradle.plugin.mpp.StaticLibrary
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.SwiftExportExtension
import org.jetbrains.kotlin.gradle.utils.LazyResolvedConfiguration
import org.jetbrains.kotlin.gradle.utils.newInstance
import org.jetbrains.kotlin.gradle.utils.setProperty

// Exported module declaration
abstract class SwiftExportedModule {
    @get:Input
    abstract val moduleName: Property<String>

    @get:Input
    @get:Optional
    abstract val flattenPackage: Property<String>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val artifacts: ConfigurableFileCollection
}

internal fun Project.swiftExportedModules(
    binary: StaticLibrary,
    swiftExportExtension: SwiftExportExtension,
): Provider<List<SwiftExportedModule>> {
    val exportedModules = objects.setProperty<SwiftExportExtension.ModuleExport>().convention(
        swiftExportExtension.exportedModules
    )

    val mainCompilation = binary.target.compilations.getByName("main")

    val rootModule = objects.newInstance<SwiftExportedModule>().apply {
        moduleName.set(swiftExportExtension.nameProvider)
        flattenPackage.set(swiftExportExtension.flattenPackageProvider)
        artifacts.from(mainCompilation.compileTaskProvider.map { it.outputFile.get() })
    }

    val configuration = LazyResolvedConfiguration(
        project.configurations.getByName(binary.exportConfigurationName)
    )

    return exportedModules.map { modules ->
        configuration.allResolvedDependencies.mapNotNull { resolvedDependency ->
            findAndCreateSwiftExportedModule(modules, resolvedDependency, configuration)
        }.toMutableList().apply {
            add(rootModule)
        }
    }
}

private fun Project.findAndCreateSwiftExportedModule(
    exportedModules: Set<SwiftExportExtension.ModuleExport>,
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

        objects.newInstance(SwiftExportedModule::class.java).apply {
            moduleName.set(module.moduleName ?: module.projectName.get())
            flattenPackage.set(module.flattenPackage)
            artifacts.from(dependencyArtifacts)
        }
    } else {
        null
    }
}