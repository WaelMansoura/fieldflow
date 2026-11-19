# Implementation Plan: Consultation des techniciens et suivi d'une intervention

**Branch**: `001-intervention-consultation` | **Date**: 2026-11-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-intervention-consultation/spec.md`

## Summary

Le chef crée une intervention. Les trois techniciens ont 60 secondes pour l'accepter, l'accepter avec
frais ou la refuser. Le chef sélectionne ensuite une proposition acceptée, et le technicien choisi
fait avancer l'intervention jusqu'au rapport. Chaque fait est horodaté dans un historique.

Approche :

- Un workflow Dapr `InterventionWorkflow` par intervention orchestre le parcours. Il reçoit les
  actions comme événements externes et attend la consultation avec un timer durable calculé depuis
  `ctx.getCurrentInstant()`.
- Les écritures en base PostgreSQL et la publication des notifications se font dans des activités
  idempotentes. Le code du workflow reste déterministe.
- Toutes les règles R1 à R10 vivent dans `InterventionRules`, une classe Java pure testée en TDD,
  appelée par l'API (contrôles 400 et 409), par le workflow (autorité finale) et pour calculer la
  vue de chaque technicien. Cela comprend le calcul de l'échéance (R2) et la construction des
  lignes d'historique, clé et description (R9), utilisées par les activités.
- Les écrans React sont rafraîchis par Dapr Pub/Sub puis Server-Sent Events.

## Technical Context

**Language/Version**: Java 21 (back-end) ; TypeScript 5 (front-end)

**Primary Dependencies**: Spring Boot 3.5 (Web, Data JPA, Validation), Maven ;
`io.dapr.spring:dapr-spring-boot-starter` 1.18.x (`@EnableDaprWorkflows`, `DaprWorkflowClient`,
`DaprClient`) ; React 18 + Vite, sans librairie d'interface ni routeur

**Storage**: PostgreSQL 16 (Docker Compose) pour les données affichées ; state store Dapr (Redis de
`dapr init`) pour l'historique des workflows

**Testing**: JUnit 5 (règles, TDD) ; `@SpringBootTest` + Testcontainers PostgreSQL 16 (API,
activités, persistance) ; scénarios manuels de reprise ([quickstart.md](./quickstart.md))

**Target Platform**: Poste de développement Windows avec Docker Desktop et Dapr en mode local
(`dapr run`), navigateurs récents

**Project Type**: Application web (back-end + front-end)

**Performance Goals**: Chaque changement visible sur les écrans concernés en moins de 2 s (FR-027)

**Constraints**:
- aucun état perdu ni action en double à travers un arrêt (FR-029 à FR-032) ;
- délai de consultation configurable (défaut 60 s) ;
- réalisable en une journée.

**Scale/Scope**: 1 chef, 3 techniciens, 1 intervention active à la fois ; 2 pages (`/chef`,
`/technicien`) ; environ 9 endpoints, 1 workflow, 6 activités

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principe | Application dans ce plan | Statut |
|----------|--------------------------|--------|
| I. Fiabilité du workflow | État du parcours tenu par le workflow Dapr (rejoué depuis le state store) et miroir en PostgreSQL. Échéance = timer durable. Les seules données en mémoire sont les connexions SSE, qui ne sont pas des données de workflow : le navigateur se reconnecte et recharge sa vue. | ✅ |
| II. Déterminisme Dapr | Workflow sans base, sans réseau, sans `Instant.now()` ni hasard ; heures fournies par `getCurrentInstant()` ou par les événements (`recuA`). Activités idempotentes : clés primaires, uniques `(intervention, technicien)` et `(intervention, cle)`, `ON CONFLICT DO NOTHING`, mises à jour conditionnelles ([research](./research.md) R-03 à R-06). | ✅ |
| III. Règles métier isolées | `InterventionRules` + énumérations dans le paquet `regles`, sans import Spring, Dapr ni JPA. Contrôleurs, workflow et activités ne font qu'appeler ses méthodes. Elle porte aussi la vue technicien (R-10), l'échéance (R2) et les lignes d'historique (R9). Les champs `selectionnable` et `finissable` de l'API sont calculés par `peutSelectionner` et `peutFinir`. | ✅ |
| IV. Tests unitaires d'abord | Un test par règle R1 à R10 (et par cas de validation) écrit et constaté en échec avant le code. | ✅ |
| V. Tests d'intégration ciblés | Contrôleurs (202/400/404/409) et activités (double exécution) contre une vraie base PostgreSQL via Testcontainers. Reprise : scénarios B et C de [quickstart.md](./quickstart.md). | ✅ |
| VI. Secrets hors du dépôt | `.env` ignoré par git, lu par Docker Compose et par Spring (`spring.config.import`) ; `.env.example` commité sans valeurs (R-12). | ✅ |
| VII. Simplicité | Pas d'authentification, pas de librairie d'interface ni de routeur, pas de Flyway. Le hello world est supprimé après validation. Une seule réserve, notée ci-dessous : le passage par Pub/Sub. | ✅ (voir Complexity Tracking) |

**Re-check après la Phase 1** : le modèle de données, les contrats et le quickstart ne contredisent
aucun principe. Toutes les écritures listées dans [contracts/workflow.md](./contracts/workflow.md)
passent par une activité. Aucune règle n'est dupliquée hors de `InterventionRules`, et le front
affiche la vue calculée par le serveur. Aucun `NEEDS CLARIFICATION` ne reste.

## Project Structure

### Documentation (this feature)

```text
specs/001-intervention-consultation/
├── plan.md              # Ce fichier
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1 : lancement + scénarios de validation manuels
├── contracts/
│   ├── api-rest.md      # Endpoints REST et flux SSE
│   └── workflow.md      # Workflow, événements externes, activités, Pub/Sub
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks)
```

### Source Code (repository root)

```text
.env.example                     # POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD (sans valeurs)
.gitignore                       # .env, target/, node_modules/, dist/
docker-compose.yml               # PostgreSQL 16

