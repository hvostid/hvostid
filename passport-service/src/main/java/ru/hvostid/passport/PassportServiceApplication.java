package ru.hvostid.passport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@org.springframework.scheduling.annotation.EnableScheduling
@SpringBootApplication
public class PassportServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PassportServiceApplication.class, args);
    }
}
