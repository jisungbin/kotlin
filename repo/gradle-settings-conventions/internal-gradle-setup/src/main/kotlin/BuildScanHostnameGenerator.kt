/*
 * Copyright 2010-2024 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.build

import java.net.InetAddress
import java.security.MessageDigest

internal class BuildScanHostnameGenerator : SyntheticPropertiesGenerator {
    private fun calculateHostnameHash(salt: String): String {
        val hostname = System.getProperty("kotlin.build.hostname.for.tests") ?: InetAddress.getLocalHost().hostName
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val hash = messageDigest.digest((salt + hostname).toByteArray(Charsets.UTF_8))
        return hash.fold("") { str, it -> str + "%02x".format(it) }
    }

    override fun generate(setupFile: SetupFile): Map<String, String> {
        val salt = setupFile.obfuscationSalt ?: return emptyMap()
        return try {
            mapOf("kotlin.build.scan.hostname" to calculateHostnameHash(salt))
        } catch (e: Exception) {
            emptyMap()
        }
    }
}