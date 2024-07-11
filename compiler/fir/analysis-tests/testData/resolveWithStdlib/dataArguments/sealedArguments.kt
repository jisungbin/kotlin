@SealedArgument sealed interface Argument
data class Number(val n: Int): Argument
data class String(val s: kotlin.String): Argument

fun foo(sealed arg: Argument) { }

fun main() {
    foo(1)
    foo("Hello")
    foo(<!SEALED_ARGUMENT_NO_CONSTRUCTOR!>true<!>)
}

<!INCORRECT_SEALEDARG_CLASS!>@SealedArgument sealed interface IncorrectArgument<!>
data class OneNumber(val n: Int): IncorrectArgument
data class OtherNumber(val m: Int): IncorrectArgument

fun bar(<!SEALEDARG_PARAMETER_WRONG_CLASS!>sealed<!> arg: Int) { }
