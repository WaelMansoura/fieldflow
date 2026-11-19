<!--
Sync Impact Report
- Version change: (template non rempli) → 1.0.0
- Principes modifiés : aucun (première adoption)
- Principes ajoutés : I. Fiabilité du workflow ; II. Déterminisme Dapr ; III. Règles métier isolées ;
  IV. Tests unitaires d'abord ; V. Tests d'intégration ciblés ; VI. Secrets hors du dépôt ;
  VII. Simplicité
- Sections ajoutées : Contraintes techniques ; Workflow de développement
- Sections supprimées : aucune
- Modèles à relire : .specify/templates/plan-template.md (Constitution Check),
  spec-template.md, tasks-template.md (les tâches de test sont obligatoires, pas optionnelles)
- TODO différés : aucun
-->
# FieldFlow Constitution

## Core Principles

### I. Fiabilité du workflow
Aucun état de workflow n'est gardé uniquement en mémoire. Un arrêt de l'application à
n'importe quel moment MUST NOT faire perdre une intervention, une réponse ou une échéance :
tout état nécessaire à la reprise est persisté.

**Pourquoi** : un workflow peut attendre des minutes ou des jours ; un redémarrage ne doit pas
casser ce qui est en cours.

### II. Déterminisme Dapr
Le code d'un workflow Dapr MUST être déterministe : aucun accès à la base, aucun appel réseau,
aucun `Instant.now()`, aucun hasard. Tout ce qui touche au monde extérieur se fait dans une
activité. Toute activité MUST être idempotente : l'exécuter deux fois donne le même résultat
qu'une seule fois.

**Pourquoi** : Dapr rejoue le workflow au redémarrage et peut relancer une activité après un
crash ; sans cette règle, on obtient des doublons ou des choix différents.

### III. Règles métier isolées
Toutes les règles métier (R1, R2…) vivent dans une seule classe Java pure, sans dépendance à
Dapr, Spring ou la base de données. Le workflow, les activités et l'API ne font que l'appeler ;
ils MUST NOT dupliquer ni réimplémenter une règle.

**Pourquoi** : une règle à un seul endroit se teste facilement et se modifie sans risque
(principe de responsabilité unique de SOLID).

### IV. Tests unitaires d'abord (NON-NEGOTIABLE)
Chaque règle métier MUST avoir un test unitaire, écrit avant son code (TDD) : le test est écrit,
constaté en échec, puis le code est écrit pour le faire passer.

**Pourquoi** : le test décrit le comportement attendu avant d'écrire le code, et détecte
immédiatement si une modification future casse une règle.

### V. Tests d'intégration ciblés
L'API et l'accès à la base MUST être couverts par des tests d'intégration sur une vraie base
PostgreSQL (pas de base simulée ou en mémoire). La reprise du workflow après redémarrage est
vérifiée par un scénario manuel documenté.

**Pourquoi** : les tests unitaires vérifient les règles une par une ; les tests d'intégration
vérifient que les pièces fonctionnent ensemble (requêtes SQL, transactions, codes HTTP).

### VI. Secrets hors du dépôt
Les mots de passe, clés d'API et données sensibles MUST NOT figurer dans le code. Les secrets sont
dans un fichier `.env` exclu par `.gitignore` ; un fichier `.env.example` commité liste les
variables à remplir, sans leurs valeurs.

**Pourquoi** : un dépôt peut devenir public ; un secret commité doit être considéré comme
compromis.

### VII. Simplicité
Rien n'est ajouté s'il n'est pas dans la spécification : pas d'authentification, pas de
librairie d'interface, pas de fonctionnalité « au cas où ». Tout changement de besoin modifie
d'abord la spécification, puis le plan et les tâches, puis le code.

**Pourquoi** : le projet doit tenir en une journée et rester entièrement explicable.

## Contraintes techniques

- Langage et pile : Java 21, Spring Boot 3.5, Dapr Workflow avec io.dapr.spring:dapr-spring-boot-starter 1.18.x, PostgreSQL 16 (Docker Compose), React + TypeScript (Vite).
- Dapr tourne en local avec `dapr init` ; l'historique des workflows est conservé dans le state store de Dapr, les données métier affichées dans PostgreSQL.
- Les règles métier résident dans une classe Java pure ; les couches Dapr, Spring et base de
  données en dépendent, jamais l'inverse.
- La configuration sensible passe par des variables d'environnement documentées dans
  `.env.example`.

## Workflow de développement

- L'ordre de travail est : spécification → plan → tâches → code. Un changement de besoin
  recommence à la spécification.
- Pour chaque règle métier : test unitaire en échec, puis implémentation, puis refactorisation.
- Les tâches de test (unitaires et d'intégration) sont obligatoires dans `tasks.md`, jamais
  optionnelles.
- Le scénario manuel de reprise après redémarrage est documenté et rejoué avant de considérer le
  workflow terminé.

## Governance

Cette constitution prévaut sur toute autre pratique du projet. Tout changement de principe est d'abord écrit ici, puis répercuté dans la spécification, le plan et les tâches.

**Version**: 1.0.0 | **Ratified**: 2026-11-19 | **Last Amended**: 2026-11-19
