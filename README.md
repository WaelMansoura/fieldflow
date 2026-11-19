# FieldFlow

Une entreprise de dépannage en chauffage reçoit des pannes. Aujourd'hui, le chef d'équipe appelle ses
techniciens un par un pour savoir qui est disponible et à quel prix, puis les rappelle : c'est lent et
rien n'est tracé. FieldFlow consulte les trois techniciens en même temps (60 secondes pour répondre),
laisse le chef choisir une proposition, puis suit l'intervention jusqu'au rapport, avec un historique
horodaté, sans rien perdre si l'application s'arrête.

## Architecture

```text
 Navigateur (React)                    Back-end Spring Boot                         Dapr (sidecar)
 /chef  /technicien                                                                 
   │  POST action ──────────────► Contrôleur REST ── InterventionRules (400/409)
   │                                   │ raiseEvent / scheduleNewWorkflow ───────► Workflow engine
   │                                   │                                           (historique dans Redis,
   │                                   │                                            timers durables)
   │                          InterventionWorkflow (déterministe) ◄────────────────────┘
   │                                   │ callActivity
   │                          Activités idempotentes ── écrivent ──► PostgreSQL (intervention,
   │                                   │                                 propositions, historique)
   │                                   └── publient ───────────────► Pub/Sub « intervention-events »
   │  ◄──── SSE /api/evenements ◄── abonné @Topic ◄───────────────────────────────────┘
   └── GET vue courante (rechargée à chaque événement, ou toutes les 2 s si la connexion est perdue)
```

- **`regles/InterventionRules`** : toutes les règles R1 à R10, en Java pur, testées en TDD.
- **`workflow/InterventionWorkflow`** : un workflow par intervention. Il attend les réponses avec un
  timer durable (60 s), clôt la consultation, attend la sélection, les étapes, puis « Finir ».
- **`workflow/activites`** : seules à écrire en base, chacune en une transaction, rejouables sans
  doublon (contraintes uniques, mises à jour conditionnelles).
- **`api`** : lit la base, vérifie les règles, envoie l'événement au workflow, répond 202.

## Pourquoi Dapr Workflow

Une intervention attend des humains : 60 secondes pour les réponses, puis un temps indéfini pour la
sélection et les étapes. Dapr Workflow conserve l'état de chaque intervention et ses échéances hors de
la mémoire de l'application. Après un arrêt, le workflow est rejoué depuis son historique, et un délai
échu pendant l'arrêt se déclenche dès le redémarrage. Le code reste une simple suite d'étapes, sans
table d'état ni planificateur à écrire soi-même.

## Lancer

Prérequis : Java 21, Node.js 20+, Docker Desktop, Dapr CLI.

```powershell
dapr init                                   # une seule fois
Copy-Item .env.example .env                 # puis renseigner les valeurs
docker compose up -d                        # PostgreSQL 16
cd backend
dapr run --app-id fieldflow --app-port 8080 --dapr-grpc-port 50001 -- .\mvnw.cmd spring-boot:run
# autre terminal
cd frontend; npm install; npm run dev       # http://localhost:5173/chef et /technicien
```

Tests : `cd backend; .\mvnw.cmd test` (règles + intégration sur PostgreSQL via Testcontainers, sans Dapr).

## Démarche Spec Kit

Le projet suit spécification → plan → tâches → code, sous une constitution
(`.specify/memory/constitution.md`). Tout est dans
[`specs/001-intervention-consultation/`](specs/001-intervention-consultation/) : `spec.md` (besoin),
`plan.md` et `research.md` (choix techniques), `data-model.md`, `contracts/`, `tasks.md`, et
`quickstart.md`, qui contient les scénarios de validation, dont la démonstration d'arrêt pendant la
consultation (scénario B).
