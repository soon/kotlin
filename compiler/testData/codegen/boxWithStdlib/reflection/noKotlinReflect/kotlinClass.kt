// NO_KOTLIN_REFLECT

import java.lang.Error
import kotlin.test.assertEquals
import kotlin.test.fail
import kotlin.jvm.reflect.fixme.*

class Klass

inline fun failsWithError(block: () -> Unit) {
    try {
        block()
        fail()
    }
    catch (e: Error) {
        return
    }
}

fun box(): String {
    val kClass = Klass::class
    val jClass = kClass.java
    val kkClass = jClass.kotlin
    val jjClass = kkClass.java

    assertEquals("Klass", jjClass.getSimpleName())

    return "OK"
}