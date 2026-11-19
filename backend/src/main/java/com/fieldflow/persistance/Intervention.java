package com.fieldflow.persistance;

import com.fieldflow.regles.EtatIntervention;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import java.time.Instant;
import java.util.UUID;

/** Lecture seule côté application : seules les activités du workflow écrivent (requêtes natives). */
@Entity
public class Intervention {

    @Id
    private UUID id;

    private String titre;

    private String adresse;

    @Enumerated(EnumType.STRING)
    private EtatIntervention etat;

    @Column(name = "cree_a")
    private Instant creeA;

    private Instant echeance;

    private String technicienSelectionne;

    @Column(columnDefinition = "text")
    private String rapport;

    @Column(name = "archivee_a")
    private Instant archiveeA;

    protected Intervention() {
    }

    public UUID getId() {
        return id;
    }

    public String getTitre() {
        return titre;
    }

    public String getAdresse() {
        return adresse;
    }

    public EtatIntervention getEtat() {
        return etat;
    }

    public Instant getCreeA() {
        return creeA;
    }

    public Instant getEcheance() {
        return echeance;
    }

    public String getTechnicienSelectionne() {
        return technicienSelectionne;
    }

    public String getRapport() {
        return rapport;
    }

    public Instant getArchiveeA() {
        return archiveeA;
    }
}
