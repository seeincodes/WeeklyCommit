package com.acme.weeklycommit.api.dto;

import com.acme.weeklycommit.domain.entity.WeeklyCommit;
import java.util.Arrays;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * MapStruct mapper from {@link WeeklyCommit} entity + derived values to {@link
 * WeeklyCommitResponse}. Component-modelled for Spring DI.
 *
 * <p>Takes {@code carryStreak} + {@code stuckFlag} as extra parameters so the service can pass
 * results from {@code DerivedFieldService} without the mapper needing a repo dependency.
 */
@Mapper(componentModel = "spring")
public interface WeeklyCommitMapper {

  @Mapping(source = "commit.id", target = "id")
  @Mapping(source = "commit.planId", target = "planId")
  @Mapping(source = "commit.title", target = "title")
  @Mapping(source = "commit.description", target = "description")
  @Mapping(source = "commit.supportingOutcomeId", target = "supportingOutcomeId")
  @Mapping(source = "commit.chessTier", target = "chessTier")
  @Mapping(source = "commit.categoryTags", target = "categoryTags", qualifiedByName = "arrayToList")
  @Mapping(source = "commit.estimatedHours", target = "estimatedHours")
  @Mapping(source = "commit.displayOrder", target = "displayOrder")
  @Mapping(source = "commit.relatedMeeting", target = "relatedMeeting")
  @Mapping(source = "commit.carriedForwardFromId", target = "carriedForwardFromId")
  @Mapping(source = "commit.carriedForwardToId", target = "carriedForwardToId")
  @Mapping(source = "commit.actualStatus", target = "actualStatus")
  @Mapping(source = "commit.actualNote", target = "actualNote")
  @Mapping(
      target = "derived",
      expression = "java(new WeeklyCommitResponse.Derived(carryStreak, stuckFlag))")
  WeeklyCommitResponse toResponse(WeeklyCommit commit, int carryStreak, boolean stuckFlag);

  /** String[] on entity -> List<String> on DTO (Jackson serializes List naturally). */
  @Named("arrayToList")
  static List<String> arrayToList(String[] tags) {
    return tags == null ? List.of() : Arrays.asList(tags);
  }
}
