/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp.apple.swiftexport.internal

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

internal interface SwiftExportedModuleMetadata {
    @get:Input
    val moduleName: Property<String>

    @get:Input
    @get:Optional
    val flattenPackage: Property<String>
}

// Exported module declaration
internal abstract class SwiftExportedModule : SwiftExportedModuleMetadata {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val artifacts: ConfigurableFileCollection
}