backend/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/fieldflow/
    │   │   ├── FieldFlowApplication.java
    │   │   ├── config/DaprWorkflowsConfig.java    # @EnableDaprWorkflows, @Profile("!test")
    │   │   ├── regles/                            # Java pur (principe III)
    │   │   │   ├── InterventionRules.java
    │   │   │   ├── EtatIntervention.java, Issue.java, TypeReponse.java, Etape.java, VueTechnicien.java
    │   │   │   └── Decision.java, ResultatCloture.java, LigneHistorique.java (clé + description, R9)
    │   │   ├── workflow/
    │   │   │   ├── InterventionWorkflow.java
    │   │   │   ├── EntreeWorkflow.java, ReponseRecue.java, SelectionRecue.java, EtapeRecue.java, FinRecue.java
    │   │   │   └── activites/                     # 6 activités + Notificateur (publication Pub/Sub, erreur journalisée)
    │   │   ├── persistance/                       # entités JPA + repositories (requêtes ON CONFLICT)
    │   │   ├── api/
    │   │   │   ├── InterventionController.java, TechnicienController.java
    │   │   │   ├── EvenementsController.java      # SSE + abonné @Topic
    │   │   │   ├── dto/
    │   │   │   └── GestionErreurs.java            # @RestControllerAdvice : 400/404/409
    │   │   └── hello/                             # étape 0, supprimé après validation
    │   └── resources/
    │       ├── application.yml                    # fieldflow.consultation.delai=60s
    │       ├── schema.sql
    │       └── data.sql
    └── test/java/com/fieldflow/
        ├── regles/InterventionRulesTest.java      # unitaires, TDD
        ├── api/*IT.java                           # intégration, Testcontainers
        └── workflow/activites/*IT.java            # idempotence, Testcontainers

frontend/
├── package.json, vite.config.ts                   # proxy /api → 8080
└── src/
    ├── main.tsx, App.tsx                          # choix de page selon location.pathname
    ├── api.ts                                     # appels REST typés
    ├── useEvenements.ts                           # EventSource + repli toutes les 2 s
    └── pages/
        ├── Chef.tsx                               # création / propositions / état / historique + Finir
        └── Technicien.tsx                         # choix du nom (?nom=) / vide / consultation / attente / étapes
```

**Structure Decision**: application web en deux projets, `backend/` (Spring Boot + Dapr) et
`frontend/` (Vite), avec l'infrastructure locale à la racine. Le nom du technicien est porté par
l'URL (`/technicien?nom=Jean`), ce qui conserve son écran au rechargement sans connexion ni stockage.

### Ordre de réalisation

0. **Hello world Dapr** (imposé) : `HelloWorkflow` avec une activité et un timer de 30 s, coupé puis
   relancé pendant le timer ([quickstart](./quickstart.md), étape 0). On valide aussi l'abonnement
   `@Topic`. On ne poursuit qu'une fois ce test réussi.
1. Socle : `.env.example`, `.gitignore`, `docker-compose.yml`, `schema.sql`, `data.sql`.
2. `InterventionRules` en TDD (R1 à R10, validations, vue technicien).
3. Persistance et activités, avec leurs tests d'idempotence.
4. `InterventionWorkflow`.
5. API REST, avec ses tests d'intégration.
6. Notifications (Pub/Sub → SSE).
7. Front-end : chef, puis technicien.
8. Scénarios manuels A à D ; suppression du paquet `hello`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Notification via Dapr Pub/Sub, en plus du SSE | Choix technique de l'utilisateur. Il découple les activités des connexions navigateur, et la livraison « au moins une fois » est absorbée par le rechargement de la vue. | Alternative plus simple notée : appeler directement le service SSE depuis l'activité (même processus). Elle n'est pas retenue pour respecter le choix de l'utilisateur ; on peut la reprendre sans toucher à la spec si l'abonnement pose problème au hello world. |
| Deux stockages (Redis de Dapr + PostgreSQL) | Dapr Workflow exige un state store pour son historique. PostgreSQL porte les données affichées et testées. | Utiliser PostgreSQL comme state store Dapr : configuration supplémentaire et mélange des deux rôles, sans gain pour la spec. |
