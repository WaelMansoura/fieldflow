# Feature Specification: Consultation des techniciens et suivi d'une intervention

**Feature Branch**: `001-intervention-consultation`

**Created**: 2026-11-19

**Status**: Draft

**Input**: User description: "FieldFlow — gestion d'une intervention de dépannage par consultation des
techniciens : le chef crée l'intervention, les trois techniciens répondent en 60 secondes (accepter,
accepter avec frais, refuser), le chef sélectionne une proposition, le technicien choisi fait avancer
l'intervention (En route → Sur site → Terminée avec rapport), et l'historique horodaté est affiché.
Le parcours survit à un arrêt de l'application à tout moment."

## Contexte

Une entreprise de dépannage en chauffage reçoit des pannes. Aujourd'hui, le chef d'équipe appelle ses
techniciens un par un pour savoir qui est disponible et quels frais supplémentaires l'intervention
entraînera (déplacement, matériel), puis il les rappelle pour annoncer qui part. C'est lent et rien
n'est tracé. FieldFlow automatise ce parcours : consultation des techniciens, choix par le chef, puis
suivi de l'intervention jusqu'au rapport, avec un historique horodaté.

**Utilisateurs**

- **Chef d'équipe** : crée l'intervention, choisit un technicien parmi les propositions, suit l'état
  de l'intervention, consulte l'historique.
- **Technicien** (Jean, Jacques, Pierre) : répond à la consultation puis, s'il est choisi, fait
  avancer l'intervention jusqu'au rapport.

Pas de connexion : chaque technicien choisit son nom dans une liste. Une seule intervention est
traitée à la fois.

**États d'une intervention** : En consultation → En attente de sélection → Sélectionnée → En route →
Sur site → Terminée. Si aucun technicien n'accepte : Refusée.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Consulter les techniciens et sélectionner une proposition (Priority: P1)

Le chef saisit un titre et une adresse, puis clique sur « Créer ». Jean, Jacques et Pierre reçoivent
l'intervention en même temps, avec un compte à rebours de 60 secondes et trois boutons : Accepter,
Accepter avec frais (montant et motif), Refuser. Le chef voit les réponses arriver au fur et à
mesure, avec le temps restant. Dès que la consultation est close (tous ont répondu ou le délai est
écoulé), chaque proposition acceptée affiche un bouton « Sélectionner » actif. Le chef en choisit une ;
son écran n'affiche plus que « État de l'intervention : Sélectionnée ». Tous les techniciens non
sélectionnés reviennent sur l'écran vide.

**Why this priority**: c'est le cœur du problème : remplacer les appels un par un par une consultation
simultanée et un choix tracé. Sans cette étape, rien d'autre n'a de sens.

**Independent Test**: créer une intervention, faire répondre les trois techniciens depuis leurs
écrans, sélectionner une proposition et vérifier les écrans du chef et des techniciens.

**Acceptance Scenarios**:

1. **Given** l'écran de création, **When** le chef saisit un titre et une adresse et clique sur
   « Créer », **Then** l'intervention passe à « En consultation » et les trois techniciens la voient
   en même temps avec un compte à rebours de 60 secondes.
2. **Given** une intervention en consultation, **When** Jean clique sur « Accepter », **Then** le chef
   voit en moins de 2 secondes la réponse « Accepté » de Jean, sans frais.
3. **Given** une intervention en consultation, **When** Jacques choisit « Accepter avec frais » avec
   40 € et le motif « Déplacement », **Then** le chef voit la réponse de Jacques avec 40 € et le motif.
4. **Given** une intervention en consultation, **When** Pierre clique sur « Refuser », **Then** le chef
  voit « Refusé » pour Pierre et Pierre revient immédiatement sur l'écran vide.
5. **Given** les trois techniciens ont répondu avant 60 secondes, **When** la dernière réponse arrive,
   **Then** la consultation est close immédiatement et les boutons « Sélectionner » des propositions
   acceptées deviennent actifs.
