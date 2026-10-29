package com.moviebot.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class MovieBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieBotApplication.class, args);


    }

}
