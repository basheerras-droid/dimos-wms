import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { CssBaseline, ThemeProvider } from '@mui/material'
import { BrowserRouter, Route, Routes } from 'react-router'
import AppShell from './components/AppShell'
import { navItems } from './components/navItems'
import ChangePasswordPage from './components/ChangePasswordPage'
import DashboardPage from './components/DashboardPage'
import LoginPage from './components/LoginPage'
import NotFoundPage from './components/NotFoundPage'
import PlaceholderPage from './components/PlaceholderPage'
import { theme } from './theme'

const queryClient = new QueryClient()

export default function App() {
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/change-password" element={<ChangePasswordPage />} />
            <Route element={<AppShell />}>
              <Route index element={<DashboardPage />} />
              {navItems
                .filter((n) => n.path !== '/')
                .map((n) => (
                  <Route key={n.key} path={n.path} element={<PlaceholderPage navKey={n.key} />} />
                ))}
              <Route path="*" element={<NotFoundPage />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  )
}
