# Research: Consultation des techniciens et suivi d'une intervention

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md) | **Date**: 2026-11-19

Les choix techniques de départ sont ceux donnés par l'utilisateur (voir plan.md). Ce document fixe
les points qu'ils laissent ouverts et note ce qui doit être confirmé par le hello world Dapr.

## R-01 — Délai de consultation durable

- **Decision**: À la première exécution, le workflow calcule
  `echeance = InterventionRules.echeance(ctx.getCurrentInstant(), delai)` (le délai arrive dans
  l'entrée du workflow ; le calcul est une règle R2, testée unitairement). Il crée
  **un seul** timer durable `ctx.createTimer(echeance)`, puis boucle sur
  `ctx.anyOf(timer, ctx.waitForExternalEvent("reponse", ReponseRecue.class)).await()`. Si le timer
  gagne, la consultation est close. Si une réponse gagne, elle est appliquée et la boucle reprend avec
  le même timer. La boucle s'arrête aussi dès que les trois techniciens ont répondu.
- **Rationale**: `getCurrentInstant()` renvoie la même valeur à chaque rejeu, donc l'échéance ne
  change pas après un redémarrage (principe II). Le timer est durable : s'il expire pendant un arrêt,
  il se déclenche dès que le sidecar redémarre (FR-030). Un seul timer garde l'échéance identique
  pour les trois techniciens, comme le dit la spec.
- **Alternatives considered**: `waitForExternalEvent(nom, timeoutRestant)` répété à chaque tour.
  Cela crée un timer par réponse et oblige à recalculer le temps restant ; rejeté pour sa complexité.
  `Thread.sleep` ou un planificateur Spring : rejetés, ils ne sont pas durables (principe I).

## R-02 — Valeur du délai (R2 « modifiable »)

- **Decision**: La propriété `fieldflow.consultation.delai` (défaut `60s`, format `Duration` de
  Spring) est lue par le contrôleur de création et passée dans l'entrée du workflow.
- **Rationale**: Le workflow ne lit aucune configuration (principe II). Une intervention garde le
  délai qu'elle avait à sa création, même si la configuration change pendant qu'elle s'exécute.
- **Alternatives considered**: Lire la propriété dans le workflow : non déterministe en cas de
  changement entre deux rejeux.

## R-03 — Événements externes et doublons

- **Decision**: Quatre noms d'événements : `reponse`, `selection`, `etape`, `finir`. Le workflow
  garde en variables locales (reconstruites au rejeu) l'issue de chaque technicien, l'état et le
  technicien sélectionné. Il applique `InterventionRules` à chaque événement reçu et **ignore** tout
  événement refusé par les règles : seconde réponse, sélection d'une proposition non acceptée,
  seconde sélection, étape hors ordre.
- **Rationale**: Le contrôleur vérifie déjà l'état en base et répond 409. Mais l'écriture en base est
  asynchrone : deux clics rapprochés peuvent tous deux passer le contrôle. Le workflow est la seule
  autorité, et il traite ses événements un par un (FR-031, FR-032). Les règles étant pures, les
  appeler dans le workflow reste déterministe.
- **Alternatives considered**: Faire confiance au seul contrôle du contrôleur : il laisse passer les
  doubles clics. Verrou en base dans le contrôleur : il contredit « seules les activités écrivent ».

## R-04 — Réponses tardives et course réponse/clôture

- **Decision**: Après la clôture, le workflow n'attend plus d'événement `reponse`. Une réponse
  tardive que le contrôleur aurait laissée passer reste sans effet : Dapr la met en file, mais elle
  n'est jamais lue. Le contrôleur refuse avec 409 toute réponse quand l'état en base n'est plus
  « En consultation » **ou** quand l'échéance enregistrée est dépassée (le contrôleur peut lire
  l'horloge, il n'est pas un workflow).
- **Rationale**: Dans le workflow, une réponse est soit traitée avant que le timer gagne, soit
  jamais : il n'existe aucun état intermédiaire (FR-032). Le 202 signifie « reçu » : si la réponse
  arrive après la clôture, l'écran du technicien montre la consultation close au rechargement suivant
  (moins de 2 s).
- **Alternatives considered**: Réponse synchrone attendant le traitement par le workflow : elle
  ajoute une attente et un délai d'expiration côté API sans rien apporter à la spec.

## R-05 — Idempotence des activités

