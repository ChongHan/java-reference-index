package example;

public class UsesNativeLibrary {
    static {
        System.loadLibrary("bedrockio");
    }

    public native int readNativeValue(long address);
}
