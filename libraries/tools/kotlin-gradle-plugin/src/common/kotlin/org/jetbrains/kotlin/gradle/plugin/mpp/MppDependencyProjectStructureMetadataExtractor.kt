/*
 * Copyright 2010-2021 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.gradle.plugin.mpp

import java.io.File

sealed class MppDependencyProjectStructureMetadataExtractor {
    abstract fun getProjectStructureMetadata(): KotlinProjectStructureMetadata?

    companion object Factory
}

@Deprecated(
    message = "This class is not compatible with gradle project Isolation",
    replaceWith = ReplaceWith("ProjectMppDependencyProjectStructureMetadataExtractor")
)
internal class ProjectMppDependencyProjectStructureMetadataExtractorDeprecated(
    val projectPath: String,
    private val projectStructureMetadataProvider: () -> KotlinProjectStructureMetadata?,
) : MppDependencyProjectStructureMetadataExtractor() {

    override fun getProjectStructureMetadata(): KotlinProjectStructureMetadata? = projectStructureMetadataProvider()
}

internal class ProjectMppDependencyProjectStructureMetadataExtractor(
    val projectPath: String,
    private val projectStructureMetadataFile: File?,
) : MppDependencyProjectStructureMetadataExtractor() {

    override fun getProjectStructureMetadata(): KotlinProjectStructureMetadata? {
        return projectStructureMetadataFile?.let {
            parseKotlinSourceSetMetadataFromJson(projectStructureMetadataFile.readText())
        }
    }
}

internal open class JarMppDependencyProjectStructureMetadataExtractor(
    val primaryArtifactFile: File,
) : MppDependencyProjectStructureMetadataExtractor() {

    override fun getProjectStructureMetadata(): KotlinProjectStructureMetadata? {
        return if (primaryArtifactFile.name != EMPTY_PROJECT_STRUCTURE_METADATA_FILE_NAME) {
            parseKotlinSourceSetMetadataFromJson(primaryArtifactFile.readText())
        } else null
    }
}

internal class IncludedBuildMppDependencyProjectStructureMetadataExtractor(
    primaryArtifact: File,
    private val projectStructureMetadataProvider: () -> KotlinProjectStructureMetadata?,
    private val projectStructureMetadataFile: File? = null,
) : JarMppDependencyProjectStructureMetadataExtractor(primaryArtifact) {
    override fun getProjectStructureMetadata(): KotlinProjectStructureMetadata? =
        projectStructureMetadataFile?.let {
            parseKotlinSourceSetMetadataFromJson(projectStructureMetadataFile.readText())
        } ?: projectStructureMetadataProvider()
}
