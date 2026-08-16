package com.likelion.hackathon.domain.agenda.entity;

import com.likelion.hackathon.domain.agenda.entity.enums.AddedBy;
import com.likelion.hackathon.domain.document.entity.SourceDocument;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "agenda_reference_documents",
        uniqueConstraints = @UniqueConstraint(columnNames = {"agenda_id", "source_document_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class AgendaReferenceDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agenda_id", nullable = false)
    private Agenda agenda;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_document_id", nullable = false)
    private SourceDocument sourceDocument;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AddedBy addedBy = AddedBy.USER;

    // 생성 후 사용자가 목록에서 제외했으면 true (기록은 남기되 안건 생성 대상에서 제외)
    @Column(nullable = false)
    @Builder.Default
    private boolean excluded = false;

    @Column(nullable = false)
    private LocalDateTime addedAt;

    public void exclude() {
        this.excluded = true;
    }
}
