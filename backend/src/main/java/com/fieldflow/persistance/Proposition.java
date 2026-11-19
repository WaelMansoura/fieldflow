package com.fieldflow.persistance;

import com.fieldflow.regles.Issue;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
public class Proposition {

    @Id
    private Long id;

    private UUID interventionId;

    private String technicien;

    @Enumerated(EnumType.STRING)
    private Issue issue;

    @Column(precision = 10, scale = 2)
    private BigDecimal montantFrais;

    private String motifFrais;

    @Column(name = "repondu_a")
    private Instant reponduA;

    protected Proposition() {
    }

    public Long getId() {
        return id;
    }

    public UUID getInterventionId() {
        return interventionId;
    }

    public String getTechnicien() {
        return technicien;
    }

    public Issue getIssue() {
        return issue;
    }

    public BigDecimal getMontantFrais() {
        return montantFrais;
    }

    public String getMotifFrais() {
        return motifFrais;
    }

    public Instant getReponduA() {
        return reponduA;
    }
}
