# maritima
[Armeria](https://armeria.dev) on steroids

## Install
### Maven
```xml
<dependencies>
    <dependency>
        <groupId>com.github.vh</groupId>
        <artifactId>maritima</artifactId>
        <version>0.20.2</version>
    </dependency>
</dependencies>
```

### Gradle
```groovy
implementation 'com.github.vh:maritima:0.20.2'
```

## Usage
```java
   Maritima
        // Guice module (inherits com.google.inject.AbstractModule)
        .build(new ApplicationModule())
        
        // Guice injector
        .init(injector -> {
            Flyway.configure()
                .dataSource(injector.getInstance(DataSource.class))
                .load()
                .migrate();
        })
        
        // GRPC services
        .services(
            TestService.class
        )
                
        // Port
        .start(8080);
```