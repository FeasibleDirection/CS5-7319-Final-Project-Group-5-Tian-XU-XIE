package com.projectgroup5.gamedemo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;

@SpringBootApplication(exclude = {R2dbcAutoConfiguration.class})
public class ProjectGroup5GameDemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProjectGroup5GameDemoApplication.class, args);
    }
}

