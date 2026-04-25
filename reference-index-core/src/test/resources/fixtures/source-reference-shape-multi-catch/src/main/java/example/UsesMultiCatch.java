package example;

public class UsesMultiCatch {
    public void run() {
        try {
            mayFail();
        } catch (FirstExceptionTarget | SecondExceptionTarget exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void mayFail() throws FirstExceptionTarget, SecondExceptionTarget {
    }
}
