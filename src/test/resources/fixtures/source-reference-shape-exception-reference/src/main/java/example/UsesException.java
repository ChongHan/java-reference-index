package example;

public class UsesException {
    public void run() throws ExceptionTarget {
        try {
            throw new ExceptionTarget();
        } catch (ExceptionTarget exception) {
            throw exception;
        }
    }
}
