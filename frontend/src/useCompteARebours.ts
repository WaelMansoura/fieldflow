import { useEffect, useState } from 'react'

/** Décompte local à partir des secondes restantes envoyées par le serveur (recalé à chaque rechargement). */
export function useCompteARebours(secondesRestantes: number | null | undefined): number {
  const [restant, setRestant] = useState(secondesRestantes ?? 0)

  useEffect(() => {
    const fin = Date.now() + (secondesRestantes ?? 0) * 1000
    setRestant(secondesRestantes ?? 0)
    const minuterie = window.setInterval(() => {
      setRestant(Math.max(0, Math.ceil((fin - Date.now()) / 1000)))
    }, 250)
    return () => window.clearInterval(minuterie)
  }, [secondesRestantes])

  return restant
}
