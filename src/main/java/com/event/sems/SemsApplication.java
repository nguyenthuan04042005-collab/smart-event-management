package com.event.sems;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SemsApplication {
  public static void main(String[] args) {
    SpringApplication.run(SemsApplication.class, args);
  }
}
