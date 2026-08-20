package com.likelion.hackathon.domain.project.entity;

import com.likelion.hackathon.domain.project.entity.enums.ProjectMemberRole;
import com.likelion.hackathon.domain.project.entity.enums.ProjectMemberStatus;
import com.likelion.hackathon.domain.user.entity.User;
import com.likelion.hackathon.global.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "project_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "user_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ProjectMember extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProjectMemberRole role;

    // 컬럼에 DB 기본값을 박아둬서(=ACTIVE), 이 필드가 생기기 전부터 있던 기존 멤버 row도
    // 전부 ACTIVE로 채워지며 마이그레이션된다(수락 절차 없이 이미 활동 중이던 사람들이라).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20) default 'ACTIVE'")
    @Builder.Default
    private ProjectMemberStatus status = ProjectMemberStatus.ACTIVE;

    public void accept() {
        this.status = ProjectMemberStatus.ACTIVE;
    }

    public void decline() {
        this.status = ProjectMemberStatus.DECLINED;
    }
}
