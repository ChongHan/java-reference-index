package example;

public class UsesHelper {
    private Helper helper;

    public int calculate(int value) {
        return helper.doubleValue(value);
    }
}
