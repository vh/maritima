package com.github.vh.maritima;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

import java.util.Properties;

public final class MaritimaModule extends AbstractModule {

    private Properties properties;

    public MaritimaModule() {
        this.properties = new Properties();
    }

    public MaritimaModule(Properties properties) {
        this.properties = properties;
    }

    @Override
    protected void configure() {
        Names.bindProperties(binder(), properties);
        Names.bindProperties(binder(), System.getProperties());
    }
}
