class A {
    inner class I {
        <!WRONG_MODIFIER_PARENT!>companion<!> object A

        <!MANY_COMPANION_OBJECTS, WRONG_MODIFIER_PARENT!>companion<!> object B

        <!MANY_COMPANION_OBJECTS, WRONG_MODIFIER_PARENT!>companion<!> object C
    }
}

object O {
    <!WRONG_MODIFIER_PARENT!>companion<!> object A

    <!MANY_COMPANION_OBJECTS, WRONG_MODIFIER_PARENT!>companion<!> object B

    <!MANY_COMPANION_OBJECTS, WRONG_MODIFIER_PARENT!>companion<!> object C
}