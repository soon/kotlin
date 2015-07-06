import kotlin.test.*
import kotlin.reflect.jvm.*
import kotlin.jvm.reflect.fixme.*

fun box(): String {
    val any = Array<Any>::class
    val string = Array<String>::class

    assertNotEquals(any, string)
    assertNotEquals(any.java, string.java)

    return "OK"
}
