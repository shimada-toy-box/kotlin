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
class DiamondSwapped : NN(), Derived

fun test(d: Diamond, ds: DiamondSwapped, s: String?) {
    d.update(s)
    ds.update(<!ARGUMENT_TYPE_MISMATCH!>s<!>)
}
