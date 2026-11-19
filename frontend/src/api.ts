// Appels REST typés (contracts/api-rest.md).

export type Etat =
  | 'EN_CONSULTATION'
  | 'EN_ATTENTE_SELECTION'
  | 'SELECTIONNEE'
  | 'EN_ROUTE'
  | 'SUR_SITE'
  | 'TERMINEE'
  | 'REFUSEE'

export type Issue = 'EN_ATTENTE' | 'ACCEPTEE' | 'ACCEPTEE_AVEC_FRAIS' | 'REFUSEE' | 'SANS_REPONSE'
export type TypeReponse = 'ACCEPTER' | 'ACCEPTER_AVEC_FRAIS' | 'REFUSER'
export type Etape = 'EN_ROUTE' | 'SUR_SITE' | 'TERMINEE'
export type Vue = 'VIDE' | 'CONSULTATION' | 'ATTENTE' | 'ETAPES'

export interface PropositionDto {
  technicien: string
  issue: Issue
  montantFrais: number | null
  motifFrais: string | null
  selectionnable: boolean
}

export interface EvenementDto {
  survenuA: string
  description: string
}

export interface InterventionCourante {
  id: string
  titre: string
  adresse: string
  etat: Etat
  etatLibelle: string
  secondesRestantes: number
  propositions: PropositionDto[]
  historique: EvenementDto[]
  finissable: boolean
}

export interface VueTechnicienDto {
  vue: Vue
  interventionId: string | null
  titre: string | null
  adresse: string | null
  secondesRestantes: number | null
  maReponse: Issue | null
  etat: Etat | null
  etapeSuivante: Etape | null
}

export interface Erreur {
  code: string
  message: string
}

/** Erreur renvoyée par l'API (400, 404, 409) ou réseau. */
export class ErreurApi extends Error {
  constructor(public erreur: Erreur) {
    super(erreur.message)
  }
}

async function appeler<T>(url: string, init?: RequestInit): Promise<T | null> {
  let reponse: Response
  try {
    reponse = await fetch(url, {
      ...init,
      headers: init?.body ? { 'Content-Type': 'application/json' } : undefined,
    })
  } catch {
    throw new ErreurApi({ code: 'RESEAU', message: 'Serveur injoignable.' })
  }
  if (!reponse.ok) {
    let erreur: Erreur = { code: String(reponse.status), message: `Erreur ${reponse.status}` }
    try {
      erreur = await reponse.json()
    } catch {
      // corps vide ou non JSON : on garde le message par défaut
    }
    throw new ErreurApi(erreur)
  }
  if (reponse.status === 202 || reponse.status === 204) return null
  return (await reponse.json()) as T
}

function poster(url: string, corps?: unknown) {
  return appeler<unknown>(url, { method: 'POST', body: corps === undefined ? undefined : JSON.stringify(corps) })
}

export const api = {
  techniciens: () => appeler<string[]>('/api/techniciens').then((t) => t ?? []),
  interventionCourante: () => appeler<InterventionCourante>('/api/interventions/courante'),
  vueTechnicien: (nom: string) =>
    appeler<VueTechnicienDto>(`/api/techniciens/${encodeURIComponent(nom)}/vue`),
  creer: (titre: string, adresse: string) => poster('/api/interventions', { titre, adresse }),
  repondre: (id: string, technicien: string, type: TypeReponse, montant?: number, motif?: string) =>
    poster(`/api/interventions/${id}/reponses`, { technicien, type, montant, motif }),
  selectionner: (id: string, technicien: string) =>
    poster(`/api/interventions/${id}/selection`, { technicien }),
  etape: (id: string, technicien: string, etape: Etape, rapport?: string) =>
    poster(`/api/interventions/${id}/etapes`, { technicien, etape, rapport }),
  finir: (id: string) => poster(`/api/interventions/${id}/finir`),
}
