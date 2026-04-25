package example;

public @interface UsesAnnotationDefaultValue {
    Class<?> type() default AnnotationDefaultValueTarget.class;
}
