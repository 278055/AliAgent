package com.macro.mall.portal.aftersale.api;

public final class StepResult {
    private final String status; private final String detail;
    public StepResult(String status, String detail) { this.status = status; this.detail = detail; }
    public String status() { return status; } public String detail() { return detail; }
}
