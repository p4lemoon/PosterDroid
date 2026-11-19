package com.posterdroid.caml.parser

import com.posterdroid.caml.model.CamlLayerTree
import com.posterdroid.caml.model.CamlPackage
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

class TendiesExtractor(private val report: ParseReport? = null) {

    fun extract(file: File): CamlPackage {
        return file.inputStream().use { extract(it, file.name) }
    }

    fun extract(inputStream: InputStream, packageName: String = "TendiesPackage"): CamlPackage {
        var backgroundBytes: ByteArray? = null
        var floatingBytes: ByteArray? = null
        var foregroundBytes: ByteArray? = null
        val assets = mutableMapOf<String, ByteArray>()
        val metadata = mutableMapOf<String, String>()

        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (!entry.isDirectory && !name.contains("__MACOSX") && !name.endsWith(".DS_Store")) {
                    val data = zip.readBytes()

                    when {
                        name.endsWith("main.caml") -> {
                            when {
                                name.contains("Background", ignoreCase = true) -> backgroundBytes = data
                                name.contains("Floating", ignoreCase = true) -> floatingBytes = data
                                name.contains("Foreground", ignoreCase = true) -> foregroundBytes = data
                                backgroundBytes == null -> backgroundBytes = data
                            }
                        }
                        name.contains("/assets/") || name.startsWith("assets/") -> {
                            val assetRelativeName = if (name.startsWith("assets/")) name else "assets/" + name.substringAfter("/assets/")
                            assets[assetRelativeName] = data
                            assets[name.substringAfterLast("/")] = data

                            val decodedName = try { java.net.URLDecoder.decode(name, "UTF-8") } catch (e: Exception) { name }
                            if (decodedName != name) {
                                val decodedRelative = if (decodedName.startsWith("assets/")) decodedName else "assets/" + decodedName.substringAfter("/assets/")
                                assets[decodedRelative] = data
                                assets[decodedName.substringAfterLast("/")] = data
                            }
                        }
                        name.endsWith(".plist") || name.endsWith(".identifier") -> {
                            metadata[name.substringAfterLast("/")] = String(data, Charsets.UTF_8)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val parser = CamlXmlParser(report)

        val backgroundTree: CamlLayerTree? = backgroundBytes?.let {
            try {
                parser.parse(ByteArrayInputStream(it))
            } catch (t: Throwable) {
                report?.recordWarning("Failed to parse background CAML: ${t.message}")
                null
            }
        }

        val floatingTree: CamlLayerTree? = floatingBytes?.let {
            try {
                parser.parse(ByteArrayInputStream(it))
            } catch (t: Throwable) {
                report?.recordWarning("Failed to parse floating CAML: ${t.message}")
                null
            }
        }

        val foregroundTree: CamlLayerTree? = foregroundBytes?.let {
            try {
                parser.parse(ByteArrayInputStream(it))
            } catch (t: Throwable) {
                report?.recordWarning("Failed to parse foreground CAML: ${t.message}")
                null
            }
        }

        if (backgroundTree == null && floatingTree == null && foregroundTree == null) {
            throw IllegalArgumentException("Не найдены валидные CAML-слои в пакете $packageName")
        }

        return CamlPackage(
            name = packageName,
            background = backgroundTree,
            floating = floatingTree,
            foreground = foregroundTree,
            assets = assets,
            metadata = metadata
        )
    }
}
