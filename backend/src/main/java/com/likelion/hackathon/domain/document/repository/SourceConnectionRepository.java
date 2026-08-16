package com.likelion.hackathon.domain.document.repository;

import com.likelion.hackathon.domain.document.entity.SourceConnection;
import com.likelion.hackathon.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourceConnectionRepository extends JpaRepository<SourceConnection, UUID> {
    List<SourceConnection> findAllByProject(Project project);
}
