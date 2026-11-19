# Data Model: Consultation des techniciens et suivi d'une intervention

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

Deux stockages, deux rôles :

- **PostgreSQL** : les données affichées (intervention, propositions, historique). Seules les
  activités du workflow y écrivent ; les contrôleurs ne font que lire.
- **State store Dapr (Redis)** : l'historique d'exécution du workflow, géré par Dapr et jamais lu
  directement par l'application.

## Énumérations (paquet `regles`, Java pur)

| Enum | Valeurs |
|------|---------|
| `EtatIntervention` | `EN_CONSULTATION`, `EN_ATTENTE_SELECTION`, `SELECTIONNEE`, `EN_ROUTE`, `SUR_SITE`, `TERMINEE`, `REFUSEE` |
| `Issue` (réponse d'un technicien) | `EN_ATTENTE`, `ACCEPTEE`, `ACCEPTEE_AVEC_FRAIS`, `REFUSEE`, `SANS_REPONSE` |
| `TypeReponse` (bouton) | `ACCEPTER`, `ACCEPTER_AVEC_FRAIS`, `REFUSER` |
| `Etape` (bouton) | `EN_ROUTE`, `SUR_SITE`, `TERMINEE` |
| `VueTechnicien` | `VIDE`, `CONSULTATION`, `ATTENTE`, `ETAPES` |

Libellés affichés : En consultation, En attente de sélection, Sélectionnée, En route, Sur site,
Terminée, Refusée.

## Tables

### `technicien`

| Colonne | Type | Contraintes |
|---------|------|-------------|
| `nom` | `varchar(50)` | PK |

Données de démonstration : `Jean`, `Jacques`, `Pierre` (`data.sql`, `ON CONFLICT DO NOTHING`).

### `intervention`

| Colonne | Type | Contraintes |
|---------|------|-------------|
| `id` | `uuid` | PK, identique à l'identifiant d'instance du workflow |
| `titre` | `varchar(200)` | non null, non vide |
| `adresse` | `varchar(200)` | non null, non vide |
| `etat` | `varchar(30)` | non null, valeur de `EtatIntervention` |
| `cree_a` | `timestamptz` | non null |
| `echeance` | `timestamptz` | non null, `cree_a + délai`, calculée par `InterventionRules.echeance` (R2) |
| `technicien_selectionne` | `varchar(50)` | FK `technicien`, null avant la sélection |
| `rapport` | `text` | null sauf quand l'état est `TERMINEE` |
| `archivee_a` | `timestamptz` | null tant que le chef n'a pas cliqué sur « Finir » |

- Index unique partiel `intervention_active` : `((true)) WHERE archivee_a IS NULL`. Il garantit une
  seule intervention active à la fois (FR-024).
- L'intervention « courante » est celle dont `archivee_a IS NULL`.

### `proposition`

| Colonne | Type | Contraintes |
|---------|------|-------------|
| `id` | `bigserial` | PK |
| `intervention_id` | `uuid` | FK `intervention`, non null |
| `technicien` | `varchar(50)` | FK `technicien`, non null |
| `issue` | `varchar(30)` | non null, valeur de `Issue`, défaut `EN_ATTENTE` |
| `montant_frais` | `numeric(10,2)` | non null si et seulement si `issue = ACCEPTEE_AVEC_FRAIS` ; > 0 |
| `motif_frais` | `varchar(200)` | même règle que `montant_frais` ; non vide |
| `repondu_a` | `timestamptz` | null tant que l'issue est `EN_ATTENTE` |

- Unique `(intervention_id, technicien)` : une proposition par technicien et par intervention.
- Les trois propositions sont créées `EN_ATTENTE` en même temps que l'intervention (R1).

### `evenement_historique`

| Colonne | Type | Contraintes |
|---------|------|-------------|
| `id` | `bigserial` | PK |
| `intervention_id` | `uuid` | FK `intervention`, non null |
| `cle` | `varchar(50)` | non null, clé d'idempotence |
| `survenu_a` | `timestamptz` | non null, heure du fait (voir research R-06) |
| `description` | `text` | non null, texte lisible (peut contenir titre, adresse ou rapport) |

- Unique `(intervention_id, cle)` : un événement n'est jamais enregistré deux fois (FR-021, FR-031).
- Ordre d'affichage : `survenu_a`, puis `id`.
- La clé et la description de chaque ligne sont construites par `InterventionRules` (R9) ;
  l'heure est fournie par le workflow (research R-06).

| `cle` | Quand | Exemple de `description` |
|-------|-------|--------------------------|
| `CREATION` | création | Intervention créée : « Chaudière en panne », 12 rue des Lilas |
| `REPONSE:{nom}` | réponse | Jean a accepté · Jacques a accepté avec 40,00 € de frais (Déplacement) · Pierre a refusé |
| `SANS_REPONSE:{nom}` | clôture | Pierre n'a pas répondu |
| `CLOTURE` | clôture | Consultation close : 2 acceptation(s) · Consultation close : aucune acceptation, intervention refusée |
| `SELECTION` | sélection | Jean a été sélectionné |
| `EN_ROUTE` | étape | Jean est en route |
| `SUR_SITE` | étape | Jean est sur site |
| `TERMINEE` | étape | Intervention terminée — rapport : « Brûleur remplacé » |
| `FIN` | Finir | Intervention clôturée par le chef |

L'heure est affichée au format HH:MM:SS (heure locale du navigateur).

## Transitions d'état

```text
                 ┌──────────── au moins une acceptation ──► EN_ATTENTE_SELECTION ──selection──► SELECTIONNEE
EN_CONSULTATION ─┤   (3 réponses ou échéance)                                                       │
                 └──────────── aucune acceptation ────────► REFUSEE                               en route
                                                                                                    ▼
                                                     TERMINEE ◄──terminer (rapport)── SUR_SITE ◄──sur site── EN_ROUTE
```

| De | Événement | Vers | Règle |
|----|-----------|------|-------|
| — | création | `EN_CONSULTATION` | R1 |
| `EN_CONSULTATION` | réponse | `EN_CONSULTATION` | R4, R5 ; une seule réponse par technicien |
| `EN_CONSULTATION` | 3 réponses ou échéance, ≥ 1 acceptation | `EN_ATTENTE_SELECTION` | R3 |
| `EN_CONSULTATION` | 3 réponses ou échéance, 0 acceptation | `REFUSEE` | R3 |
| `EN_ATTENTE_SELECTION` | sélection d'une proposition acceptée | `SELECTIONNEE` | R6 |
| `SELECTIONNEE` | « En route » du technicien sélectionné | `EN_ROUTE` | R8 |
| `EN_ROUTE` | « Sur site » du technicien sélectionné | `SUR_SITE` | R8 |
| `SUR_SITE` | « Terminer » avec un rapport non vide | `TERMINEE` | R8 |
| `TERMINEE`, `REFUSEE` | « Finir » | (archivée) | R10 |

Toute autre combinaison est refusée : 409 côté API, événement ignoré côté workflow.

## Règles de validation (portées par `InterventionRules`)

| Règle | Contrôle | Réponse API |
|-------|----------|-------------|
| Titre, adresse | non vides, 200 caractères au maximum | 400 |
| R5 Frais | montant > 0, au plus 2 décimales ; motif non vide, 200 caractères au maximum | 400 |
| R4 Réponse | état `EN_CONSULTATION`, maintenant < échéance, issue `EN_ATTENTE` | 409 |
| R6 Sélection | état `EN_ATTENTE_SELECTION`, issue `ACCEPTEE` ou `ACCEPTEE_AVEC_FRAIS` | 409 |
| R8 Étape | étape = suivante de l'état courant ; demandée par le technicien sélectionné | 409 |
| R8 Rapport | non vide pour `TERMINEE` | 400 |
| R10 Finir | état `TERMINEE` ou `REFUSEE` | 409 |
| Création | aucune intervention non archivée | 409 |

## Vue du technicien (R3, R7)

| État de l'intervention | Issue du technicien | Sélectionné | Vue |
|------------------------|--------------------|-------------|-----|
| aucune intervention courante | — | — | `VIDE` |
| `EN_CONSULTATION` | `EN_ATTENTE` | — | `CONSULTATION` |
| `EN_CONSULTATION` | a répondu | — | `ATTENTE` |
| `EN_ATTENTE_SELECTION` | `ACCEPTEE*` | — | `ATTENTE` |
| `EN_ATTENTE_SELECTION` | `REFUSEE`, `SANS_REPONSE` | — | `VIDE` |
| `SELECTIONNEE`, `EN_ROUTE`, `SUR_SITE` | — | oui | `ETAPES` |
| `SELECTIONNEE`, `EN_ROUTE`, `SUR_SITE` | — | non | `VIDE` |
| `TERMINEE`, `REFUSEE` | — | — | `VIDE` |

## État du workflow (en mémoire du workflow, reconstruit par rejeu)

Ces variables locales de `InterventionWorkflow` ne sont jamais la source de vérité persistante : Dapr
les reconstruit en rejouant son historique.

- `echeance` (calculée une fois depuis `ctx.getCurrentInstant()`) ;
- `Map<String, Issue> issues` pour les trois techniciens ;
- `EtatIntervention etat` ;
- `String technicienSelectionne`.
