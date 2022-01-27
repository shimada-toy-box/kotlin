// FILE: Base.java

public interface Base {
    void update(String param);
}

// FILE: Derived.java

public interface Derived extends Base {}

// FILE: test.kt

abstract class NN : Base {
    override fun update(param: String) {}
}

class Diamond : Derived, NN()

fun test(d: Diamond, s: String?) {
    d.update(<!ARGUMENT_TYPE_MISMATCH!>s<!>)
}
