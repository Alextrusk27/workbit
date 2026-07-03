import { Outlet } from 'react-router-dom'

/** Корневой layout. Шапка и футер появятся в Фазе 1. */
function App() {
  return (
    <div className="flex min-h-screen flex-col">
      <main className="flex-1">
        <Outlet />
      </main>
    </div>
  )
}

export default App
