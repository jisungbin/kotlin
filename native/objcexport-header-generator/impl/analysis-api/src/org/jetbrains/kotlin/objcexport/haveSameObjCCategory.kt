package org.jetbrains.kotlin.objcexport

import org.jetbrains.kotlin.backend.konan.objcexport.ObjCClass
import org.jetbrains.kotlin.backend.konan.objcexport.ObjCInterface

internal fun haveSameObjCCategory(a: ObjCClass?, b: ObjCClass?): Boolean {
    return if (a is ObjCInterface && b is ObjCInterface) a.categoryName == b.categoryName
    else false
}