enum class E {
    FIRST,

    SECOND {
        <!WRONG_MODIFIER_PARENT!>companion<!> object {
            fun foo() = 42
        }
    };
}

fun f() = E.SECOND.<!UNRESOLVED_REFERENCE!>foo<!>()
