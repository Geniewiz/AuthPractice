package org.example.authpractice;

import org.springframework.boot.SpringApplication;

public class TestAuthPracticeApplication {

    public static void main(String[] args) {
        SpringApplication.from(AuthPracticeApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
