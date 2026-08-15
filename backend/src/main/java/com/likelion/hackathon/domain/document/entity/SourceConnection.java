package com.likelion.hackathon.domain.document.entity;

import com.likelion.hackathon.domain.document.entity.enums.ConnectionType;
import com.likelion.hackathon.domain.project.entity.Project;
import com.likelion.hackathon.domain.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "source_connections")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SourceConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConnectionType type;

    private String workspaceOrRepoName;

    @Column(nullable = false)
    private String accessToken; // 암호화 저장

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_by", nullable = false)
    private User connectedBy;

    @Column(nullable = false)
    private LocalDateTime connectedAt;
}
