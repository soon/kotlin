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
    val kjClass = Klass::class.java

    assertEquals("Klass", jClass.getSimpleName())
    assertEquals("Klass", kjClass.getSimpleName())

    failsWithError { kClass.simpleName }
    failsWithError { kClass.qualifiedName }
    failsWithError { kClass.properties }
    failsWithError { kClass.extensionProperties }

    val jlError = Error::class.java
    val kljError = Error::class
    val jljError = kljError.java

    assertEquals("Error", jlError.getSimpleName())
    assertEquals("Error", jljError.getSimpleName())

    return "OK"
}