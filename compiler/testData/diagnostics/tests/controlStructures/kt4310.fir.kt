package f

fun test(a: Boolean, b: Boolean): Int {
    return <!RETURN_TYPE_MISMATCH, TYPE_MISMATCH!>if(a) {
        1
    } else {
        if (b) {
            3
        }
    }<!>
}
