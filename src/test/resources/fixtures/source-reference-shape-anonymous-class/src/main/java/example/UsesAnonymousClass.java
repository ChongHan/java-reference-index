package example;

public class UsesAnonymousClass {
    public AnonymousTarget create() {
        return new AnonymousTarget() {
            @Override
            public int value() {
                return super.value() + 1;
            }
        };
    }
}
