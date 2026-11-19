# Contrat : API REST et SSE

Base : `http://localhost:8080`. En développement, le serveur Vite redirige `/api` vers cette adresse.
Format : JSON, UTF-8. Les heures sont au format ISO-8601 UTC.

Codes communs :

| Code | Sens |
|------|------|
| 202 | Action acceptée et transmise au workflow ; le résultat arrive par SSE |
| 400 | Entrée invalide (champ manquant, frais invalides, rapport vide) |
| 404 | Intervention ou technicien inconnu |
| 409 | Action impossible dans l'état courant (réponse tardive ou en double, sélection en double ou non acceptée, étape hors ordre, création pendant une intervention active, « Finir » trop tôt) |

Corps d'erreur (400, 404, 409) :

```json
{ "code": "CONSULTATION_CLOSE", "message": "La consultation est close : réponse refusée." }
```

Valeurs de `code` : `CHAMP_INVALIDE`, `FRAIS_INVALIDES`, `RAPPORT_OBLIGATOIRE`,
`INTERVENTION_INCONNUE`, `TECHNICIEN_INCONNU`, `INTERVENTION_ACTIVE`, `CONSULTATION_CLOSE`,
`DEJA_REPONDU`, `SELECTION_IMPOSSIBLE`, `ETAPE_HORS_ORDRE`, `NON_SELECTIONNE`, `FINIR_IMPOSSIBLE`.

---

## `GET /api/techniciens`

200 :

```json
["Jean", "Jacques", "Pierre"]
```

## `POST /api/interventions`

Crée l'intervention et démarre son workflow.

```json
{ "titre": "Chaudière en panne", "adresse": "12 rue des Lilas, Lyon" }
```

- 202 `{ "id": "3f0c…" }`
- 400 `CHAMP_INVALIDE` · 409 `INTERVENTION_ACTIVE`

## `GET /api/interventions/courante`

Vue du chef. 204 s'il n'y a aucune intervention non archivée.

200 :

```json
{
  "id": "3f0c…",
  "titre": "Chaudière en panne",
  "adresse": "12 rue des Lilas, Lyon",
  "etat": "EN_ATTENTE_SELECTION",
  "etatLibelle": "En attente de sélection",
  "secondesRestantes": 0,
  "propositions": [
    { "technicien": "Jean",    "issue": "ACCEPTEE",            "montantFrais": null,  "motifFrais": null,          "selectionnable": true },
    { "technicien": "Jacques", "issue": "ACCEPTEE_AVEC_FRAIS", "montantFrais": 40.00, "motifFrais": "Déplacement", "selectionnable": true },
    { "technicien": "Pierre",  "issue": "SANS_REPONSE",        "montantFrais": null,  "motifFrais": null,          "selectionnable": false }
  ],
  "historique": [
    { "survenuA": "2026-11-19T09:42:00Z", "description": "Intervention créée : « Chaudière en panne », 12 rue des Lilas, Lyon" }
  ],
  "finissable": false
}
```

- `secondesRestantes` : calculé par le serveur au moment de la requête ; 0 hors consultation. Le
  navigateur décompte ensuite localement.
- `selectionnable` : vrai seulement si l'état est `EN_ATTENTE_SELECTION` et la proposition acceptée.
- `historique` : toujours renvoyé, dans l'ordre chronologique. L'écran du chef ne l'affiche que pour
  les états `TERMINEE` et `REFUSEE`.
- `finissable` : vrai pour les états `TERMINEE` et `REFUSEE`.

## `GET /api/techniciens/{nom}/vue`

Vue d'un technicien (R3, R7).

200 :

```json
{
  "vue": "CONSULTATION",
  "interventionId": "3f0c…",
  "titre": "Chaudière en panne",
  "adresse": "12 rue des Lilas, Lyon",
  "secondesRestantes": 42,
  "maReponse": null,
  "etat": null,
  "etapeSuivante": null
}
```

| `vue` | Champs renseignés |
|-------|-------------------|
| `VIDE` | aucun |
| `CONSULTATION` | `interventionId`, `titre`, `adresse`, `secondesRestantes` |
| `ATTENTE` | `interventionId`, `titre`, `adresse`, `maReponse` (issue) |
| `ETAPES` | `interventionId`, `titre`, `adresse`, `etat`, `etapeSuivante` (`EN_ROUTE`, `SUR_SITE` ou `TERMINEE` : le seul bouton actif) |

- 404 `TECHNICIEN_INCONNU`

## `POST /api/interventions/{id}/reponses`

```json
{ "technicien": "Jacques", "type": "ACCEPTER_AVEC_FRAIS", "montant": 40.00, "motif": "Déplacement" }
```

`montant` et `motif` ne sont attendus que pour `ACCEPTER_AVEC_FRAIS`.

- 202 (pas de corps)
- 400 `FRAIS_INVALIDES`, `CHAMP_INVALIDE` · 404 · 409 `CONSULTATION_CLOSE`, `DEJA_REPONDU`

## `POST /api/interventions/{id}/selection`

```json
{ "technicien": "Jean" }
```

- 202 · 404 · 409 `SELECTION_IMPOSSIBLE`

## `POST /api/interventions/{id}/etapes`

```json
{ "technicien": "Jean", "etape": "TERMINEE", "rapport": "Brûleur remplacé, chaudière relancée." }
```

`rapport` n'est obligatoire que pour `TERMINEE`.

- 202 · 400 `RAPPORT_OBLIGATOIRE` · 404 · 409 `ETAPE_HORS_ORDRE`, `NON_SELECTIONNE`

## `POST /api/interventions/{id}/finir`

- 202 · 404 · 409 `FINIR_IMPOSSIBLE`

## `GET /api/evenements` (Server-Sent Events)

`Content-Type: text/event-stream`. Le flux reste ouvert. Chaque changement envoie :

```text
event: intervention
data: {"interventionId":"3f0c…","type":"REPONSE"}
```

`type` vaut `CREATION`, `REPONSE`, `CLOTURE`, `SELECTION`, `ETAPE` ou `FIN`.

Toutes les 5 s, le serveur envoie aussi `event: ping` (données vides). Sans aucun message pendant 8 s,
le client considère la connexion comme morte. C'est le cas d'un serveur arrêté derrière le proxy Vite,
qui garde la connexion ouverte. Le client passe alors en repli et se reconnecte. Le message ne porte
**pas** l'état : à chaque message, le client recharge sa vue (`/api/interventions/courante` ou
`/api/techniciens/{nom}/vue`). Un message reçu en double est donc sans effet. Si le flux est en
erreur, le client recharge sa vue toutes les 2 secondes jusqu'à la reconnexion.