6. **Given** une consultation où au moins un technicien n'a pas répondu, **When** les 60 secondes
   s'écoulent, **Then** la consultation est close, le technicien concerné est marqué « sans réponse »
   et l'intervention passe à « En attente de sélection » s'il y a au moins une acceptation.
7. **Given** une intervention en attente de sélection, **When** le chef clique sur « Sélectionner »
   pour Jean, **Then** l'écran du chef affiche uniquement « État de l'intervention : Sélectionnée »,
   Jean voit ses boutons d'étape et Jacques et Pierre reviennent sur l'écran vide.
8. **Given** une consultation en cours, **When** le chef regarde l'écran des propositions, **Then** les
   boutons « Sélectionner » sont visibles mais inactifs tant que la consultation n'est pas close.

---

### User Story 2 - Faire avancer l'intervention jusqu'au rapport (Priority: P1)

Le technicien sélectionné voit trois boutons, un seul actif à la fois : « En route » quand il démarre
son trajet, « Sur site » quand il arrive, « Terminer » avec un champ pour une phrase de rapport quand
il a fini. Le chef voit l'état changer automatiquement. Quand l'intervention est Terminée, l'écran du
chef affiche la liste à puces des étapes (heure et action) et un bouton « Finir » qui ramène à
l'écran de création.

**Why this priority**: sans suivi jusqu'au rapport, l'intervention n'est pas tracée de bout en bout,
ce qui est la deuxième moitié du problème.

**Independent Test**: à partir d'une intervention sélectionnée, cliquer successivement sur les trois
boutons depuis l'écran du technicien et vérifier l'état, l'historique et le bouton « Finir » chez le
chef.

**Acceptance Scenarios**:

1. **Given** une intervention Sélectionnée, **When** le technicien sélectionné ouvre son écran,
   **Then** seul le bouton « En route » est actif.
2. **Given** une intervention Sélectionnée, **When** le technicien clique sur « En route », **Then**
   l'état passe à « En route », le chef voit « État de l'intervention : En route » en moins de
   2 secondes et seul le bouton « Sur site » est actif.
3. **Given** une intervention En route, **When** le technicien clique sur « Sur site », **Then** l'état
   passe à « Sur site » et seul le bouton « Terminer » (avec le champ rapport) est actif.
4. **Given** une intervention Sur site, **When** le technicien clique sur « Terminer » sans rapport,
   **Then** l'action est refusée avec un message indiquant que le rapport est obligatoire, et l'état
   reste « Sur site ».
5. **Given** une intervention Sur site, **When** le technicien saisit un rapport et clique sur
   « Terminer », **Then** l'état passe à « Terminée », le technicien revient sur l'écran vide et le
   chef voit l'état, la liste à puces des étapes avec heure et action (création, réponses, clôture,
   sélection, en route, sur site, terminée avec le rapport) et le bouton « Finir ».
6. **Given** une intervention Terminée, **When** le chef clique sur « Finir », **Then** il revient sur
   l'écran de création, vide.

---

### User Story 3 - Reprendre le parcours après un arrêt de l'application (Priority: P1)

Scénario de démonstration : Jean accepte sans frais, Jacques accepte avec 40 € (motif
« Déplacement »), Pierre ne répond pas. L'application est arrêtée pendant l'attente et les
60 secondes passent pendant l'arrêt. Au redémarrage, la consultation est close correctement, la
proposition de Pierre est marquée « sans réponse », et le chef peut sélectionner Jean. Le parcours
continue jusqu'au rapport.

**Why this priority**: une intervention peut durer longtemps ; perdre une réponse, une échéance ou une
étape à cause d'un redémarrage rendrait l'outil moins fiable que le téléphone.

**Independent Test**: dérouler le scénario de démonstration en arrêtant l'application pendant la
consultation, puis à d'autres moments (après la sélection, après « En route »), et vérifier qu'aucune
donnée n'est perdue ni dupliquée.

**Acceptance Scenarios**:

1. **Given** Jean a accepté, Jacques a accepté avec 40 € et Pierre n'a pas répondu, **When**
   l'application est arrêtée et redémarrée après l'échéance des 60 secondes, **Then** la consultation
   est close dès le redémarrage, Pierre est marqué « sans réponse », les réponses de Jean et Jacques
   sont conservées et l'intervention est « En attente de sélection ».
