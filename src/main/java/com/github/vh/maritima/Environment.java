package com.github.vh.maritima;

public enum Environment {
    DEVELOPMENT,
    STAGING,
    PRODUCTION;

    public boolean isProduction() {
        return this.equals(PRODUCTION);
    }

    public boolean isDevelopment() {
        return this.equals(DEVELOPMENT);
    }
}
