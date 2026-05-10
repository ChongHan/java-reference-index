package example;

public class UsesBuilder {
    private final App app = App.builder()
        .name("example")
        .build();
}