2. **Given** l'application est arrêtée pendant la consultation et redémarrée avant l'échéance,
   **When** elle redémarre, **Then** le compte à rebours reprend sur le temps restant réel (calculé
   depuis la création) et les techniciens peuvent encore répondre.
3. **Given** une intervention à n'importe quel état, **When** l'application est arrêtée puis
   redémarrée, **Then** l'état, les réponses et l'historique sont identiques à ceux d'avant l'arrêt,
   et le parcours reprend exactement où il en était.
4. **Given** une action (sélection, étape) enregistrée juste avant l'arrêt, **When** l'application
   redémarre, **Then** l'action n'apparaît qu'une seule fois dans l'historique.

---

### User Story 4 - Aucun technicien n'accepte (Priority: P2)

Si personne n'accepte (tous refusent, ne répondent pas, ou un mélange des deux), l'intervention passe
à Refusée. Tous les techniciens reviennent sur l'écran vide. L'écran du chef affiche « État de
l'intervention : Refusée », l'historique et le bouton « Finir ».

**Why this priority**: c'est un cas réel mais secondaire ; le parcours nominal fonctionne sans lui.

**Independent Test**: créer une intervention, faire refuser les trois techniciens (ou les laisser sans
réponse), puis vérifier l'écran du chef et des techniciens.

**Acceptance Scenarios**:

1. **Given** une intervention en consultation, **When** les trois techniciens refusent, **Then** la
   consultation est close immédiatement et l'intervention passe à « Refusée ».
2. **Given** une intervention en consultation, **When** personne n'a accepté à l'échéance des
   60 secondes, **Then** les techniciens sans réponse sont marqués « sans réponse » et l'intervention
   passe à « Refusée ».
3. **Given** une intervention Refusée, **When** le chef regarde son écran, **Then** il voit « État de
   l'intervention : Refusée », la liste à puces des étapes avec heure et action et le bouton
   « Finir » ; les trois techniciens sont sur l'écran vide.

---

### Edge Cases

- **Réponse tardive** : un technicien répond après la clôture (écran resté ouvert, réseau lent) → la
  réponse est refusée, un message lui indique que la consultation est close, et rien ne change.
- **Réponse et clôture simultanées** : une réponse arrive au moment exact de l'échéance → elle est soit
  prise en compte avant la clôture, soit refusée comme tardive ; jamais les deux, et l'intervention
  n'est jamais dans un état incohérent (par exemple « sans réponse » et « accepté » à la fois).
- **Double réponse** : un technicien clique deux fois ou répond depuis deux onglets → seule la première
  réponse compte ; la seconde est refusée.
- **Frais invalides** : montant à 0 €, négatif ou vide, motif vide ou de plus de 200 caractères → la
  réponse est refusée avec un message explicite et le technicien peut corriger avant l'échéance.
- **Double sélection** : le chef clique deux fois sur « Sélectionner », ou sur deux propositions →
  seule la première sélection est retenue ; la seconde est refusée.
- **Sélection d'une proposition non acceptée** : refusée ou « sans réponse » → aucun bouton
  « Sélectionner » actif ; une tentative est refusée.
- **Étape hors ordre ou répétée** : « Sur site » avant « En route », double clic sur « En route »,
  action d'un technicien non sélectionné → refusée, l'étape n'est enregistrée qu'une fois.
- **Création pendant une intervention en cours** : l'écran de création n'est accessible qu'après
  « Finir » ; une tentative de création est refusée tant qu'une intervention n'est pas finie.
- **Rechargement de page** : le chef ou un technicien recharge son écran → il retrouve l'écran
  correspondant à l'état actuel de l'intervention.
- **Même nom choisi sur deux appareils** : en l'absence de connexion, les deux écrans montrent la même
  chose ; seule la première réponse compte.

## Requirements *(mandatory)*

### Functional Requirements

**Création et consultation**

- **FR-001**: Le chef MUST pouvoir créer une intervention avec un titre et une adresse, tous deux
  obligatoires.
