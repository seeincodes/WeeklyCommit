package com.acme.weeklycommit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableJpaAuditing} intentionally lives on {@code JpaAuditingConfig} rather than here —
 * placing it on this class would pull JPA auto-config into {@code @WebMvcTest} slices and crash
 * context load without a datasource.
 */
@SpringBootApplication
@EnableScheduling
public class WeeklyCommitServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(WeeklyCommitServiceApplication.class, args);
  }
}
