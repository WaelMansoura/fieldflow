# Contrat : workflow Dapr, événements externes, activités et Pub/Sub

Interface interne entre l'API, le workflow et les activités. Les règles de déterminisme et
d'idempotence viennent de la constitution (principes I et II) et de [research.md](../research.md).

## Workflow `InterventionWorkflow`

- **Identifiant d'instance** : l'`id` de l'intervention (UUID généré par le contrôleur).
- **Démarrage** : `DaprWorkflowClient.scheduleNewWorkflow(InterventionWorkflow.class, entree, id)`.
- **Entrée** :

```json
{ "interventionId": "3f0c…", "titre": "…", "adresse": "…", "techniciens": ["Jean","Jacques","Pierre"], "delaiSecondes": 60 }
```

### Déroulé

1. `echeance = InterventionRules.echeance(ctx.getCurrentInstant(), delai)` (R2).
2. Activité `CreerIntervention`. Si elle renvoie `false` (une autre intervention est active), le
   workflow se termine.
3. Consultation : timer `createTimer(echeance)` ; boucle
   `anyOf(timer, waitForExternalEvent("reponse"))` tant que les trois n'ont pas répondu. Une réponse
   acceptée par `InterventionRules` mène à l'activité `EnregistrerReponse` ; une réponse refusée est
   ignorée.
4. Clôture : `InterventionRules.clore(issues)` donne l'état final et la liste des sans-réponse, puis
   l'activité `CloreConsultation` les enregistre.
5. Si l'état est `EN_ATTENTE_SELECTION` : attendre `selection` (sans délai), en ignorant les
   sélections invalides, puis appeler l'activité `SelectionnerTechnicien`.
6. Attendre `etape` jusqu'à `TERMINEE`, en ignorant les étapes invalides. Chaque étape valide mène à
   l'activité `EnregistrerEtape`.
7. Pour les états `TERMINEE` ou `REFUSEE` : attendre `finir`, puis appeler l'activité
   `ArchiverIntervention`, puis terminer le workflow.

Interdits dans le workflow : repository, `DaprClient`, `Instant.now()`, `Random`, `UUID.randomUUID()`,
lecture de configuration, journalisation hors `ctx.isReplaying()`.

### Événements externes (envoyés par `raiseEvent`)

| Nom | Charge utile | Envoyé par |
|-----|--------------|------------|
| `reponse` | `{ "technicien", "type", "montant", "motif", "recuA" }` | `POST …/reponses` |
| `selection` | `{ "technicien", "recuA" }` | `POST …/selection` |
| `etape` | `{ "technicien", "etape", "rapport", "recuA" }` | `POST …/etapes` |
| `finir` | `{ "recuA" }` | `POST …/finir` |

`recuA` : `Instant.now()` pris par le contrôleur, qui sert d'heure du fait dans l'historique.

## Activités

Toutes sont idempotentes : les rejouer donne le même état en base (research R-05). Chacune écrit dans
une transaction, puis publie une notification. La clé et la description de la ligne d'historique
viennent de `InterventionRules` (R9).

- **Politique de reprise** : délai croissant de 1 s à 30 s, 20 tentatives au maximum. Une panne
  passagère de la base ne fait donc pas échouer le workflow.
- **Échec de publication** : il est journalisé et **ne fait pas échouer** l'activité, car l'écriture
  en base est déjà faite. Les écrans se mettent à jour grâce au rafraîchissement de repli toutes
  les 2 s.

| Activité | Entrée | Écritures | Clé(s) historique | Notification |
|----------|--------|-----------|-------------------|--------------|
| `CreerIntervention` | id, titre, adresse, techniciens, creeA, echeance | intervention `EN_CONSULTATION` + 3 propositions `EN_ATTENTE` | `CREATION` | `CREATION` |
| `EnregistrerReponse` | id, technicien, issue, montant, motif, recuA | proposition (si `EN_ATTENTE`) | `REPONSE:{nom}` | `REPONSE` |
| `CloreConsultation` | id, etatFinal, sansReponse[], closA | propositions → `SANS_REPONSE` ; état | `SANS_REPONSE:{nom}`, `CLOTURE` | `CLOTURE` |
| `SelectionnerTechnicien` | id, technicien, recuA | `technicien_selectionne`, état `SELECTIONNEE` | `SELECTION` | `SELECTION` |
| `EnregistrerEtape` | id, etape, rapport, recuA | état (+ rapport si `TERMINEE`) | `EN_ROUTE` / `SUR_SITE` / `TERMINEE` | `ETAPE` |
| `ArchiverIntervention` | id, recuA | `archivee_a` | `FIN` | `FIN` |

## Pub/Sub

- Composant : `pubsub` (Redis, installé par `dapr init`).
- Topic : `intervention-events`.
- Message : `{ "interventionId": "3f0c…", "type": "REPONSE" }` (CloudEvent émis par Dapr).
- Abonné : `POST /dapr/intervention-events` (méthode annotée `@Topic`). Elle transmet le message
  aux `SseEmitter` ouverts et répond 200. La livraison est « au moins une fois », et les doublons
  sont sans effet (voir [api-rest.md](./api-rest.md)).
