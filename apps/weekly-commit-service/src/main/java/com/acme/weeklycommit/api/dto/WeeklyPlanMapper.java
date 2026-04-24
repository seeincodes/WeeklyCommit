package com.acme.weeklycommit.api.dto;

import com.acme.weeklycommit.domain.entity.WeeklyPlan;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * MapStruct mapper from {@link WeeklyPlan} entity to {@link WeeklyPlanResponse}. Compile-time
 * generated; no reflection at runtime. Component-modelled for Spring so controllers and services
 * can inject it.
 *
 * <p>Null entity returns null response (test-driven). Mapping is field-by-field; MapStruct
 * flags any missing or type-mismatched source property at build time.
 */
@Mapper(componentModel = "spring")
public interface WeeklyPlanMapper {

  @Mapping(source = "id", target = "id")
  @Mapping(source = "employeeId", target = "employeeId")
  @Mapping(source = "weekStart", target = "weekStart")
  @Mapping(source = "state", target = "state")
  @Mapping(source = "lockedAt", target = "lockedAt")
  @Mapping(source = "reconciledAt", target = "reconciledAt")
  @Mapping(source = "managerReviewedAt", target = "managerReviewedAt")
  @Mapping(source = "reflectionNote", target = "reflectionNote")
  @Mapping(source = "version", target = "version")
  WeeklyPlanResponse toResponse(WeeklyPlan entity);
}
