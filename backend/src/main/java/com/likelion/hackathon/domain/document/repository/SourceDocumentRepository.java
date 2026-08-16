package com.likelion.hackathon.domain.document.repository;

import com.likelion.hackathon.domain.document.entity.SourceDocument;
import com.likelion.hackathon.domain.document.entity.SourceConnection;
import com.likelion.hackathon.domain.project.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceDocumentRepository extends JpaRepository<SourceDocument, UUID> {
    List<SourceDocument> findAllByProjectOrderByIsCoreContextDescLastModifiedAtDesc(Project project);
    List<SourceDocument> findAllByConnection(SourceConnection connection);
    Optional<SourceDocument> findByConnectionAndPath(SourceConnection connection, String path);
}
