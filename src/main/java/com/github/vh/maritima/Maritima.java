package com.github.vh.maritima;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.api.Service;
import com.google.common.collect.ImmutableList;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.protobuf.util.JsonFormat;
import com.linecorp.armeria.common.HttpMethod;
import com.linecorp.armeria.common.HttpResponse;
import com.linecorp.armeria.common.grpc.GrpcSerializationFormats;
import com.linecorp.armeria.server.DecoratingHttpServiceFunction;
import com.linecorp.armeria.server.HttpService;
import com.linecorp.armeria.server.Server;
import com.linecorp.armeria.server.ServerBuilder;
import com.linecorp.armeria.server.annotation.ExceptionHandlerFunction;
import com.linecorp.armeria.server.cors.CorsService;
import com.linecorp.armeria.server.cors.CorsServiceBuilder;
import com.linecorp.armeria.server.docs.DocService;
import com.linecorp.armeria.server.grpc.GrpcService;
import com.linecorp.armeria.server.grpc.GrpcServiceBuilder;
import io.grpc.BindableService;
import io.grpc.ServerInterceptor;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;

public final class Maritima {

    private static final Logger logger = LoggerFactory.getLogger(Maritima.class);

    private static final Integer PORT = System.getenv("PORT") != null ? Integer.parseInt(System.getenv("PORT")) : 8080;

    protected static final byte[] APP_KEY = System.getenv("APP_KEY") != null ? System.getenv("APP_KEY").getBytes() : new byte[]{};

    private final CorsServiceBuilder csb = CorsService.builderForAnyOrigin()
            .allowRequestMethods(HttpMethod.POST)
            .allowRequestHeaders("accept-encoding", "authorization", "content-type", "grpc-timeout", "grpc-encoding", "grpc-accept-encoding", "x-grpc-web", "x-user-agent")
            .exposeHeaders("content-encoding", "grpc-status", "grpc-message", "grpc-encoding", "grpc-accept-encoding");

    private final GrpcServiceBuilder gsb = GrpcService.builder()
            .supportedSerializationFormats(GrpcSerializationFormats.values());

    private final ServerBuilder sb = Server.builder()
            .maxRequestLength(32 * 1024 * 1024)                     // 32 Mb
            .requestTimeoutMillis(5 * 60 * 1000)                    // 5 min
            .service("/", (ctx, res) -> HttpResponse.of("OK"));

    private final List<DecoratingHttpServiceFunction> decorators = new ArrayList<>();

    private final Injector injector;

    public static Environment environment() {
        if (Environment.PRODUCTION.name().toLowerCase().equals(System.getenv("APP_ENV"))) {
            return Environment.PRODUCTION;
        } else if (Environment.STAGING.name().toLowerCase().equals(System.getenv("APP_ENV"))) {
            return Environment.STAGING;
        } else {
            return Environment.DEVELOPMENT;
        }
    }

    public static String jwt(Map<String, Object> claims) {
        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(new Date())
                .signWith(SignatureAlgorithm.HS256, APP_KEY)
                .compact();
    }

    private Maritima(Injector injector) {
        this.injector = injector;
    }

    @Nonnull
    public static Maritima build(Module... modules) {
        List<Module> ms = new ArrayList<>(Arrays.asList(modules));
        ms.add(new MaritimaModule(loadProperties()));

        return new Maritima(Guice.createInjector(ms));
    }

    @Nonnull
    public final Maritima init(Consumer<Injector> fn) {
        fn.accept(this.injector);
        return this;
    }

    @Nonnull @SafeVarargs
    public final Maritima interceptors(Class<? extends ServerInterceptor>... interceptorClasses) {
        for(Class<? extends ServerInterceptor> interceptorClass : interceptorClasses) {
            gsb.intercept(injector.getInstance(interceptorClass));
        }

        return this;
    }

    @Nonnull @SafeVarargs
    public final Maritima services(Class<? extends BindableService>... serviceClasses) {
        for (Class<? extends BindableService> serviceClass : serviceClasses) {
            gsb.addServices(injector.getInstance(serviceClass));
        }

        return this;
    }

