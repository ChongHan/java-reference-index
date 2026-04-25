package example;

import java.util.function.IntSupplier;

public class UsesMethodReference {
    public IntSupplier instanceSupplier(MethodReferenceTarget target) {
        return target::value;
    }

    public IntSupplier staticSupplier() {
        return MethodReferenceTarget::staticValue;
    }
}
