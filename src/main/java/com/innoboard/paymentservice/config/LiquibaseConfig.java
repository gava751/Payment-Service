package com.innoboard.paymentservice.config;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LiquibaseConfig implements InitializingBean {

    private final String mongoUri;
    private final String changeLog;

    public LiquibaseConfig(
            @Value("${spring.mongodb.uri}") String mongoUri,
            @Value("${spring.liquibase.change-log}") String changeLog) {
        this.mongoUri = mongoUri;
        this.changeLog = changeLog;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        liquibase.database.Database database = DatabaseFactory.getInstance()
                .openDatabase(mongoUri, null, null, null, new ClassLoaderResourceAccessor());
        try (Liquibase liquibase = new Liquibase(changeLog.replace("classpath:", ""),
                new ClassLoaderResourceAccessor(), database)) {
            liquibase.update(new Contexts(), new LabelExpression());
        }
        database.close();
    }
}
