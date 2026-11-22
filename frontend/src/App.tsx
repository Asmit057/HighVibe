import type { ReactNode } from 'react'
import { Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useAuth } from './lib/auth'
import AuthPage from './pages/AuthPage'
import BillingPage from './pages/BillingPage'
import HomePage from './pages/HomePage'
import LandingPage from './pages/LandingPage'
import WorkspacePage from './pages/WorkspacePage'

function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return <>{children}</>
}

export default function App() {
  const { user } = useAuth()
  return (
    <Routes>
      <Route path="/" element={user ? <HomePage /> : <LandingPage />} />
      <Route path="/login" element={<AuthPage mode="login" />} />
      <Route path="/signup" element={<AuthPage mode="signup" />} />
      <Route path="/projects/:projectId" element={<RequireAuth><WorkspacePage /></RequireAuth>} />
      <Route path="/billing" element={<RequireAuth><BillingPage /></RequireAuth>} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
