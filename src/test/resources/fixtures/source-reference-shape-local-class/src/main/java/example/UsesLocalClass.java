package example;

public class UsesLocalClass {
    public Object create() {
        class LocalWorker extends LocalClassTarget {
        }
        return new LocalWorker();
    }
}
