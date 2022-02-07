// SCOPE_DUMP: Inheritor:remove
// FULL_JDK

class Inheritor : java.util.ArrayList<String>() {
    override fun remove(x: String): Boolean = true
}
