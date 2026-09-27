package com.se100.clinic;

import org.springframework.boot.SpringApplication;

public class TestClinicBookingApiApplication {

  public static void main(String[] args) {
    SpringApplication.from(ClinicBookingApiApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
