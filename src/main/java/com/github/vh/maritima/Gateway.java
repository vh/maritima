package com.github.vh.maritima;

import com.google.api.AuthenticationRule;
import com.google.api.Service;
import com.linecorp.armeria.common.*;
import com.linecorp.armeria.server.DecoratingHttpServiceFunction;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.ServiceRequestContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

final class Gateway implements DecoratingHttpServiceFunction {

    private static final Logger logger = LoggerFactory.getLogger(Gateway.class);

    private static final String AUTH_PROVIDER = "maritima";
    private static final String BEARER = "bearer";
    private static final String WILDCARD = "*";

    private static Set<String> authSelectors = new HashSet<>();
    private static Set<String> allowWithoutCredentialSelectors = new HashSet<>();

    private static boolean match(final Set<String> selectorSet, final String path) {
        for (String selector : selectorSet) {
            if (path.matches(selector)) {
                return true;
            }
        }

        return false;
    }

    private static void applyAuthRule(final AuthenticationRule rule) {
        if (rule.getAllowWithoutCredential()) {
            try {
                SelectorUtils.apply(allowWithoutCredentialSelectors, rule.getSelector());
            } catch (Exception e) {
                logger.warn("Invalid usage rule", e);
            }
        } else {
            rule.getRequirementsList().forEach(r -> {
                if (AUTH_PROVIDER.equals(r.getProviderId())) {
                    try {
                        SelectorUtils.apply(authSelectors, rule.getSelector());
                    } catch (Exception e) {
                        logger.warn("Invalid auth rule", e);
                    }
                }
            });
        }
    }

    public Gateway(Service config) {
        config.getAuthentication().getRulesList().forEach(Gateway::applyAuthRule);

        if (allowWithoutCredentialSelectors.contains(WILDCARD)) {
            allowWithoutCredentialSelectors = Set.of(WILDCARD);
        }

        if (authSelectors.contains(WILDCARD)) {
            authSelectors = Set.of(WILDCARD);
        }
    }

    @Override
    public HttpResponse serve(HttpService delegate, ServiceRequestContext ctx, HttpRequest req) throws Exception {
        // Skip preflight requests
        if (req.method() == HttpMethod.OPTIONS) {
            return delegate.serve(ctx, req);
        }

        String path = req.path().substring(1).replace('/', '.');

        if (match(allowWithoutCredentialSelectors, path)) {
            return delegate.serve(ctx, req);
        }

        if (match(authSelectors, path)) {
            Claims claims = null;

            String header = req.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (header != null && header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
                String token = header.substring(BEARER.length() + 1);
                try {
                    claims = Jwts.parserBuilder().setSigningKey(Maritima.APP_KEY)
                            .build()
                            .parseClaimsJws(token).getBody();
                } catch(Exception e) {
                    logger.warn("Error parsing JWT: token = " + token + ", path = " + req.path(), e);
                }
            }

            if (claims != null && (claims.getExpiration() == null || new Date().before(claims.getExpiration()))) {
                ctx.setAttr(MaritimaContext.CLAIMS_ATTR_KEY, claims);
                return delegate.serve(ctx, req);
            } else {
                return HttpResponse.of(HttpStatus.UNAUTHORIZED);
            }
        }

        return delegate.serve(ctx, req);
    }
}
