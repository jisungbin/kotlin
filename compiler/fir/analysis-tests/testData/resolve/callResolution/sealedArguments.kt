sealed interface Argument
data class Number(val n: Int): Argument
data class String(val s: kotlin.String): Argument

fun foo(sealed arg: Argument) { }

fun main() {
    foo(1)
    foo("Hello")
    foo(<!SEALED_ARGUMENT_NO_CONSTRUCTOR!>true<!>)
}
