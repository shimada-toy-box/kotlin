// FILE: PsiNamedElement.java

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiNamedElement {
    @Nullable
    String getName();

    PsiNamedElement setName(@NotNull String name);

    String getImmutableName();
}

// FILE: SwiftArgument.java

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface SwiftArgument extends PsiNamedElement {
    @Override
    @NotNull SwiftArgument setName(@Nullable String name);
}

// FILE: SwiftNamedElementImpl.kt

abstract class SwiftNamedElementImpl : PsiNamedElement {
    override fun getName(): String? = null

    override fun setName(name: String): PsiNamedElement {
        return this
    }

    fun clearName() {
        <!VAL_REASSIGNMENT!>this.name<!> = "" // Ok in FIR, error in FE 1.0
        <!VAL_REASSIGNMENT!>this.immutableName<!> = ""
    }
}

// FILE: SwiftArgumentImpl.kt

abstract class SwiftArgumentImpl : SwiftNamedElementImpl(), SwiftArgument {
    abstract override fun setName(name: String?): SwiftArgument
}