    @Nonnull @SafeVarargs
    public final Maritima decorators(Class<? extends DecoratingHttpServiceFunction>... decoratorClasses) {
        for (Class<? extends DecoratingHttpServiceFunction> decoratorClass : decoratorClasses) {
            decorators.add(injector.getInstance(decoratorClass));
        }

        return this;
    }

    @Nonnull
    public final Maritima annotatedService(String pathPrefix, Class<?> serviceClass) {
        sb.annotatedService(pathPrefix, injector.getInstance(serviceClass));
        return this;
    }

    @Nonnull
    public final Maritima annotatedService(String pathPrefix, Class<?> serviceClass, Function<? super HttpService, ? extends HttpService> decorator) {
        sb.annotatedService(pathPrefix, injector.getInstance(serviceClass), decorator);
        return this;
    }

    @Nonnull @Deprecated
    public final Maritima serviceUnder(String pathPrefix, Class<? extends HttpService> serviceClass) {
        sb.serviceUnder(pathPrefix, injector.getInstance(serviceClass));
        return this;
    }

    @Nonnull
    public final Maritima maxRequestLength(long length) {
        sb.maxRequestLength(length);
        return this;
    }

    @Nonnull
    public final Maritima requestTimeout(long timeout) {
        sb.requestTimeoutMillis(timeout);
        return this;
    }

    @Nonnull
    public final Maritima unframed() {
        gsb.enableUnframedRequests(true);
        return this;
    }

    public void start() {
        start(PORT);
    }

    public void start(int port) {
        HttpService grpcService = gsb.build()
                .decorate(createExceptionLoggingDecorator())
                .decorate(createGateway());

        for (DecoratingHttpServiceFunction decorator : decorators) {
            grpcService = grpcService.decorate(decorator);
        }

        if (!environment().isProduction()) {
            sb.serviceUnder("/docs", new DocService());
        }

        Server server = sb.http(port)
                .serviceUnder("/", csb.build(grpcService))
                .annotatedServiceExtensions(
                        ImmutableList.of(), ImmutableList.of(),
                        ImmutableList.of((ctx, req, cause) -> {
                            logger.error(req.headers().path() + " failed: ", cause);
                            return ExceptionHandlerFunction.fallthrough();
                        })
                )
                .build();

        server.start().join();

        logger.info("The server has been started. ENV: {}", environment());

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            server.stop().join();
            logger.info("The server has been stopped.");
        }));
    }

    protected static DecoratingHttpServiceFunction createExceptionLoggingDecorator() {
        return (delegate, ctx, req) -> {
            ctx.log().whenComplete().thenAccept(log -> {
                Throwable cause = log.responseCause();
                if (cause != null && cause.getCause() != null) {
                    logger.error("Failed: ", cause.getCause());
                }
            });

            return delegate.serve(ctx, req);
        };
    }

    @SuppressWarnings("rawtypes")
    protected static Gateway createGateway() {
        Service.Builder builder = Service.newBuilder();
        try {
            InputStream configInputStream = Maritima.class.getResourceAsStream("/api-config.yaml");

            ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
            Map config = yamlReader.readValue(configInputStream, Map.class);

            if ("google.api.Service".equals(config.get("type"))) {
                config.remove("type");
            } else {
                throw new Exception("Invalid API config type");
            }

            ObjectMapper jsonWriter = new ObjectMapper();
            String configJson = jsonWriter.writeValueAsString(config);

            JsonFormat.parser().merge(configJson, builder);
        } catch (Exception e) {
            logger.warn("Cannot read the API config", e);
        }

        return new Gateway(builder.build());
    }

    private static Properties loadProperties() {
        Properties defaultProperties = new Properties();
        try {
            defaultProperties.load(Maritima.class.getResourceAsStream("/application.properties"));
        } catch (Exception e) {
            // Do nothing
        }

        //Load env specific
        Properties properties = new Properties(defaultProperties);
        try {
            String name = String.format("/application-%s.properties", Maritima.environment().name().toLowerCase());
            properties.load(Maritima.class.getResourceAsStream(name));
        } catch (Exception e) {
            // Do nothing
        }

        return properties;
    }
}
