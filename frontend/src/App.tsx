import Chef from './pages/Chef'
import Technicien from './pages/Technicien'

// Pas de routeur : la page dépend uniquement du chemin.
export default function App() {
  const chemin = window.location.pathname
  if (chemin.startsWith('/chef')) return <Chef />
  if (chemin.startsWith('/technicien')) return <Technicien />
  return (
    <main>
      <h1>FieldFlow</h1>
      <ul>
        <li><a href="/chef">Écran du chef d'équipe</a></li>
        <li><a href="/technicien">Écran du technicien</a></li>
      </ul>
    </main>
  )
}
