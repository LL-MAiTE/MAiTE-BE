package com.likelion.hackathon.domain.document.entity;

import com.likelion.hackathon.domain.document.entity.enums.ConnectionType;
import com.likelion.hackathon.domain.project.entity.Project;
import com.likelion.hackathon.domain.user.entity.User;
import com.likelion.hackathon.global.crypto.TokenEncryptionConverter;
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

    // AES-256-GCM으로 암호화되어 저장됨 (TokenEncryptionConverter). 애플리케이션 코드에서는
    // 평문 문자열처럼 그대로 다루면 되고, 암/복호화는 JPA가 읽기/쓰기 시 자동으로 처리한다.
    @Convert(converter = TokenEncryptionConverter.class)
    @Column(nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connected_by", nullable = false)
    private User connectedBy;

    @Column(nullable = false)
    private LocalDateTime connectedAt;
}
