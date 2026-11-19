---

description: "Tâches de réalisation : consultation des techniciens et suivi d'une intervention"
---

# Tasks: Consultation des techniciens et suivi d'une intervention

**Input**: Design documents from `/specs/001-intervention-consultation/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/api-rest.md,
contracts/workflow.md, quickstart.md

**Tests**: Obligatoires (constitution, principes IV et V). Tests unitaires de `InterventionRules`
écrits et constatés **en échec** avant le code. Tests d'intégration sur une vraie base PostgreSQL
(Testcontainers) pour l'API et les activités. Reprise après redémarrage : scénarios manuels de
quickstart.md.

**Organization**: Tâches groupées par user story (spec.md). US1, US2 et US3 sont toutes P1 ; US3
(reprise) se vérifie sur le parcours complet, donc après US1 et US2.

> Priorisation pour la démo du 20/11 : les tâches marquées **[REPORTÉ]** sont une dette assumée,
> à reprendre après la démo :
> - tests d'intégration hors socle (T034–T038, T046, T049, T058, T063, T064, T070, T074) ;
> - T019 si l'abonnement Pub/Sub marche du premier coup (les écrans se mettent à jour sans passer
>   par le repli) ;
> - scénario C de quickstart (T073), la phase US4 (T074–T076) et T079.
>
> Jamais reportés :
> - le hello world ;
> - les tests unitaires de `InterventionRules` et le workflow ;
> - le **socle d'intégration** : T015, T016, T047, T048 (principe V) ;
> - le scénario B (arrêt pendant la consultation) ;
> - T077 (suppression du hello world, principe VII) ;
> - T081 (`mvn test` avant la démo).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Peut être fait en parallèle (fichiers différents, aucune dépendance en cours)
- **[Story]**: User story concernée (US1, US2, US3, US4)
- Chemins : `backend/src/main/java/com/fieldflow/…` (abrégé `…/fieldflow/`),
  `backend/src/test/java/com/fieldflow/…` (abrégé `test/…/fieldflow/`), `frontend/src/…`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Squelette des deux projets et infrastructure locale

- [ ] T001 Créer `.gitignore` à la racine (`.env`, `target/`, `node_modules/`, `dist/`) et `.env.example` avec `POSTGRES_DB=`, `POSTGRES_USER=`, `POSTGRES_PASSWORD=` sans valeurs (principe VI, research R-12)
- [ ] T002 [P] Créer `docker-compose.yml` à la racine : service `postgres:16`, variables lues depuis `.env`, port 5432, volume nommé
- [ ] T003 [P] Créer `backend/pom.xml` : parent Spring Boot 3.5, Java 21, `spring-boot-starter-web`, `-data-jpa`, `-validation`, driver `postgresql`, `io.dapr.spring:dapr-spring-boot-starter` 1.18.x ; en test : `spring-boot-starter-test`, `spring-boot-testcontainers`, `org.testcontainers:postgresql`, `org.testcontainers:junit-jupiter`
- [ ] T004 Créer `backend/src/main/java/com/fieldflow/FieldFlowApplication.java` annotée `@SpringBootApplication`, et `backend/src/main/java/com/fieldflow/config/DaprWorkflowsConfig.java` annotée `@Configuration`, `@EnableDaprWorkflows` et `@Profile("!test")`. Le moteur de workflow n'est ainsi pas démarré avec le profil `test` (voir T015)
- [ ] T005 Créer `backend/src/main/resources/application.yml` : `spring.config.import: optional:file:../.env[.properties]`, datasource `jdbc:postgresql://localhost:5432/${POSTGRES_DB}` avec `${POSTGRES_USER}`/`${POSTGRES_PASSWORD}`, `spring.jpa.hibernate.ddl-auto: validate`, `spring.sql.init.mode: always`, `server.port: 8080`, `fieldflow.consultation.delai: 60s` (research R-02, R-11)
- [ ] T006 [P] Créer le projet front sans librairie d'interface ni routeur : `frontend/package.json` (react 18, react-dom, typescript, vite, @vitejs/plugin-react), `frontend/tsconfig.json`, `frontend/index.html`, `frontend/vite.config.ts` avec le proxy `/api` → `http://localhost:8080`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Valider Dapr (étape 0 imposée), puis poser le schéma, les entités, les notifications et
les bases de l'API et du front

**⚠️ CRITICAL**: Aucune user story ne commence avant la fin de cette phase. T009 est une porte : si
le hello world échoue, on s'arrête.

### Étape 0 — Hello world Dapr (research R-14)

