import { useEffect } from 'react'
import AppRouter from './routes/AppRouter'
import ErrorBoundary from './components/shared/ErrorBoundary'
import api from './lib/api'

export default function App() {
  useEffect(() => {
    // Non-blocking pre-warm ping to Render API
    // Initiates container wakeup early so subsequent operations are fast
    api.get('/actuator/health').catch(() => {})
  }, [])

  return (
    <ErrorBoundary>
      <AppRouter />
    </ErrorBoundary>
  )
}
