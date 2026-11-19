import { useCallback, useEffect, useState } from 'react'
import { api, ErreurApi, type Etape, type TypeReponse, type VueTechnicienDto } from '../api'
import { libelleIssue } from '../libelles'
import { useCompteARebours } from '../useCompteARebours'
import { useEvenements } from '../useEvenements'

export default function Technicien() {
  const nom = new URLSearchParams(window.location.search).get('nom')
  return nom ? <EcranTechnicien nom={nom} /> : <ChoixDuNom />
}

function ChoixDuNom() {
  const [noms, setNoms] = useState<string[]>([])
  useEffect(() => {
    api.techniciens().then(setNoms).catch(() => setNoms([]))
  }, [])
  return (
    <main>
      <h1>Qui êtes-vous ?</h1>
      <ul>
        {noms.map((n) => (
          <li key={n}>
            <a href={`/technicien?nom=${encodeURIComponent(n)}`}>{n}</a>
          </li>
        ))}
      </ul>
    </main>
  )
}

function EcranTechnicien({ nom }: { nom: string }) {
  const [vue, setVue] = useState<VueTechnicienDto | null>(null)
  const [erreur, setErreur] = useState<string | null>(null)

  const recharger = useCallback(() => {
    api
      .vueTechnicien(nom)
      .then(setVue)
      .catch(() => {
        // serveur injoignable : le repli réessaie toutes les 2 s
      })
  }, [nom])
  const deconnecte = useEvenements(recharger)

  const agir = (action: () => Promise<unknown>) => {
    setErreur(null)
    action()
      .then(recharger)
      .catch((e) => setErreur(e instanceof ErreurApi ? e.erreur.message : 'Erreur inattendue.'))
  }

  return (
    <main>
      <h1>{nom}</h1>
      {deconnecte && <p className="bandeau">Connexion perdue, nouvelle tentative…</p>}
      {erreur && <p className="erreur">{erreur}</p>}
      {vue?.vue === 'CONSULTATION' && (
        <Consultation
          vue={vue}
          onRepondre={(type, montant, motif) =>
            agir(() => api.repondre(vue.interventionId!, nom, type, montant, motif))
          }
        />
      )}
      {vue?.vue === 'ETAPES' && (
        <Etapes
          vue={vue}
          onAvancer={(etape, rapport) => agir(() => api.etape(vue.interventionId!, nom, etape, rapport))}
        />
      )}
      {vue?.vue === 'ATTENTE' && (
        <div className="carte">
          <strong>{vue.titre}</strong> — {vue.adresse}
          <p>Réponse envoyée : {vue.maReponse ? libelleIssue[vue.maReponse] : ''}. En attente de la décision du chef.</p>
        </div>
      )}
    </main>
  )
}

function Etapes({
  vue,
  onAvancer,
}: {
  vue: VueTechnicienDto
  onAvancer: (etape: Etape, rapport?: string) => void
}) {
  const [rapport, setRapport] = useState('')
  const suivante = vue.etapeSuivante
  return (
    <div className="carte">
      <strong>{vue.titre}</strong> — {vue.adresse}
      <p>Vous êtes sélectionné pour cette intervention.</p>
      <button disabled={suivante !== 'EN_ROUTE'} onClick={() => onAvancer('EN_ROUTE')}>
        En route
      </button>
      <button disabled={suivante !== 'SUR_SITE'} onClick={() => onAvancer('SUR_SITE')}>
        Sur site
      </button>
      <label>
        Rapport
        <textarea
          value={rapport}
          rows={2}
          disabled={suivante !== 'TERMINEE'}
          onChange={(e) => setRapport(e.target.value)}
        />
      </label>
      <button disabled={suivante !== 'TERMINEE'} onClick={() => onAvancer('TERMINEE', rapport.trim())}>
        Terminer
      </button>
    </div>
  )
}

function Consultation({
  vue,
  onRepondre,
}: {
  vue: VueTechnicienDto
  onRepondre: (type: TypeReponse, montant?: number, motif?: string) => void
}) {
  const restant = useCompteARebours(vue.secondesRestantes)
  const [avecFrais, setAvecFrais] = useState(false)
  const [montant, setMontant] = useState('')
  const [motif, setMotif] = useState('')

  return (
    <div className="carte">
      <strong>{vue.titre}</strong> — {vue.adresse}
      <p>Temps restant : {restant} s</p>
      {!avecFrais ? (
        <>
          <button onClick={() => onRepondre('ACCEPTER')}>Accepter</button>
          <button onClick={() => setAvecFrais(true)}>Accepter avec frais</button>
          <button onClick={() => onRepondre('REFUSER')}>Refuser</button>
        </>
      ) : (
        <form
          onSubmit={(e) => {
            e.preventDefault()
            onRepondre('ACCEPTER_AVEC_FRAIS', Number(montant.replace(',', '.')), motif.trim())
          }}
        >
          <label>
            Montant (€)
            <input inputMode="decimal" value={montant} required onChange={(e) => setMontant(e.target.value)} />
          </label>
          <label>
            Motif
            <input value={motif} maxLength={200} required onChange={(e) => setMotif(e.target.value)} />
          </label>
          <button type="submit">Envoyer</button>
          <button type="button" onClick={() => setAvecFrais(false)}>
            Annuler
          </button>
        </form>
      )}
    </div>
  )
}
