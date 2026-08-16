package com.likelion.hackathon.domain.project.repository;

import com.likelion.hackathon.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    @Query("SELECT p FROM Project p JOIN ProjectMember pm ON pm.project = p WHERE pm.user.id = :userId")
    List<Project> findAllByMemberId(@Param("userId") UUID userId);
}
