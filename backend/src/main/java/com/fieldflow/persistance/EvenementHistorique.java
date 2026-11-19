package com.fieldflow.persistance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

@Entity
public class EvenementHistorique {

    @Id
    private Long id;

    private UUID interventionId;

    private String cle;

    @Column(name = "survenu_a")
    private Instant survenuA;

    @Column(columnDefinition = "text")
    private String description;

    protected EvenementHistorique() {
    }

    public Long getId() {
        return id;
    }

    public UUID getInterventionId() {
        return interventionId;
    }

    public String getCle() {
        return cle;
    }

    public Instant getSurvenuA() {
        return survenuA;
    }

    public String getDescription() {
        return description;
    }
}