- **FR-002** (R1): À la création, le système MUST envoyer l'intervention aux trois techniciens en même
  temps et la placer dans l'état « En consultation ».
- **FR-003** (R2): Chaque technicien MUST disposer de 60 secondes, comptées depuis la création, pour
  répondre. Ce délai MUST être modifiable par configuration sans changer le comportement.
- **FR-004**: Chaque technicien MUST pouvoir répondre une seule fois par « Accepter », « Accepter avec
  frais » ou « Refuser ».
- **FR-005** (R5): Avec « Accepter avec frais », le montant MUST être supérieur à 0 € et le motif MUST
  être renseigné, avec 200 caractères au maximum ; sinon la réponse est refusée avec un message.
- **FR-006**: L'écran du technicien en consultation MUST afficher le titre, l'adresse, le compte à
  rebours et les trois boutons.
- **FR-007**: L'écran des propositions du chef MUST afficher, pour chaque technicien, sa réponse (en
  attente, accepté, accepté avec frais, refusé, sans réponse), le montant et le motif s'il y en a, et
  le temps restant.

**Clôture**

- **FR-008** (R3): La consultation MUST être close dès que les trois techniciens ont répondu, ou à
  l'échéance du délai.
- **FR-009** (R3): À la clôture, chaque technicien sans réponse MUST être marqué « sans réponse ».
- **FR-010** (R3): À la clôture, l'intervention MUST passer à « En attente de sélection » s'il y a au
  moins une acceptation (avec ou sans frais), sinon à « Refusée ».
- **FR-011** (R3): Si l'intervention passe à « Refusée », tous les techniciens MUST revenir sur l'écran
  vide.
- **FR-012** (R4): Après la clôture, toute réponse MUST être refusée sans rien modifier.

**Sélection**

- **FR-013**: Les boutons « Sélectionner » MUST n'être actifs qu'après la clôture et uniquement sur les
  propositions acceptées.
- **FR-014** (R6): Le chef MUST pouvoir sélectionner une seule proposition, une seule fois ; toute
  autre tentative MUST être refusée.
- **FR-015**: Après la sélection, l'écran du chef MUST afficher uniquement « État de l'intervention :
  {état} », mis à jour automatiquement.
- **FR-016** (R7): Après la sélection, seul le technicien sélectionné MUST voir l'intervention ; les
  autres MUST revenir sur l'écran vide.

**Étapes**

- **FR-017** (R8): Le technicien sélectionné MUST voir les boutons « En route », « Sur site » et
  « Terminer », un seul étant actif à la fois, dans cet ordre.
- **FR-018** (R8): Le système MUST refuser toute étape hors ordre, répétée, ou demandée par un autre
  technicien que celui sélectionné.
- **FR-019** (R8): « Terminer » MUST exiger une phrase de rapport non vide.
- **FR-020**: Après « Terminer », le technicien MUST revenir sur l'écran vide.

**Historique et fin**

- **FR-021** (R9): Le système MUST enregistrer chaque événement (création, réponse de chaque
  technicien, marquage « sans réponse », clôture, sélection, chaque étape, rapport) avec son heure et
  une description lisible, une seule fois chacun.
- **FR-022**: Quand l'intervention est Terminée ou Refusée, l'écran du chef MUST afficher l'état, la
  liste à puces des événements dans l'ordre chronologique avec heure et description, et le bouton
  « Finir ».
- **FR-023** (R10): Le bouton « Finir » MUST n'apparaître que pour une intervention Terminée ou
  Refusée, et ramener le chef à l'écran de création.
- **FR-024**: Une nouvelle intervention MUST ne pouvoir être créée que lorsqu'aucune intervention n'est
  en cours (aucune, ou la précédente finie par « Finir »).

**Écrans et accès**

- **FR-025**: Le technicien MUST choisir son nom parmi Jean, Jacques et Pierre, sans mot de passe.
- **FR-026**: Le technicien MUST voir un écran vide lorsqu'il n'a rien à faire.
- **FR-027**: Les écrans du chef et des techniciens MUST afficher chaque changement (nouvelle réponse,
  clôture, sélection, étape) en moins de 2 secondes, sans recharger la page.
