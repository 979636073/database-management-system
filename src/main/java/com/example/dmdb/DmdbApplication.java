package com.example.dmdb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class DmdbApplication {

    public static void main(String[] args) {
        SpringApplication.run(DmdbApplication.class, args);
    }

}
