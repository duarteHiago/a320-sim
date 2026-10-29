package br.a320sim.core.module;

public record SelfTestResult(boolean passed, String message) {

    public static SelfTestResult ok() {
        return new SelfTestResult(true, "OK");
    }

    public static SelfTestResult fail(String message) {
        return new SelfTestResult(false, message);
    }
}
