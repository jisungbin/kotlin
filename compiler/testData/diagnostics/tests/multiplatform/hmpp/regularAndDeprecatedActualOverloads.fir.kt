// LANGUAGE: +MultiPlatformProjects
// ISSUE: KT-69201, KT-69069

// MODULE: common
// TARGET_PLATFORM: Common
// FILE: common.kt

expect fun <T> Array<T>.f()

// MODULE: intermediate()()(common)
// FILE: intermediate.kt

fun <T> Array<out T>.f() {}

fun test() {
    Array(0) { _ -> "" }.f() // Disambiguation to regular `f`
}

// MODULE: target()()(intermediate)
// FILE: target.kt

@Deprecated("Doesn't affect resolving", level = DeprecationLevel.HIDDEN)
actual fun <T> Array<T>.f() {}
