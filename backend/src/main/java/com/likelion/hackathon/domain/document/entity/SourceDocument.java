package com.likelion.hackathon.domain.document.entity;

import com.likelion.hackathon.domain.project.entity.Project;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "source_documents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SourceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    // md 직접 업로드면 NULL
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id")
    private SourceConnection connection;

    @Column(nullable = false)
    private String title;

    private String path;

    @Column(columnDefinition = "TEXT")
    private String content;

    private String sourceUrl;

    // true면 회의 생성 시 파일 선택 목록에 항상 우선 노출
    @Column(nullable = false)
    @Builder.Default
    private boolean isCoreContext = false;

    private LocalDateTime lastModifiedAt;

    private LocalDateTime syncedAt;

    public void setCoreContext(boolean isCoreContext) {
        this.isCoreContext = isCoreContext;
    }

    public void updateSyncInfo(LocalDateTime syncedAt) {
        this.syncedAt = syncedAt;
        this.lastModifiedAt = syncedAt;
    }

    public void updateFromSync(String title, String content, LocalDateTime syncedAt) {
        this.title = title;
        this.content = content;
        this.syncedAt = syncedAt;
        this.lastModifiedAt = syncedAt;
    }
}
