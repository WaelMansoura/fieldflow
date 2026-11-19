import { useCallback, useState } from 'react'
import { api, ErreurApi, type InterventionCourante } from '../api'
import { euros, heure, libelleIssue } from '../libelles'
import { useCompteARebours } from '../useCompteARebours'
import { useEvenements } from '../useEvenements'

export default function Chef() {
  const [intervention, setIntervention] = useState<InterventionCourante | null>(null)
  const [charge, setCharge] = useState(false)
  const [erreur, setErreur] = useState<string | null>(null)

  const recharger = useCallback(() => {
    api
      .interventionCourante()
      .then((i) => {
        setIntervention(i)
        setCharge(true)
      })
      .catch(() => {
        // serveur injoignable : on garde l'écran actuel, le repli réessaie toutes les 2 s
      })
  }, [])
  const deconnecte = useEvenements(recharger)

  const agir = (action: () => Promise<unknown>) => {
    setErreur(null)
    action()
      .then(recharger)
      .catch((e) => setErreur(e instanceof ErreurApi ? e.erreur.message : 'Erreur inattendue.'))
  }

  return (
    <main>
      <h1>Chef d'équipe</h1>
      {deconnecte && <p className="bandeau">Connexion perdue, nouvelle tentative…</p>}
      {erreur && <p className="erreur">{erreur}</p>}
      {!charge ? null : intervention === null ? (
        <Creation onCreer={(titre, adresse) => agir(() => api.creer(titre, adresse))} />
      ) : intervention.finissable ? (
        <Bilan intervention={intervention} onFinir={() => agir(() => api.finir(intervention.id))} />
      ) : intervention.etat === 'EN_CONSULTATION' || intervention.etat === 'EN_ATTENTE_SELECTION' ? (
        <Propositions
          intervention={intervention}
          onSelectionner={(t) => agir(() => api.selectionner(intervention.id, t))}
        />
      ) : (
        <p className="etat">État de l'intervention : {intervention.etatLibelle}</p>
      )}
    </main>
  )
}

function Creation({ onCreer }: { onCreer: (titre: string, adresse: string) => void }) {
  const [titre, setTitre] = useState('')
  const [adresse, setAdresse] = useState('')
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        onCreer(titre.trim(), adresse.trim())
      }}
    >
      <h2>Nouvelle intervention</h2>
      <label>
        Titre
        <input value={titre} maxLength={200} required onChange={(e) => setTitre(e.target.value)} />
      </label>
      <label>
        Adresse
        <input value={adresse} maxLength={200} required onChange={(e) => setAdresse(e.target.value)} />
      </label>
      <button type="submit">Créer</button>
    </form>
  )
}

function Propositions({
  intervention,
  onSelectionner,
}: {
  intervention: InterventionCourante
  onSelectionner: (technicien: string) => void
}) {
  const restant = useCompteARebours(intervention.secondesRestantes)
  return (
    <section>
      <div className="carte">
        <strong>{intervention.titre}</strong> — {intervention.adresse}
        <p className="etat">État de l'intervention : {intervention.etatLibelle}</p>
        {intervention.etat === 'EN_CONSULTATION' && <p>Temps restant : {restant} s</p>}
      </div>
      <table>
        <thead>
          <tr>
            <th>Technicien</th>
            <th>Réponse</th>
            <th>Frais</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {intervention.propositions.map((p) => (
            <tr key={p.technicien}>
              <td>{p.technicien}</td>
              <td>{libelleIssue[p.issue]}</td>
              <td>{p.montantFrais !== null ? `${euros(p.montantFrais)} (${p.motifFrais})` : ''}</td>
              <td>
                <button disabled={!p.selectionnable} onClick={() => onSelectionner(p.technicien)}>
                  Sélectionner
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  )
}

function Bilan({ intervention, onFinir }: { intervention: InterventionCourante; onFinir: () => void }) {
  return (
    <section className="carte">
      <p className="etat">État de l'intervention : {intervention.etatLibelle}</p>
      <ul>
        {intervention.historique.map((e, i) => (
          <li key={i}>
            {heure(e.survenuA)} — {e.description}
          </li>
        ))}
      </ul>
      <button onClick={onFinir}>Finir</button>
    </section>
  )
}
