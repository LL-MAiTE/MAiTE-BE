package com.likelion.hackathon.domain.project.repository;

import com.likelion.hackathon.domain.project.entity.Project;
import com.likelion.hackathon.domain.project.entity.ProjectMember;
import com.likelion.hackathon.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    boolean existsByProjectAndUser(Project project, User user);
    Optional<ProjectMember> findByProjectAndUser(Project project, User user);
    List<ProjectMember> findAllByProject(Project project);
}
