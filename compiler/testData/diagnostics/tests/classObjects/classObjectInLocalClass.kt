fun test() {
    class A {
        <!WRONG_MODIFIER_PARENT!>companion<!> object {}
    }

    object {
        <!WRONG_MODIFIER_PARENT!>companion<!> object {}
    }
}