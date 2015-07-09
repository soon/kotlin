import kotlin.test.assertEquals
import kotlin.test.fail
import java.lang.Error

class Klass
class Other

inline fun <reified T> simpleName(): String =
        T::class.simpleName!!

inline fun <reified T1, reified T2> twoReifiedParams(): String =
        "${T1::class.simpleName!!}, ${T2::class.simpleName!!}"

fun box(): String {
    assertEquals("Klass", simpleName<Klass>())
    assertEquals("Int", simpleName<Int>())
    assertEquals("Array", simpleName<Array<Int>>())
    assertEquals("Error", simpleName<Error>())
    assertEquals("Klass, Other", twoReifiedParams<Klass, Other>())

    return "OK"
}