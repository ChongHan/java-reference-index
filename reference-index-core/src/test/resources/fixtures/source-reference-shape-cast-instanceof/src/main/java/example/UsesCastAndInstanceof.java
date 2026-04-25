package example;

public class UsesCastAndInstanceof {
    public CastTarget cast(Object value) {
        return value instanceof CastTarget ? (CastTarget) value : null;
    }
}
