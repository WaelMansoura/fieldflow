package com.fieldflow.persistance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class Technicien {

    @Id
    private String nom;

    protected Technicien() {
    }

    public String getNom() {
        return nom;
    }
}
