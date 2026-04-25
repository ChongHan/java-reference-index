package example;

import java.util.function.Supplier;

public class UsesConstructorMethodReference {
    public Supplier<ConstructorMethodReferenceTarget> supplier() {
        return ConstructorMethodReferenceTarget::new;
    }
}
