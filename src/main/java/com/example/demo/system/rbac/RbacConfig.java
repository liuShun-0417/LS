package com.example.demo.system.rbac;

import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.*;
import javax.sql.DataSource;

@Configuration
public class RbacConfig {
    @Bean
    @ConditionalOnProperty(name="rbac.initialize-schema",havingValue="true",matchIfMissing=true)
    public DataSourceInitializer rbacSchemaInitializer(DataSource dataSource) {
        var initializer = new DataSourceInitializer();
        initializer.setDataSource(dataSource);
        var populator = new ResourceDatabasePopulator(new ClassPathResource("db/rbac-schema.sql"));
        populator.setSqlScriptEncoding("UTF-8");
        initializer.setDatabasePopulator(populator);
        return initializer;
    }
}