- [ ] T007 Créer `…/fieldflow/hello/DireBonjourActivity.java` et `…/fieldflow/hello/DireAuRevoirActivity.java` (journalisent « Bonjour {instanceId} » / « Au revoir {instanceId} »), puis `…/fieldflow/hello/HelloWorkflow.java` : activité DireBonjour → `ctx.createTimer(Duration.ofSeconds(30))` → activité DireAuRevoir ; aucun `Instant.now()` ni accès externe dans le workflow
- [ ] T008 Créer `…/fieldflow/hello/HelloController.java` : `POST /api/hello` qui démarre `HelloWorkflow` par `DaprWorkflowClient.scheduleNewWorkflow` et renvoie l'identifiant d'instance
- [ ] T009 Exécuter quickstart.md « Étape 0 », points 1 à 5 (arrêt pendant le timer, relance après 30 s). Attendu : « Au revoir » une seule fois, « Bonjour » pas rejoué. Vérifier aussi les points « runtime compatible » et « `createTimer(ZonedDateTime)` » du tableau final de research.md et noter le résultat dans ce tableau. **Ne pas continuer si l'échec persiste.**

### Schéma, données et entités

- [ ] T010 Créer `backend/src/main/resources/schema.sql` (`CREATE TABLE IF NOT EXISTS`) selon data-model.md : `technicien.nom` ; `intervention.id`, `intervention.titre`, `intervention.adresse`, `intervention.etat`, `intervention.cree_a`, `intervention.echeance`, `intervention.technicien_selectionne`, `intervention.rapport`, `intervention.archivee_a` ; `proposition.id`, `proposition.intervention_id`, `proposition.technicien`, `proposition.issue`, `proposition.montant_frais`, `proposition.motif_frais`, `proposition.repondu_a` ; `evenement_historique.id`, `evenement_historique.intervention_id`, `evenement_historique.cle`, `evenement_historique.survenu_a`, `evenement_historique.description`. Ajouter l'index unique partiel `intervention_active`, l'unique `(intervention_id, technicien)` et l'unique `(intervention_id, cle)` (`CREATE UNIQUE INDEX IF NOT EXISTS`)
- [ ] T011 [P] Créer `backend/src/main/resources/data.sql` : insérer Jean, Jacques, Pierre dans `technicien.nom` (data-model.md) avec `ON CONFLICT DO NOTHING`
- [ ] T012 [P] Créer les énumérations Java pures dans `…/fieldflow/regles/` : `EtatIntervention.java` (avec libellé affiché), `Issue.java`, `TypeReponse.java`, `Etape.java`, `VueTechnicien.java`, valeurs exactes de data-model.md (« Énumérations ») ; ajouter `Decision.java` (OK / INVALIDE / MAUVAIS_ETAT / INCONNU + code d'erreur de contracts/api-rest.md) et `ResultatCloture.java` (état final + liste des sans-réponse). Aucun import Spring, Dapr ni JPA
- [ ] T013 [P] Créer les entités JPA dans `…/fieldflow/persistance/` : `Technicien.java` (`technicien.nom`), `Intervention.java` (champs `intervention.*` de data-model.md, `intervention.etat` en `EnumType.STRING`), `Proposition.java` (`proposition.*`, `proposition.issue` en `EnumType.STRING`, `proposition.montant_frais` en `BigDecimal` échelle 2), `EvenementHistorique.java` (`evenement_historique.*`) ; mapping compatible avec `ddl-auto=validate`
- [ ] T014 Créer les repositories dans `…/fieldflow/persistance/` avec requêtes natives idempotentes (research R-05) : `InterventionRepository` (`insererSiAbsente` en `ON CONFLICT DO NOTHING`, `trouverCourante` où `archivee_a IS NULL`, mises à jour conditionnelles `WHERE etat = :attendu`), `PropositionRepository` (`insererSiAbsente`, `mettreAJourSiEnAttente` `WHERE issue = 'EN_ATTENTE'`, `trouverParIntervention`), `EvenementHistoriqueRepository` (`insererSiAbsent` `ON CONFLICT (intervention_id, cle) DO NOTHING`, liste triée par `survenu_a`, puis `id`)
- [ ] T015 [P] Créer la base des tests d'intégration `test/…/fieldflow/PostgresIntegrationTest.java` : `@SpringBootTest`, `PostgreSQLContainer("postgres:16")` avec `@ServiceConnection`, `@ActiveProfiles("test")` (désactive `DaprWorkflowsConfig`, donc aucun enregistrement de workflow ni démarrage du moteur), `@MockitoBean` pour `DaprWorkflowClient` et `DaprClient` (aucun appel gRPC). Le contexte Spring démarre donc sans sidecar. Nettoyage des tables entre les tests (sauf `technicien`). Vérification : un test vide qui charge le contexte passe avec `dapr` arrêté
- [ ] T016 Écrire `test/…/fieldflow/persistance/PersistanceIT.java` : chaque `insererSiAbsent…` appelé deux fois donne une seule ligne ; une seconde intervention non archivée est refusée par `intervention_active` ; `mettreAJourSiEnAttente` ne modifie pas une proposition déjà répondue

### Notifications (research R-09)

- [ ] T017 Créer `…/fieldflow/workflow/activites/Notificateur.java` : `DaprClient.publishEvent("pubsub", "intervention-events", {interventionId, type})` (contracts/workflow.md, Pub/Sub). Une erreur de publication est **journalisée et non propagée** : l'activité réussit, et le rafraîchissement de repli toutes les 2 s met les écrans à jour
- [ ] T018 Créer `…/fieldflow/api/EvenementsController.java` : `GET /api/evenements` qui renvoie un `SseEmitter` (sans délai, retiré de la liste à la fin ou en erreur) ; `POST /dapr/intervention-events` annotée `@Topic(name = "intervention-events", pubsubName = "pubsub")` qui envoie `event: intervention` à tous les émetteurs et répond 200 (contracts/api-rest.md)
- [ ] T019 [REPORTÉ si l'abonnement marche du premier coup] Exécuter quickstart.md « Étape 0 », point 6 (`curl http://localhost:8080/dapr/subscribe`). En cas d'échec, appliquer le repli de research R-09 : `backend/dapr/subscription.yaml`, `--resources-path` ajouté dans quickstart.md

### Socle API et front

- [ ] T020 [P] Créer `…/fieldflow/api/dto/ErreurDto.java` (`code`, `message`), `…/fieldflow/api/RegleRefuseeException.java` (porte une `Decision`) et `…/fieldflow/api/GestionErreurs.java` (`@RestControllerAdvice`) : INVALIDE → 400, INCONNU → 404, MAUVAIS_ETAT → 409 ; `MethodArgumentNotValidException` → 400 `CHAMP_INVALIDE`
- [ ] T021 [P] Créer `…/fieldflow/api/TechnicienController.java` avec `GET /api/techniciens` (liste des `technicien.nom`)
- [ ] T022 [P] Créer `frontend/src/api.ts` : types et fonctions `fetch` pour chaque endpoint de contracts/api-rest.md ; une erreur renvoie `{code, message}`
- [ ] T023 [P] Créer `frontend/src/useEvenements.ts` : `EventSource('/api/evenements')`, appelle `recharger()` à l'ouverture et à chaque `event: intervention` ; en erreur, `recharger()` toutes les 2 s jusqu'à la reconnexion
- [ ] T024 Créer `frontend/src/main.tsx` et `frontend/src/App.tsx` : affiche `Chef` pour `/chef`, `Technicien` pour `/technicien`, sinon deux liens

**Checkpoint**: Dapr validé, base et notifications prêtes. Les user stories peuvent commencer.

---

## Phase 3: User Story 1 - Consulter les techniciens et sélectionner une proposition (Priority: P1) 🎯 MVP

**Goal**: Créer une intervention, recevoir les trois réponses en 60 s au plus, clore la
consultation et sélectionner une proposition acceptée.

**Independent Test**: quickstart.md scénario A, étapes 1 à 6.

### Tests unitaires des règles (écrits AVANT le code, constatés en échec) ⚠️

- [ ] T025 [US1] Dans `test/…/fieldflow/regles/InterventionRulesTest.java`, tests de création : `intervention.titre` et `intervention.adresse` (data-model.md) vides ou de plus de 200 caractères → INVALIDE `CHAMP_INVALIDE` ; intervention active existante → MAUVAIS_ETAT `INTERVENTION_ACTIVE` ; R1 : `propositionsInitiales([Jean, Jacques, Pierre])` → trois `EN_ATTENTE`
- [ ] T026 [US1] Dans le même fichier, tests R5 sur `proposition.montant_frais` et `proposition.motif_frais` (data-model.md) : 0 €, négatif, absent, plus de 2 décimales → `FRAIS_INVALIDES` ; motif vide ou de 201 caractères → `FRAIS_INVALIDES` ; 40,00 € + « Déplacement » → OK ; ACCEPTER/REFUSER sans frais → OK
- [ ] T027 [US1] Dans le même fichier, tests R4 : réponse refusée si état ≠ `EN_CONSULTATION` ou maintenant ≥ échéance (`CONSULTATION_CLOSE`), ou issue ≠ `EN_ATTENTE` (`DEJA_REPONDU`)
- [ ] T028 [US1] Dans le même fichier, tests R3 : `consultationComplete` vrai seulement quand aucune issue n'est `EN_ATTENTE`. Pour `clore` : Jean ACCEPTEE, Jacques ACCEPTEE_AVEC_FRAIS, Pierre EN_ATTENTE → `EN_ATTENTE_SELECTION` avec sans-réponse [Pierre]. **Cas Refusée** : trois `REFUSEE` → `REFUSEE` sans sans-réponse ; trois `EN_ATTENTE` → `REFUSEE` avec sans-réponse [Jean, Jacques, Pierre] ; deux `REFUSEE` + un `EN_ATTENTE` → `REFUSEE` avec ce seul sans-réponse
- [ ] T029 [US1] Dans le même fichier, tests R6 : sélection OK seulement si l'état est `EN_ATTENTE_SELECTION` et l'issue `ACCEPTEE` ou `ACCEPTEE_AVEC_FRAIS` ; proposition refusée ou sans réponse, ou état `SELECTIONNEE` (seconde sélection) → `SELECTION_IMPOSSIBLE`
- [ ] T030 [US1] Dans le même fichier, tests de `vueTechnicien` pour chaque ligne du tableau « Vue du technicien » de data-model.md (aucune intervention, `EN_CONSULTATION`, `EN_ATTENTE_SELECTION`, `SELECTIONNEE` sélectionné ou non), **dont `REFUSEE` → `VIDE` pour les trois techniciens**
- [ ] T031 [US1] Dans le même fichier, tests R2 : `echeance(debut, Duration.ofSeconds(60))` = `debut` + 60 s ; `echeance(debut, Duration.ofSeconds(30))` = `debut` + 30 s (délai modifiable)
- [ ] T032 [US1] Dans le même fichier, tests R9 : pour chaque ligne du tableau « `cle` » de data-model.md, la méthode de construction renvoie la clé et la description attendues (`LigneHistorique`) : `CREATION` (titre, adresse) ; `REPONSE:{nom}` pour accepté, accepté avec frais (« 40,00 € de frais (Déplacement) ») et refusé ; `SANS_REPONSE:{nom}` ; `CLOTURE` avec et sans acceptation ; `SELECTION` ; `EN_ROUTE`, `SUR_SITE`, `TERMINEE` (avec le rapport) ; `FIN`

### Implémentation des règles

- [ ] T033 [US1] Implémenter dans `…/fieldflow/regles/InterventionRules.java` les méthodes nécessaires pour faire passer T025 à T032 (`validerCreation`, `peutCreer`, `propositionsInitiales`, `validerFrais`, `peutRepondre`, `issuePour(TypeReponse)`, `consultationComplete`, `clore`, `peutSelectionner`, `vueTechnicien`, `echeance`, et les méthodes de construction des lignes d'historique). Créer `…/fieldflow/regles/LigneHistorique.java` (record `cle`, `description`). Java pur, sans import Spring, Dapr ni JPA

### Tests d'intégration des activités (avant le code) ⚠️

- [ ] T034 [P] [US1] [REPORTÉ] `test/…/fieldflow/workflow/activites/CreerInterventionActivityIT.java` : deux exécutions → 1 intervention `EN_CONSULTATION`, 3 propositions `EN_ATTENTE`, 1 événement `CREATION` ; une création avec un autre id alors qu'une intervention est active → renvoie `false`, rien n'est écrit
- [ ] T035 [P] [US1] [REPORTÉ] `test/…/fieldflow/workflow/activites/EnregistrerReponseActivityIT.java` : deux exécutions de la réponse de Jacques (40,00 €, « Déplacement ») → une issue `ACCEPTEE_AVEC_FRAIS`, `proposition.repondu_a` = `recuA`, un seul événement `REPONSE:Jacques`
- [ ] T036 [P] [US1] [REPORTÉ] `test/…/fieldflow/workflow/activites/CloreConsultationActivityIT.java` : deux exécutions → Pierre `SANS_REPONSE`, un `SANS_REPONSE:Pierre`, un `CLOTURE` daté `closA`, état `EN_ATTENTE_SELECTION`
- [ ] T037 [P] [US1] [REPORTÉ] `test/…/fieldflow/workflow/activites/SelectionnerTechnicienActivityIT.java` : deux exécutions → `intervention.technicien_selectionne` = Jean, état `SELECTIONNEE`, un seul `SELECTION`
- [ ] T038 [P] [US1] [REPORTÉ] `test/…/fieldflow/workflow/activites/ArchiverInterventionActivityIT.java` : deux exécutions → `intervention.archivee_a` renseigné une fois, un seul `FIN` ; une nouvelle intervention peut ensuite être créée

### Implémentation des activités

Les clés et descriptions d'historique viennent de `InterventionRules` (T033), les activités ne
construisent aucun texte.

- [ ] T039 [US1] Créer `…/fieldflow/workflow/activites/CreerInterventionActivity.java` : en une transaction, `intervention.*` (état `EN_CONSULTATION`, `intervention.echeance` reçue du workflow), 3 `proposition.issue` = `EN_ATTENTE`, ligne d'historique `CREATION` ; renvoie `false` en cas de conflit sur `intervention_active` ; puis notifie `CREATION`
- [ ] T040 [US1] Créer `…/fieldflow/workflow/activites/EnregistrerReponseActivity.java` : `mettreAJourSiEnAttente` (`proposition.issue`, `proposition.montant_frais`, `proposition.motif_frais`, `proposition.repondu_a`) + ligne `REPONSE:{nom}` ; notifie `REPONSE`
- [ ] T041 [US1] Créer `…/fieldflow/workflow/activites/CloreConsultationActivity.java` : sans-réponse → `SANS_REPONSE`, `intervention.etat` mis à jour depuis `EN_CONSULTATION`, lignes `SANS_REPONSE:{nom}` et `CLOTURE` ; notifie `CLOTURE`
- [ ] T042 [US1] Créer `…/fieldflow/workflow/activites/SelectionnerTechnicienActivity.java` : `intervention.technicien_selectionne` et `intervention.etat` = `SELECTIONNEE` depuis `EN_ATTENTE_SELECTION`, ligne `SELECTION` ; notifie `SELECTION`
- [ ] T043 [US1] Créer `…/fieldflow/workflow/activites/ArchiverInterventionActivity.java` : `intervention.archivee_a` renseigné s'il est null, ligne `FIN` ; notifie `FIN`

### Workflow

- [ ] T044 [P] [US1] Créer les records d'entrée et d'événements dans `…/fieldflow/workflow/` : `EntreeWorkflow.java`, `ReponseRecue.java`, `SelectionRecue.java` (champs de contracts/workflow.md, dont `recuA`)
- [ ] T045 [US1] Créer `…/fieldflow/workflow/InterventionWorkflow.java`, étapes 1 à 5 de contracts/workflow.md :
  - échéance = `InterventionRules.echeance(ctx.getCurrentInstant(), delai)`, puis `CreerIntervention` ;
  - un seul `createTimer(echeance)` et une boucle `anyOf(timer, waitForExternalEvent("reponse"))`, filtrée par `InterventionRules` ;
  - clôture, puis attente de `selection` en ignorant les sélections invalides ;
  - activités appelées avec une politique de reprise à délai croissant de 1 s à 30 s, 20 tentatives au maximum ;
  - interdits : repository, `DaprClient`, `Instant.now()`, `Random`, lecture de configuration.

  **Temporaire (MVP)** : à l'état `REFUSEE`, appeler `ArchiverIntervention` avec l'heure de clôture puis terminer, pour qu'une nouvelle intervention puisse être créée. Après la sélection, le workflow se termine. T061 et T062 remplacent ce comportement.

### Tests d'intégration de l'API (avant le code) ⚠️

- [ ] T046 [P] [US1] [REPORTÉ] `test/…/fieldflow/api/CreationInterventionIT.java` : `POST /api/interventions` → 202 + `scheduleNewWorkflow` appelé avec `delaiSecondes` issu de `fieldflow.consultation.delai` ; titre vide → 400 `CHAMP_INVALIDE` ; intervention active → 409 `INTERVENTION_ACTIVE` ; `GET /api/interventions/courante` → 204 sans intervention, 200 au format de contracts/api-rest.md (`secondesRestantes` calculé depuis `intervention.echeance`, 0 si dépassée ; `selectionnable` vrai seulement pour les propositions acceptées à l'état `EN_ATTENTE_SELECTION`)
- [ ] T047 [P] [US1] `test/…/fieldflow/api/ReponsesIT.java` : 202 + `raiseEvent(id, "reponse", …)` ; 400 `FRAIS_INVALIDES` ; 409 `CONSULTATION_CLOSE` (échéance passée ou état ≠ `EN_CONSULTATION`) ; 409 `DEJA_REPONDU` ; 404 intervention ou technicien inconnu
- [ ] T048 [P] [US1] `test/…/fieldflow/api/SelectionIT.java` : 202 + `raiseEvent("selection")` ; 409 `SELECTION_IMPOSSIBLE` pour une proposition refusée ou sans réponse, ou si l'état n'est pas `EN_ATTENTE_SELECTION`
- [ ] T049 [P] [US1] [REPORTÉ] `test/…/fieldflow/api/VueTechnicienIT.java` : `GET /api/techniciens/{nom}/vue` renvoie `VIDE`, `CONSULTATION` (avec `secondesRestantes`), `ATTENTE` selon l'état en base ; 404 `TECHNICIEN_INCONNU`

### Implémentation de l'API

- [ ] T050 [P] [US1] Créer les DTO dans `…/fieldflow/api/dto/` : `CreationRequete` (`@NotBlank @Size(max=200)` pour `intervention.titre` et `intervention.adresse`, data-model.md), `ReponseRequete`, `SelectionRequete`, `InterventionCouranteDto`, `PropositionDto`, `EvenementDto`, `VueTechnicienDto` (contracts/api-rest.md)
- [ ] T051 [US1] Créer `…/fieldflow/api/InterventionController.java` :
  - `POST /api/interventions` : UUID, délai configuré, `peutCreer`, `scheduleNewWorkflow` ;
  - `GET /api/interventions/courante` : `selectionnable` de chaque proposition = `InterventionRules.peutSelectionner(etat, issue)`, sans logique recopiée ;
  - `POST /{id}/reponses` : `validerFrais`, puis `peutRepondre` avec `Instant.now()`, puis `raiseEvent("reponse")` avec `recuA` ;
  - `POST /{id}/selection` : `peutSelectionner`, puis `raiseEvent("selection")` ;
  - un refus des règles lève `RegleRefuseeException`.
- [ ] T052 [US1] Ajouter `GET /api/techniciens/{nom}/vue` dans `…/fieldflow/api/TechnicienController.java` : calcule la vue avec `InterventionRules.vueTechnicien` et remplit les champs selon la vue (contracts/api-rest.md)

### Front

- [ ] T053 [US1] Créer `frontend/src/pages/Chef.tsx` : écran de création (titre, adresse, « Créer », erreurs affichées) ; écran des propositions (technicien, réponse, frais et motif, compte à rebours local depuis `secondesRestantes`, « Sélectionner » actif seulement si `selectionnable`) ; après la sélection, uniquement « État de l'intervention : {etatLibelle} » ; rechargement par `useEvenements`
- [ ] T054 [US1] Créer `frontend/src/pages/Technicien.tsx` : choix du nom (lien vers `/technicien?nom=…`) ; vue `VIDE` ; vue `CONSULTATION` (titre, adresse, compte à rebours, Accepter / Accepter avec frais (montant + motif) / Refuser, erreur du serveur affichée) ; vue `ATTENTE` (« Réponse envoyée : … ») ; rechargement par `useEvenements`
- [ ] T055 [US1] Exécuter quickstart.md scénario A, étapes 1 à 6, et corriger les écarts

**Checkpoint**: Consultation et sélection fonctionnent de bout en bout (MVP). Une intervention
Refusée est archivée automatiquement, ce qui permet d'en créer une nouvelle. Après une sélection, il
faut attendre US2 (« Finir ») avant de pouvoir en créer une autre.

---

## Phase 4: User Story 2 - Faire avancer l'intervention jusqu'au rapport (Priority: P1)

**Goal**: En route → Sur site → Terminée avec le rapport ; historique horodaté et « Finir ».

**Independent Test**: quickstart.md scénario A complet (étapes 1 à 10).

### Tests unitaires (avant le code) ⚠️

- [ ] T056 [US2] Dans `test/…/fieldflow/regles/InterventionRulesTest.java`, tests R8 : `etapeSuivante` (`SELECTIONNEE` → `EN_ROUTE` → `SUR_SITE` → `TERMINEE`, aucune après) ; étape hors ordre ou répétée → `ETAPE_HORS_ORDRE` ; technicien non sélectionné → `NON_SELECTIONNE` ; `TERMINEE` avec un `intervention.rapport` (data-model.md) vide → `RAPPORT_OBLIGATOIRE` ; tests R10 : `peutFinir` vrai seulement pour `TERMINEE` et `REFUSEE` (`FINIR_IMPOSSIBLE`) ; vue `ETAPES` pour le sélectionné et `VIDE` après `TERMINEE`
- [ ] T057 [US2] Implémenter `etapeSuivante`, `peutPasserEtape` et `peutFinir` dans `…/fieldflow/regles/InterventionRules.java`

### Activités (test avant le code) ⚠️

- [ ] T058 [P] [US2] [REPORTÉ] `test/…/fieldflow/workflow/activites/EnregistrerEtapeActivityIT.java` : deux exécutions de chaque étape → un seul événement `EN_ROUTE` / `SUR_SITE` / `TERMINEE` ; `intervention.rapport` enregistré à `TERMINEE` ; une étape dont l'état de départ ne correspond pas ne change rien
- [ ] T059 [US2] Créer `…/fieldflow/workflow/activites/EnregistrerEtapeActivity.java` : `intervention.etat` mis à jour depuis l'état attendu, `intervention.rapport` à `TERMINEE`, ligne d'historique de l'étape (construite par `InterventionRules`) ; notifie `ETAPE`

### Workflow

- [ ] T060 [P] [US2] Créer `…/fieldflow/workflow/EtapeRecue.java` et `…/fieldflow/workflow/FinRecue.java` (contracts/workflow.md)
- [ ] T061 [US2] Étendre `…/fieldflow/workflow/InterventionWorkflow.java`, étape 6 : après la sélection, attendre `etape` jusqu'à `TERMINEE` en ignorant les étapes invalides (`peutPasserEtape`), avec `EnregistrerEtape` à chaque étape valide
- [ ] T062 [US2] Étendre `…/fieldflow/workflow/InterventionWorkflow.java`, étape 7 : pour `TERMINEE` **et** `REFUSEE`, attendre `finir`, appeler `ArchiverIntervention` avec le `recuA` de l'événement, puis terminer. **Supprimer l'archivage automatique à `REFUSEE` ajouté en T045.**

### API (test avant le code) ⚠️

- [ ] T063 [P] [US2] [REPORTÉ] `test/…/fieldflow/api/EtapesIT.java` : 202 + `raiseEvent("etape")` ; 400 `RAPPORT_OBLIGATOIRE` ; 409 `ETAPE_HORS_ORDRE` (« Sur site » avant « En route ») ; 409 `NON_SELECTIONNE`
- [ ] T064 [P] [US2] [REPORTÉ] `test/…/fieldflow/api/FinirIT.java` : 202 + `raiseEvent("finir")` pour `TERMINEE` ; 409 `FINIR_IMPOSSIBLE` pour les autres états ; `GET …/courante` renvoie `finissable` (vrai seulement pour `TERMINEE` et `REFUSEE`) et l'`historique` trié
- [ ] T065 [US2] Ajouter `EtapeRequete` dans `…/fieldflow/api/dto/`, ainsi que `POST /{id}/etapes` (`peutPasserEtape`) et `POST /{id}/finir` (`peutFinir`) dans `…/fieldflow/api/InterventionController.java`. Dans `GET …/courante`, `finissable` = `InterventionRules.peutFinir(etat)`, sans logique recopiée. Ajouter `etat` et `etapeSuivante` (= `InterventionRules.etapeSuivante`) pour la vue `ETAPES` dans `…/fieldflow/api/TechnicienController.java`

### Front

- [ ] T066 [US2] Dans `frontend/src/pages/Technicien.tsx`, vue `ETAPES` : « En route », « Sur site », « Terminer » (avec le champ rapport), seul `etapeSuivante` actif ; erreur du serveur affichée
- [ ] T067 [US2] Dans `frontend/src/pages/Chef.tsx`, quand `finissable` (état `TERMINEE` ou `REFUSEE`) : état, liste à puces de l'`historique` (heure HH:MM:SS locale + description) et « Finir », qui revient à l'écran de création
- [ ] T068 [US2] Exécuter quickstart.md scénario A complet (étapes 1 à 10)

**Checkpoint**: Parcours nominal complet, de la création au rapport.

---

## Phase 5: User Story 3 - Reprendre le parcours après un arrêt de l'application (Priority: P1)

**Goal**: Aucune perte ni aucun doublon à travers un arrêt ; une échéance dépassée pendant l'arrêt
est traitée au redémarrage.

**Independent Test**: quickstart.md scénarios B et C.

- [ ] T069 [US3] Relire `…/fieldflow/workflow/InterventionWorkflow.java` (hors `activites/`) : aucun `Instant.now`, `Random`, `UUID.randomUUID`, repository, `DaprClient` ni `@Value` ; journalisation seulement si `!ctx.isReplaying()` ; corriger les écarts (principe II)
- [ ] T070 [P] [US3] [REPORTÉ] Ajouter dans `test/…/fieldflow/api/CreationInterventionIT.java` un test : avec `intervention.echeance` à +20 s, `secondesRestantes` vaut environ 20 (temps réel restant, compte à rebours repris après redémarrage) ; avec `intervention.echeance` passée, il vaut 0
- [ ] T071 [P] [US3] Dans `frontend/src/useEvenements.ts`, vérifier qu'à la reconnexion de l'`EventSource` après un redémarrage la vue est rechargée et le repli de 2 s arrêté ; afficher discrètement « Connexion perdue, nouvelle tentative… » pendant l'erreur
- [ ] T072 [US3] Exécuter quickstart.md scénario B (démonstration : Pierre sans réponse, échéance dépassée pendant l'arrêt). Vérifier en base qu'il n'y a aucun doublon dans `evenement_historique` et que l'heure de `CLOTURE` est égale à `intervention.echeance`
- [ ] T073 [US3] [REPORTÉ] Exécuter quickstart.md scénario C (arrêts avant l'échéance, après la sélection, après « En route ») et corriger les écarts

**Checkpoint**: Fiabilité démontrée (FR-029 à FR-032, SC-003, SC-004).

---

## Phase 6: User Story 4 - Aucun technicien n'accepte (Priority: P2)

**Goal**: Passage à Refusée, techniciens sur l'écran vide, historique et « Finir » chez le chef.
Les règles de ce cas (R3 Refusée, vue `VIDE`) sont testées et codées en US1 (T028, T030, T033).

**Independent Test**: quickstart.md scénario D, étapes 1 et 2.

- [ ] T074 [P] [US4] [REPORTÉ] Ajouter dans `test/…/fieldflow/workflow/activites/CloreConsultationActivityIT.java` le cas `REFUSEE` : trois `SANS_REPONSE`, `intervention.etat` = `REFUSEE`, un `CLOTURE` « aucune acceptation »
- [ ] T075 [US4] [REPORTÉ] Dans `frontend/src/pages/Chef.tsx`, passer directement de l'écran des propositions à « État de l'intervention : Refusée » + historique + « Finir » quand l'état est `REFUSEE`
- [ ] T076 [US4] [REPORTÉ] Exécuter quickstart.md scénario D (étapes 1 à 7 : refus, cas limites en 409, rechargement)

**Checkpoint**: Toutes les user stories fonctionnent.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T077 Supprimer `…/fieldflow/hello/` (workflow, activités, `POST /api/hello`), car il ne fait pas partie de la spec (principe VII) ; dans quickstart.md, indiquer que l'étape 0 a été validée et le code retiré
- [ ] T078 [P] Vérifier qu'aucun secret n'est dans le dépôt : `.env` absent de l'arborescence suivie, aucun mot de passe dans `application.yml`, `docker-compose.yml` ou les tests ; `.env.example` sans valeurs (principe VI)
- [ ] T079 [REPORTÉ] Lancer `mvn test` dans `backend/` (unitaires + intégration, tous verts), puis rejouer quickstart.md scénario A après la suppression du hello world
- [ ] T080 Écrire README.md : le problème en 3 phrases, le schéma d'architecture (workflow Dapr, activités, PostgreSQL, Pub/Sub → SSE), pourquoi Dapr Workflow, comment lancer (dapr init, docker compose, dapr run, npm run dev), et la démarche Spec Kit (lien vers specs/).
- [ ] T081 Avant la démo, lancer `mvn test` dans `backend/` : tests unitaires de `InterventionRules` et socle d'intégration (`PersistanceIT`, `ReponsesIT`, `SelectionIT`), tous verts, `dapr` arrêté

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** : aucune dépendance
- **Foundational (Phase 2)** : après Setup ; **T009 (hello world) bloque tout le reste**
- **US1 (Phase 3)** : après Foundational
- **US2 (Phase 4)** : après US1 (elle prolonge le même workflow, et l'étape suit la sélection)
- **US3 (Phase 5)** : après US1 et US2 (reprise vérifiée sur le parcours complet)
- **US4 (Phase 6)** : après US1 (clôture) et T062 (attente de `finir` à l'état `REFUSEE`) ; peut se faire en parallèle de US3
- **Polish (Phase 7)** : après toutes les stories

### Within Each User Story

- Tests de règles écrits et en échec → `InterventionRules` → tests d'activités → activités →
  workflow → tests d'API → API → front → scénario de quickstart
- T025 à T032 et T056 sont dans le même fichier de test : à faire en séquence
- `InterventionController.java`, `TechnicienController.java`, `InterventionWorkflow.java`,
  `Chef.tsx` et `Technicien.tsx` sont modifiés par plusieurs stories : jamais en parallèle

### Parallel Opportunities

- Phase 1 : T002, T003 et T006
- Phase 2 : T011, T012, T013 et T015 ; puis T020 à T023
- US1 : T034 à T038 (tests d'activités) ; T044 ; T046 à T049 (tests d'API) ; T050
- US2 : T058 ; T060 ; T063 et T064
- US3 : T070 et T071
- US4 : T074, en parallèle de la phase US3

---

## Parallel Example: User Story 1

```text
# Tests d'intégration des activités (après T033) :
Task: "T034 CreerInterventionActivityIT.java"
Task: "T035 EnregistrerReponseActivityIT.java"
Task: "T036 CloreConsultationActivityIT.java"
Task: "T037 SelectionnerTechnicienActivityIT.java"
Task: "T038 ArchiverInterventionActivityIT.java"

# Tests d'intégration de l'API (après T045) :
Task: "T046 CreationInterventionIT.java"
Task: "T047 ReponsesIT.java"
Task: "T048 SelectionIT.java"
Task: "T049 VueTechnicienIT.java"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1, puis Phase 2 avec la porte T009 (hello world Dapr réussi)
2. Phase 3 (US1), puis **STOP** : scénario A, étapes 1 à 6

### Incremental Delivery

1. US1 → consultation et sélection démontrables
2. US2 → parcours complet jusqu'au rapport et « Finir »
3. US3 → démonstration avec arrêt (scénario B), objectif principal de fiabilité
4. US4 → cas Refusée et cas limites
5. Polish → suppression du hello world, vérification des secrets, tests verts

---

## Notes

- [P] = fichiers différents, sans dépendance en cours
- Chaque test de règle est constaté en échec avant l'implémentation (principe IV)
- Toute écriture en base passe par une activité ; les contrôleurs ne font que lire et envoyer des
  événements
- Tout changement de besoin repasse par spec.md, puis plan.md, puis tasks.md (principe VII)
