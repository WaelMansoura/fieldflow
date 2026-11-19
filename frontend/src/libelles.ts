import type { Issue } from './api'

export const libelleIssue: Record<Issue, string> = {
  EN_ATTENTE: 'En attente',
  ACCEPTEE: 'Accepté',
  ACCEPTEE_AVEC_FRAIS: 'Accepté avec frais',
  REFUSEE: 'Refusé',
  SANS_REPONSE: 'Sans réponse',
}

export function euros(montant: number): string {
  return montant.toLocaleString('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' €'
}

export function heure(iso: string): string {
  return new Date(iso).toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
