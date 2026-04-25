package example;

public enum UsesEnumReferences implements EnumInterfaceTarget {
    FIRST(new EnumConstructorTarget()) {
        @Override
        public int compute() {
            return EnumBodyTarget.value();
        }
    };

    private final EnumConstructorTarget target;

    UsesEnumReferences(EnumConstructorTarget target) {
        this.target = target;
    }
}
