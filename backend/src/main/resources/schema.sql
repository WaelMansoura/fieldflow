-- Schéma FieldFlow (data-model.md). Exécuté à chaque démarrage : idempotent.

CREATE TABLE IF NOT EXISTS technicien (
    nom VARCHAR(50) PRIMARY KEY
);

CREATE TABLE IF NOT EXISTS intervention (
    id                     UUID PRIMARY KEY,
    titre                  VARCHAR(200) NOT NULL CHECK (btrim(titre) <> ''),
    adresse                VARCHAR(200) NOT NULL CHECK (btrim(adresse) <> ''),
    etat                   VARCHAR(30)  NOT NULL,
    cree_a                 TIMESTAMPTZ  NOT NULL,
    echeance               TIMESTAMPTZ  NOT NULL,
    technicien_selectionne VARCHAR(50)  REFERENCES technicien (nom),
    rapport                TEXT,
    archivee_a             TIMESTAMPTZ
);

-- Une seule intervention active (non archivée) à la fois (FR-024).
CREATE UNIQUE INDEX IF NOT EXISTS intervention_active
    ON intervention ((true)) WHERE archivee_a IS NULL;

CREATE TABLE IF NOT EXISTS proposition (
    id              BIGSERIAL PRIMARY KEY,
    intervention_id UUID        NOT NULL REFERENCES intervention (id),
    technicien      VARCHAR(50) NOT NULL REFERENCES technicien (nom),
    issue           VARCHAR(30) NOT NULL DEFAULT 'EN_ATTENTE',
    montant_frais   NUMERIC(10, 2) CHECK (montant_frais > 0),
    motif_frais     VARCHAR(200),
    repondu_a       TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS proposition_unique
    ON proposition (intervention_id, technicien);

CREATE TABLE IF NOT EXISTS evenement_historique (
    id              BIGSERIAL PRIMARY KEY,
    intervention_id UUID        NOT NULL REFERENCES intervention (id),
    cle             VARCHAR(50) NOT NULL,
    survenu_a       TIMESTAMPTZ NOT NULL,
    description     TEXT        NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS evenement_historique_unique
    ON evenement_historique (intervention_id, cle);
