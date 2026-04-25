package com.acme.weeklycommit.testsupport;

import com.acme.weeklycommit.config.JpaAuditingConfig;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

/**
 * Composed annotation for repository integration tests.
 *
 * <ul>
 *   <li>{@link DataJpaTest} slice — JPA + Flyway, not the full {@code SecurityConfig}
 *   <li>{@code replace = NONE} — use the real Postgres 16.4 container, not an embedded DB
 *   <li>{@code @Import(JpaAuditingConfig.class)} — AuditorAware bean present so {@code @CreatedBy}
 *       / {@code @LastModifiedBy} fire during {@code save()} (the NOT NULL constraints on {@code
 *       created_by} / {@code last_modified_by} would otherwise blow up)
 * </ul>
 *
 * <p>Individual tests still need a {@code @DynamicPropertySource} that calls {@link
 * PostgresTestContainer#register} to bind the datasource.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@Import(JpaAuditingConfig.class)
public @interface JpaTestSlice {}
