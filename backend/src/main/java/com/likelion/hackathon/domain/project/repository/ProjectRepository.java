package com.likelion.hackathon.domain.project.repository;

import com.likelion.hackathon.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    // ACTIVE 멤버(직접 만들었거나 초대를 수락한 사람)의 "내 프로젝트" 목록에만 뜬다 —
    // PENDING(초대는 왔지만 아직 수락 안 함)인 프로젝트는 여기 안 잡히고 알림으로만 안내된다.
    @Query("SELECT p FROM Project p JOIN ProjectMember pm ON pm.project = p " +
            "WHERE pm.user.id = :userId AND pm.status = com.likelion.hackathon.domain.project.entity.enums.ProjectMemberStatus.ACTIVE")
    List<Project> findAllByMemberId(@Param("userId") UUID userId);
}