- **FR-028**: Un écran rechargé MUST afficher l'état courant de l'intervention.

**Fiabilité**

- **FR-029**: Un arrêt de l'application à n'importe quel moment MUST NOT faire perdre une
  intervention, une réponse, une étape, un événement d'historique ou une échéance ; au redémarrage, le
  parcours MUST reprendre exactement où il en était.
- **FR-030**: Une échéance de consultation dépassée pendant un arrêt MUST être traitée dès le
  redémarrage.
- **FR-031**: Une action MUST NOT être appliquée deux fois, même en cas de reprise après arrêt ou de double clic : pas de double sélection, pas d'étape ni d'événement enregistré deux fois.
- **FR-032**: Une réponse et une clôture simultanées MUST laisser l'intervention dans un état cohérent :
  chaque technicien a exactement une issue (accepté, accepté avec frais, refusé ou sans réponse).

### Key Entities

- **Intervention** : la panne à traiter. Titre, adresse, état (En consultation, En attente de
  sélection, Sélectionnée, En route, Sur site, Terminée, Refusée), heure de création, échéance de la
  consultation, technicien sélectionné, rapport. Une seule active à la fois.
- **Technicien** : Jean, Jacques ou Pierre. Nom uniquement ; données de démonstration fixes.
- **Proposition** : la réponse d'un technicien à une intervention. Technicien, issue (en attente,
  accepté, accepté avec frais, refusé, sans réponse), montant et motif des frais, heure de réponse.
  Exactement une par technicien et par intervention.
- **Événement d'historique** : une étape tracée de l'intervention. Heure, type, description lisible
  (par exemple « 10:42:15 — Jacques a accepté avec 40 € de frais (Déplacement) »).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Le chef obtient les réponses des trois techniciens et sélectionne l'un d'eux,
  au lieu d'une série d'appels téléphoniques.
- **SC-002**: Chaque changement (réponse, clôture, sélection, étape) apparaît sur les écrans concernés
  en moins de 2 secondes, sans rechargement, dans 100 % des essais de la démonstration.
- **SC-003**: Le scénario de démonstration avec arrêt pendant la consultation se termine correctement
  dans 100 % des essais : Pierre marqué « sans réponse », réponses de Jean et Jacques conservées,
  sélection de Jean possible, parcours mené jusqu'au rapport.
- **SC-004**: Après un arrêt à n'importe quelle étape, 0 réponse, 0 étape et 0 événement perdu, et
  0 événement en double dans l'historique.
- **SC-005**: 100 % des réponses tardives, doubles sélections et étapes hors ordre sont refusées sans
  modifier l'intervention.
- **SC-006**: Une intervention terminée affiche un historique complet : chaque événement de R9 est
  présent une fois, dans l'ordre, avec son heure, lisible sans explication.

## Assumptions

- Les heures de l'historique sont affichées à l'heure locale au format HH:MM:SS ; l'intervention dure
  au plus une journée.
- Le délai de 60 secondes est compté depuis la création de l'intervention, de façon identique pour les
  trois techniciens ; un arrêt de l'application ne le suspend pas.
- Les frais sont exprimés en euros, avec au plus deux décimales.
- Un technicien qui refuse revient immédiatement sur l'écran vide. Un technicien qui a accepté (avec ou sans frais) voit un écran d'attente indiquant sa réponse jusqu'à la sélection ou au passage à Refusée.
- L'état « Sélectionnée » est affiché au chef entre la sélection et le clic sur « En route ».
- Après « Finir », l'intervention reste enregistrée avec son historique, mais n'est plus affichée.
- Le chef et les techniciens utilisent des navigateurs sur le même réseau que l'application.
- Hors périmètre : plusieurs interventions en parallèle, interface client, connexion et comptes,
  métiers et zones, priorité, affectation manuelle, notifications (SMS, e-mail ou liste de
  notifications), commande de pièces, nouvelle visite, signature, facture, carte, configuration des
  étapes par l'utilisateur.
