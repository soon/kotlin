// !DIAGNOSTICS: -UNUSED_VARIABLE
// FILE: A.java

import java.util.List;

public interface A<T> {
    List<String> getChildrenStubs();
}

// FILE: B.java

public class B<E extends A> {
    public E foo() { return null;}
}

// FILE: main.kt

fun foo(x: B<*>) {
    val q: MutableList<String> = <!TYPE_MISMATCH!>x.foo().getChildrenStubs()<!>
}
