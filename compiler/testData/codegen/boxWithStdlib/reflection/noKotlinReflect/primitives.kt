// NO_KOTLIN_REFLECT

import kotlin.test.assertEquals
import kotlin.reflect.jvm.lite.*

fun box(): String {
    assertEquals("int", Int::class.java.getSimpleName())
    assertEquals("char", Char::class.java.getSimpleName())

    return "OK"
}