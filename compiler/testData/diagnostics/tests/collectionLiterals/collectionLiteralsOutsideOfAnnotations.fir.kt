// !LANGUAGE: +NewInference
// !DIAGNOSTICS: -UNUSED_VARIABLE -UNUSED_PARAMETER

fun takeArray(array: Array<String>) {}

fun test() {
    "foo bar".<!UNRESOLVED_REFERENCE!>split<!>([""])
    <!UNRESOLVED_REFERENCE!>unresolved<!>([""])
    takeArray([""])
    val v = [""]
    [""]
    [1, 2, 3].size
}

fun baz(arg: Array<Int> = []) {
    <!NEW_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>if (true) ["yes"] else {["no"]}<!>
}

class Foo(
    val v: Array<Int> = []
)
