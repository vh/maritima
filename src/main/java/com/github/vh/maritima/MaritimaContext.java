package com.github.vh.maritima;

import com.linecorp.armeria.common.RequestContext;
import io.netty.util.AttributeKey;

import java.util.Map;

public abstract class MaritimaContext {

    protected static final AttributeKey<Map<String, Object>> CLAIMS_ATTR_KEY = AttributeKey.valueOf("claims");

    protected static Map<String, Object> getClaims() {
        return RequestContext.current().attr(CLAIMS_ATTR_KEY);
    }
}
