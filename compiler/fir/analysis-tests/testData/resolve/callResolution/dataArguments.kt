class Arguments(val enabled: Boolean = true, val size: Int = 10)

fun button(text: String, data arguments: Arguments) { }

fun main() {
    button("Hello", enabled = true, size = 2)
    button("Hello", size = 2, enabled = true)
    button("Hello", enabled = true)
    button("Hello")

    button("Hello", enabled = true, <!ARGUMENT_PASSED_TWICE!>enabled<!> = false)
    button("Hello", <!NAMED_PARAMETER_NOT_FOUND!>incorrect<!> = 3)
    button("Hello", enabled = <!ARGUMENT_TYPE_MISMATCH!>3<!>)
}