- **Decision**: Une activité par transition. Chacune écrit dans **une** transaction l'état, la
  proposition et l'événement d'historique, puis publie la notification. Clés d'idempotence :
  - intervention : clé primaire = identifiant du workflow ; `INSERT … ON CONFLICT DO NOTHING` ;
  - proposition : unique `(intervention_id, technicien)` ; mise à jour avec
    `WHERE issue = 'EN_ATTENTE'` ;
  - historique : unique `(intervention_id, cle)`, avec une clé déterministe (`CREATION`,
    `REPONSE:Jean`, `SANS_REPONSE:Pierre`, `CLOTURE`, `SELECTION`, `EN_ROUTE`, `SUR_SITE`,
    `TERMINEE`, `FIN`) ; `INSERT … ON CONFLICT DO NOTHING` ;
  - état : mise à jour conditionnelle (`WHERE etat = <état attendu>`).
- **Politique de reprise** : délai croissant de 1 s à 30 s, 20 tentatives au maximum. Une
  indisponibilité passagère de PostgreSQL est absorbée sans faire échouer le workflow
  (FR-029).
- **Rationale**: Une activité relancée après un crash produit exactement les mêmes lignes
  (principe II, FR-031).
- **Alternatives considered**: Un champ « déjà traité » lu avant d'écrire : la lecture puis
  l'écriture ne sont pas atomiques. Rejeté au profit des contraintes uniques.

## R-06 — Horodatage de l'historique

- **Decision**: Le contrôleur ajoute `recuA = Instant.now()` à chaque événement envoyé au workflow.
  Ce sont les heures de réponse, de sélection et d'étape. La création et la clôture par délai utilisent
  respectivement `ctx.getCurrentInstant()` et l'échéance. La clôture parce que tous ont répondu
  utilise l'heure de la dernière réponse. Le workflow passe ces heures aux activités.
- **Rationale**: Les heures sont celles des faits réels, pas celles du traitement. Après un
  redémarrage, la clôture par délai affiche l'échéance réelle, pas l'heure du redémarrage. Une
  activité relancée réécrit la même heure (idempotence).
- **Alternatives considered**: `Instant.now()` dans l'activité : l'heure change si l'activité est
  relancée, et une clôture rattrapée afficherait l'heure du redémarrage.

## R-07 — Une seule intervention active

- **Decision**: Index unique partiel `CREATE UNIQUE INDEX … ON intervention ((true)) WHERE
  archivee_a IS NULL`. Le contrôleur répond 409 s'il trouve une intervention non archivée. Si deux
  créations passent ce contrôle en même temps, l'activité de création de la seconde ne crée rien
  (conflit). Elle renvoie `false` et ce workflow se termine aussitôt, sans effet.
- **Rationale**: L'invariant est garanti par la base, sans écriture dans le contrôleur (FR-024).
- **Alternatives considered**: Identifiant de workflow fixe (« intervention-courante ») : il empêche
  de garder les interventions passées et complique « Finir ».

## R-08 — « Finir » comme événement du workflow

- **Decision**: Après Terminée ou Refusée, le workflow attend l'événement `finir`. L'activité
  `ArchiverIntervention` renseigne `archivee_a`, puis le workflow se termine.
