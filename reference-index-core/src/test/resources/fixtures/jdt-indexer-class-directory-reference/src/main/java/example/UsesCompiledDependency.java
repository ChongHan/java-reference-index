package example;

import external.dep.CompiledDependency;

public class UsesCompiledDependency {
    private CompiledDependency dependency;

    public int value() {
        return dependency.value();
    }
}
