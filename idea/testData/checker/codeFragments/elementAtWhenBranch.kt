fun main(args: Array<String>) {
    val str: String? = ""

    when(str) {
        null -> test("")
        <caret>else -> test(str)
    }
}

fun test(s: String) = 1