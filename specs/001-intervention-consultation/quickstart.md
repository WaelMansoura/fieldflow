# Quickstart : lancer et valider FieldFlow

Guide de validation de bout en bout. Les contrats sont dans [contracts/](./contracts/) et le modèle
dans [data-model.md](./data-model.md).

## Prérequis

- Java 21, Maven 3.9+
- Node.js 20+ et npm
- Docker Desktop (PostgreSQL, conteneurs Dapr, Testcontainers)
- Dapr CLI ; runtime installé une fois avec `dapr init`. Vérifier que les conteneurs `dapr_redis`,
  `dapr_placement` et `dapr_scheduler` tournent (`docker ps`).

## Configuration

```powershell
Copy-Item .env.example .env   # puis renseigner POSTGRES_PASSWORD
```

Délai de consultation : `fieldflow.consultation.delai` dans `backend/src/main/resources/application.yml`
(défaut `60s`).

## Lancement

```powershell
docker compose up -d                                   # PostgreSQL 16
cd backend
dapr run --app-id fieldflow --app-port 8080 --dapr-grpc-port 50001 -- mvn spring-boot:run
# autre terminal
cd frontend; npm install; npm run dev                  # http://localhost:5173
```

Écrans : `http://localhost:5173/chef` et `http://localhost:5173/technicien`. Ouvrir trois onglets
technicien (Jean, Jacques, Pierre) et un onglet chef.

## Tests automatiques

```powershell
cd backend
mvn test      # InterventionRules (unitaires) + intégration API/persistance (Testcontainers)
```

Attendu : tous verts. Docker doit tourner pour Testcontainers.

---

## Étape 0 — Hello world Dapr (avant tout le reste)

1. Lancer l'application avec `dapr run` (voir plus haut).
2. `curl -X POST http://localhost:8080/api/hello`. Les journaux affichent « Bonjour » et l'identifiant
   d'instance.
3. Avant la fin des 30 secondes, arrêter `dapr run` avec Ctrl+C.
4. Attendre que les 30 secondes soient écoulées, puis relancer `dapr run`.
5. **Attendu** : dès le démarrage, « Au revoir » s'affiche **une fois** pour cette instance, et
   « Bonjour » ne s'affiche **pas** une seconde fois.
6. `curl http://localhost:8080/dapr/subscribe` renvoie l'abonnement à `intervention-events` (sinon,
   appliquer le repli de research R-09).

Si l'étape 5 échoue, ne pas continuer : l'installation de Dapr est en cause.

## Scénario A — Parcours nominal (US1, US2)

| # | Action | Attendu |
|---|--------|---------|
| 1 | Chef : titre « Chaudière en panne », adresse « 12 rue des Lilas », Créer | Les trois techniciens voient l'intervention et un compte à rebours de 60 s en moins de 2 s |
| 2 | Jean : Accepter | Chef : Jean « Accepté » en moins de 2 s ; Jean voit l'écran d'attente |
| 3 | Jacques : Accepter avec frais, 0 €, motif vide | Message d'erreur, rien n'est envoyé |
| 4 | Jacques : Accepter avec frais, 40 €, « Déplacement » | Chef : Jacques, 40,00 €, « Déplacement » |
| 5 | Pierre : Refuser | Consultation close immédiatement ; « Sélectionner » actif pour Jean et Jacques ; Pierre sur l'écran vide |
| 6 | Chef : Sélectionner Jean | Chef : « État de l'intervention : Sélectionnée » ; Jacques sur l'écran vide ; Jean : seul « En route » actif |
| 7 | Jean : En route, puis Sur site | Chef : « En route », puis « Sur site » |
| 8 | Jean : Terminer sans rapport | Message « rapport obligatoire », l'état reste « Sur site » |
| 9 | Jean : Terminer avec « Brûleur remplacé » | Chef : « Terminée », liste à puces horodatée (création, 3 réponses, clôture, sélection, en route, sur site, terminée avec le rapport), bouton « Finir » ; Jean sur l'écran vide |
| 10 | Chef : Finir | Écran de création vide |

## Scénario B — Démonstration avec arrêt pendant la consultation (US3, SC-003)

| # | Action | Attendu |
|---|--------|---------|
| 1 | Créer une intervention | Compte à rebours de 60 s |
| 2 | Jean : Accepter ; Jacques : Accepter avec frais 40 €, « Déplacement » ; Pierre : ne rien faire | Chef : 2 réponses, Pierre en attente |
| 3 | Arrêter `dapr run` (Ctrl+C) | Les écrans passent en mode repli (rafraîchissement toutes les 2 s, erreurs réseau) |
| 4 | Attendre plus de 60 s depuis la création, puis relancer `dapr run` | — |
| 5 | Observer les écrans | Au démarrage : Pierre « sans réponse », état « En attente de sélection », réponses de Jean et Jacques intactes, « Sélectionner » actif pour Jean et Jacques |
| 6 | Sélectionner Jean et finir le parcours (scénario A, étapes 6 à 10) | Historique : une ligne par événement, pas de doublon ; la clôture affiche l'heure de l'échéance, pas celle du redémarrage |

## Scénario C — Arrêts à d'autres moments (FR-029, SC-004)

Répéter un arrêt et une relance de `dapr run` :

- avant l'échéance (redémarrage avant 60 s) : le compte à rebours reprend sur le temps restant réel,
  et Pierre peut encore répondre ;
- après la sélection, avant « En route » ;
- après « En route ».

**Attendu** à chaque fois : même état, mêmes réponses, même historique qu'avant l'arrêt ; l'action
suivante fonctionne ; aucune ligne d'historique en double.

## Scénario D — Refus et cas limites (US4, SC-005)

| # | Action | Attendu |
|---|--------|---------|
| 1 | Créer ; les trois refusent | État « Refusée » ; historique et « Finir » chez le chef ; trois écrans vides |
| 2 | Créer ; personne ne répond pendant 60 s | Trois « sans réponse », état « Refusée » |
| 3 | Créer pendant qu'une intervention est active (`curl -X POST …/api/interventions`) | 409 `INTERVENTION_ACTIVE` |
| 4 | Réponse après la clôture (`curl …/reponses`) | 409 `CONSULTATION_CLOSE`, aucun changement |
| 5 | Double sélection (deux `curl …/selection` rapides, puis un troisième) | Un seul technicien sélectionné, une seule ligne « SELECTION » ; le troisième appel reçoit un 409 |
| 6 | « Sur site » avant « En route » (`curl …/etapes`) | 409 `ETAPE_HORS_ORDRE` |
| 7 | Recharger une page à n'importe quel moment | L'écran correspond à l'état courant |
