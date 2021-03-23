package ir.armansoft.telegram.gathering.integration;

@FunctionalInterface
public interface CallBack<I, O> {
    void call(String phone, I input, O output);
}
