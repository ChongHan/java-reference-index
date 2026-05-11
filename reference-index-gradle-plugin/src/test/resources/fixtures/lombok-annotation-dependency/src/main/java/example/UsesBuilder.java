package example;

public class UsesBuilder {
    private final App.AppBuilder builder = App.builder()
        .name("example");
    private final App app = builder.build();
}