- **Rationale**: Toute écriture passe par une activité (choix de l'utilisateur). Le workflow reste
  vivant tant que l'intervention est affichée, ce qui garde un seul chemin d'écriture.
- **Alternatives considered**: Workflow terminé à Terminée et contrôleur qui écrit `archivee_a` :
  cela fait une exception à la règle « seules les activités écrivent ».

## R-09 — Notification des écrans

- **Decision**: Après sa transaction, chaque activité publie `{interventionId, type}` sur le
  composant `pubsub`, topic `intervention-events`. Un contrôleur abonné (`@Topic`) transmet le message
  à tous les `SseEmitter` ouverts sur `GET /api/evenements`. À chaque message, le navigateur recharge
  sa vue (`GET`). En repli, si l'`EventSource` est en erreur, le navigateur recharge toutes les
  2 secondes. Une erreur de publication est journalisée et ne fait **pas** échouer l'activité :
  l'écriture en base est déjà faite, et un échec de notification ne doit pas bloquer le workflow.
- **Rationale**: Les messages peuvent arriver deux fois ou dans le désordre. Comme le message ne
  porte pas l'état et que l'écran recharge l'état courant, un doublon est sans effet. Les
  connexions SSE sont en mémoire, mais ce ne sont pas des données de workflow : l'`EventSource` se
  reconnecte tout seul après un redémarrage et le navigateur recharge sa vue (principe I respecté).
- **Alternatives considered**: Rafraîchissement seul toutes les 2 s : simple, mais à la limite de
  FR-027. Il est gardé comme repli. WebSocket : bidirectionnel, inutile ici.
- **À vérifier au hello world**: que `dapr-spring-boot-starter` 1.18 expose bien `/dapr/subscribe`
  pour les méthodes annotées `@Topic`. Sinon, utiliser un fichier d'abonnement déclaratif passé à
  `dapr run --resources-path`.

## R-10 — Vue du technicien (R7) calculée par les règles

- **Decision**: `InterventionRules.vueTechnicien(etat, issue, estSelectionne)` renvoie `VIDE`,
  `CONSULTATION`, `ATTENTE` ou `ETAPES`. L'API l'expose telle quelle et le front ne fait qu'afficher
  l'écran correspondant.
- **Rationale**: « Qui voit quoi » est une règle métier (R3, R7) et doit donc rester à un seul endroit
  (principe III).
- **Alternatives considered**: Logique dans le front : non testée en TDD et dupliquée.

## R-11 — Schéma et données de démonstration

- **Decision**: `schema.sql` et `data.sql` exécutés au démarrage (`spring.sql.init.mode=always`),
  avec `CREATE TABLE IF NOT EXISTS` et `INSERT … ON CONFLICT DO NOTHING` pour les trois techniciens.
  JPA est en `ddl-auto=validate`.
- **Rationale**: C'est suffisant pour un schéma unique et stable, sans nouvelle dépendance
  (principe VII).
- **Alternatives considered**: Flyway ou Liquibase : utiles pour des migrations successives, hors
  besoin ici.

## R-12 — Secrets et configuration

- **Decision**: `.env` (ignoré par git) contient `POSTGRES_DB`, `POSTGRES_USER` et
  `POSTGRES_PASSWORD`. Docker Compose le lit directement ; Spring le lit par
  `spring.config.import=optional:file:../.env[.properties]`. `.env.example` est commité sans les
  valeurs.
- **Rationale**: Un seul fichier de secrets pour les deux outils (principe VI).
- **Alternatives considered**: Variables exportées à la main : source d'erreurs et pas
  documentées.

## R-13 — Tests

- **Decision**:
  - unitaires (JUnit 5) : `InterventionRules`, écrits avant le code (principe IV) ;
  - intégration (`@SpringBootTest` + Testcontainers PostgreSQL 16) :
    - contrôleurs : codes 202/400/404/409, avec `DaprWorkflowClient` remplacé par un mock dont on
      vérifie les appels `raiseEvent` ;
    - activités : appelées directement **deux fois** contre la vraie base, en vérifiant qu'il n'y a
      ni doublon ni changement ;
  - reprise après redémarrage : scénarios manuels dans [quickstart.md](./quickstart.md)
    (principe V).
- **Rationale**: Couvre SQL, transactions et HTTP sur une vraie base, sans faire tourner Dapr dans
  les tests automatiques.
- **Alternatives considered**: Tests automatiques avec un sidecar Dapr (Testcontainers Dapr) :
  plus lourds. La constitution prévoit un scénario manuel pour la reprise.

## R-14 — Hello world Dapr (étape 0 imposée)

- **Decision**: Un `HelloWorkflow` (activité `DireBonjour` → timer de 30 s → activité
  `DireAuRevoir`), démarré par `POST /api/hello`. Test : démarrer, arrêter `dapr run` pendant le
  timer, relancer, puis constater dans les journaux que `DireAuRevoir` s'exécute **une** fois et que
  `DireBonjour` ne se réexécute pas. Le paquet `hello` est supprimé quand le workflow
  d'intervention est validé (principe VII).
- **Rationale**: Valide `dapr init`, le sidecar, le starter, l'enregistrement des workflows et la
  durabilité des timers avant tout le reste. Vérifie aussi R-09 (abonnement `@Topic`).
- **Alternatives considered**: Commencer directement par le workflow métier : un problème
  d'installation serait mêlé aux erreurs métier.

## Points à confirmer pendant le hello world

| Point | Vérification | Repli |
|-------|--------------|-------|
| Version du runtime Dapr compatible avec le SDK 1.18 | `dapr --version`, démarrage sans erreur gRPC | Aligner le runtime sur la matrice de compatibilité du SDK |
| `createTimer(ZonedDateTime)` disponible | Compilation | `createTimer(Duration.between(now, echeance))` |
| Timer échu pendant l'arrêt déclenché au redémarrage | Scénario R-14 | Bloquant : revoir l'installation (service scheduler de `dapr init`) |
| `/dapr/subscribe` exposé pour `@Topic` | `curl localhost:8080/dapr/subscribe` | Abonnement déclaratif YAML |
