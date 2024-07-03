/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport

import org.gradle.api.Action
import org.gradle.api.Named
import org.gradle.api.NamedDomainObjectSet
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.jetbrains.kotlin.gradle.dsl.multiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.cocoapods.supportedTargets
import org.jetbrains.kotlin.gradle.plugin.mpp.*
import org.jetbrains.kotlin.gradle.utils.*
import org.jetbrains.kotlin.swiftexport.ExperimentalSwiftExportDsl
import javax.inject.Inject

@ExperimentalSwiftExportDsl
@Suppress("unused", "MemberVisibilityCanBePrivate") // Public API
abstract class SwiftExportExtension @Inject constructor(
    private val project: Project,
    private val objects: ObjectFactory,
) {

    /**
     * Configure name of the swift export module from this project.
     */
    var moduleName: String? = null

    /**
     * Configure package collapsing rule.
     */
    var flattenPackage: String? = null

    /**
     * Configure binaries of the Swift Export built from this project.
     */
    fun binaries(configure: AbstractNativeLibrary.() -> Unit) {
        forAllSwiftExportBinaries(configure)
    }

    /**
     * Configure binaries of the Swift Export built from this project.
     */
    fun binaries(configure: Action<AbstractNativeLibrary>) = binaries {
        configure.execute(this)
    }

    /**
     * Configure Swift Export modules export.
     */
    fun export(dependency: Any, configure: ModuleExport.() -> Unit) {
        val dependencyProvider: Provider<Dependency> = when (dependency) {
            is Provider<*> -> dependency.map { dep ->
                when (dep) {
                    is Dependency -> dep
                    else -> project.dependencies.create(dep)
                }
            }
            else -> objects.providerWithLazyConvention { project.dependencies.create(dependency) }
        }

        _allSwiftExportBinaries.forEach { binary ->
            project.dependencies.addProvider(binary.exportConfigurationName, dependencyProvider)
        }

        val dependencyName = dependencyProvider.map {
            "${it.group}:${it.name}:${it.version}"
        }

        val dependencyId = dependencyProvider.map {
            object : ModuleVersionIdentifier {
                override fun getGroup() = it.group ?: "unspecified"
                override fun getName() = it.name
                override fun getVersion() = it.version ?: "unspecified"

                override fun getModule(): ModuleIdentifier = object : ModuleIdentifier {
                    override fun getGroup(): String = it.group ?: "unspecified"
                    override fun getName(): String = it.name
                }
            }
        }

        project.objects.newInstance(
            ModuleExport::class.java,
            dependencyName,
            dependencyId
        ).apply {
            configure()
            addToExportedModules(this)
        }
    }

    /**
     * Configure Swift Export modules export.
     */
    fun export(dependency: Any, configure: Action<ModuleExport>) = export(dependency) {
        configure.execute(this)
    }

    /**
     * Returns a list of exported modules.
     */
    val exportedModules: NamedDomainObjectSet<ModuleExport>
        get() = _exportedModules

    internal var flattenPackageProvider: Provider<String> = project.provider { flattenPackage }
    internal val nameProvider: Provider<String> = project.provider { moduleName ?: project.name }

    private val _exportedModules = project.container(ModuleExport::class.java)

    private fun addToExportedModules(module: ModuleExport) {
        check(_exportedModules.findByName(module.name) == null) { "Project already has Export module with name ${module.name}" }
        _exportedModules.add(module)
    }

    private val _allSwiftExportBinaries
        get() = project.multiplatformExtension.supportedTargets().flatMap { target ->
            target.binaries
                .matching { it.name.startsWith(SwiftExportDSLConstants.SWIFT_EXPORT_LIBRARY_PREFIX) }
                .withType(StaticLibrary::class.java)
        }

    private fun forAllSwiftExportBinaries(action: Action<in AbstractNativeLibrary>) {
        _allSwiftExportBinaries.forEach {
            action.execute(it)
        }
    }

    abstract class ModuleExport @Inject constructor(
        @get:Input val dependencyName: Provider<String>,
        @get:Input val moduleVersion: Provider<ModuleVersionIdentifier>
    ) : Named {
        @get:Input
        var moduleName: String? = null

        @get:Input
        var flattenPackage: String? = null

        override fun getName(): String = dependencyName.get()
    }
}