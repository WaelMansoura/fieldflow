import { useEffect, useRef, useState } from 'react'

/** Sans aucun message (événement ou battement « ping » toutes les 5 s) pendant ce délai, la connexion est morte. */
const SILENCE_MAX_MS = 8000
const REPLI_MS = 2000

/**
 * Recharge la vue à l'ouverture du flux SSE et à chaque `event: intervention`.
 * Si le flux est en erreur ou silencieux (serveur arrêté), recharge toutes les 2 s et tente de se
 * reconnecter ; à la reconnexion, recharge la vue et arrête le repli.
 * Renvoie `true` tant que la connexion est perdue.
 */
export function useEvenements(recharger: () => void): boolean {
  const rechargerRef = useRef(recharger)
  rechargerRef.current = recharger
  const [deconnecte, setDeconnecte] = useState(false)

  useEffect(() => {
    let source: EventSource | null = null
    let repli: number | undefined
    let chienDeGarde: number | undefined
    let relance: number | undefined
    let actif = true

    const arreterRepli = () => {
      window.clearInterval(repli)
      repli = undefined
    }

    const perdue = () => {
      window.clearTimeout(chienDeGarde)
      source?.close()
      setDeconnecte(true)
      if (repli === undefined) {
        repli = window.setInterval(() => rechargerRef.current(), REPLI_MS)
      }
      window.clearTimeout(relance)
      relance = window.setTimeout(connecter, REPLI_MS)
    }

    const vivante = () => {
      window.clearTimeout(chienDeGarde)
      chienDeGarde = window.setTimeout(perdue, SILENCE_MAX_MS)
    }

    function connecter() {
      if (!actif) return
      source = new EventSource('/api/evenements')
      source.onopen = () => {
        arreterRepli()
        setDeconnecte(false)
        vivante()
        rechargerRef.current()
      }
      source.addEventListener('intervention', () => {
        vivante()
        rechargerRef.current()
      })
      source.addEventListener('ping', vivante)
      source.onerror = perdue
      vivante()
    }

    rechargerRef.current()
    connecter()
    return () => {
      actif = false
      arreterRepli()
      window.clearTimeout(chienDeGarde)
      window.clearTimeout(relance)
      source?.close()
    }
  }, [])

  return deconnecte
}
