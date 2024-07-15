// WITH_REFLECT

// example from the issue KT-68685
import kotlin.reflect.full.memberProperties

data class Response(
    val list: List<Data>,
) {
    data class Data(
        val id: String,
    )
}

val mp1 = Response::class.memberProperties.single()
val mp2 = Response.Data::class.memberProperties.single()

// simplified repro
class O {
    class K {
        inner class `!`
    }
}

val o = O::class.simpleName
val k = O.K::class.simpleName
val `!` = O.K.`!`::class.simpleName

val rv =  o!! + k!! + `!`!!

// expected: rv: OK!
