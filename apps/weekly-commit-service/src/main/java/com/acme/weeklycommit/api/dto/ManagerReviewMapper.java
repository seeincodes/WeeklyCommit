package com.acme.weeklycommit.api.dto;

import com.acme.weeklycommit.domain.entity.ManagerReview;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** MapStruct mapper from {@link ManagerReview} entity to {@link ManagerReviewResponse}. */
@Mapper(componentModel = "spring")
public interface ManagerReviewMapper {

  @Mapping(source = "id", target = "id")
  @Mapping(source = "planId", target = "planId")
  @Mapping(source = "managerId", target = "managerId")
  @Mapping(source = "comment", target = "comment")
  @Mapping(source = "acknowledgedAt", target = "acknowledgedAt")
  ManagerReviewResponse toResponse(ManagerReview entity);
}
