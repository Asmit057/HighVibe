package com.springproject.lovable_clone.mapper;

import com.springproject.lovable_clone.dto.project.ProjectResponse;
import com.springproject.lovable_clone.dto.project.ProjectSummaryResponse;
import com.springproject.lovable_clone.entity.Project;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ProjectMapper {

    // just create method
    ProjectResponse toProjectResponse(Project project);


    @Mapping(target = "projectName" , source = "name")
    // source is whatever we are providing in project
    // we take source name from project and map it to the source projectResponse in record ProjectSummaryResponse
    //@Mapping(target = "createdAt" , dateFormat = "YYYY-MM-DD")
    ProjectSummaryResponse toProjectSummaryResponse(Project project);


    List<ProjectSummaryResponse> toListOfProjectSummaryResponse(List<Project> projects);
}
