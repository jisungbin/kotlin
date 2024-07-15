package org.jetbrains.kotlin.objcexport

import org.jetbrains.kotlin.analysis.api.symbols.KaClassSymbol
import org.jetbrains.kotlin.analysis.api.types.symbol
import org.jetbrains.kotlin.backend.konan.objcexport.ObjCClass
import org.jetbrains.kotlin.backend.konan.objcexport.ObjCInterface
import org.jetbrains.kotlin.backend.konan.objcexport.ObjCInterfaceImpl

private const val extensionsCategoryName = "Extensions"

internal val ObjCInterface.isExtensionsFacade: Boolean
    get() = this.categoryName == extensionsCategoryName

/**
 * Translates extension functions/properties inside the given [this] file as a single [ObjCInterface]
 * with category [extensionsCategoryName]
 *
 * Later interface should be forwarded using [isExtensionsFacade]
 *
 * ## example:
 * given a file "Foo.kt"
 *
 * ```kotlin
 *
 * fun Foo.func() = 42
 *
 * val Foo.prop get() = 42
 *
 * class Foo {
 *
 * }
 *
 * ```
 *
 * This will be exporting two Interfaces with forwarded class:
 *
 * ```
 * @class Foo
 *
 * @interface Foo: Base
 *
 * @interface Foo (Extensions)
 *      - func
 *      - prop
 * ```
 *
 * Where `Foo` would be the "top level interface file extensions facade" returned by this function.
 *
 * ## What function returns
 * Function returns extended type(s) and extension facade(s). So it doesn't include duplicates verifications and
 * if extended type is translated already it will be filtered out by [KtObjCExportHeaderGenerator]
 * See [org.jetbrains.kotlin.objcexport.KtObjCExportHeaderGenerator.addObjCStubIfNotTranslated]
 *
 * See related [translateToObjCTopLevelFacade]
 */
fun ObjCExportContext.translateToObjCExtensionFacades(file: KtResolvedObjCExportFile): List<ObjCClass> {
    val extensions = file.callableSymbols
        .filter { analysisSession.getClassIfCategory(it) != null && it.isExtension }
        .sortedWith(StableCallableOrder)
        .ifEmpty { return emptyList() }
        .groupBy {
            val type = it.receiverParameter?.type
            if (analysisSession.isMappedObjCType(type)) return@groupBy null
            else {
                /**
                 * Mapped types extensions should be handled as top level facades
                 * @see [translateToObjCTopLevelFacade] and [isExtensionOfMappedObjCType]
                 * @see [org.jetbrains.kotlin.backend.konan.objcexport.ObjCExportMapper.getClassIfCategory] which K1 uses
                 * to differentiate extensions and top level callables
                 */
                val expandedSymbol = with(analysisSession) { type?.expandedSymbol }
                if (expandedSymbol == null) return@groupBy null
                else getObjCClassOrProtocolName(expandedSymbol).objCName
            }
        }

    return extensions.mapNotNull { (objCName, extensionSymbols) ->

        val extensionTypes = extensionSymbols
            .map { it.receiverParameter?.type }
            .mapNotNull { it?.symbol as? KaClassSymbol }
            .mapNotNull { translateToObjCClass(it) }

        extensionTypes + listOf(
            ObjCInterfaceImpl(
                name = objCName ?: return@mapNotNull null,
                comment = null,
                origin = null,
                attributes = emptyList(),
                superProtocols = emptyList(),
                members = extensionSymbols.flatMap { ext -> translateToObjCExportStub(ext) },
                categoryName = extensionsCategoryName,
                generics = emptyList(),
                superClass = null,
                superClassGenerics = emptyList()
            )
        )
    }.flatten()
}