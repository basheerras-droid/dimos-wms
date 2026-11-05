import { Alert, Box, Button, Card, CardContent, TextField, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate } from 'react-router'
import { ApiError } from '../api/client'
import { useLogin, useMe } from '../api/auth'
import { brand } from '../theme'

export default function LoginPage() {
  const { t } = useTranslation()
  const me = useMe()
  const login = useLogin()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  if (me.data) {
    return <Navigate to={me.data.mustChangePassword ? '/change-password' : '/'} replace />
  }

  function message(e: unknown): string {
    if (e instanceof ApiError) {
      if (e.code === 'ACCOUNT_LOCKED') return t('login.locked')
      if (e.code === 'INVALID_CREDENTIALS') return t('login.failed')
      if (e.status >= 500) return t('login.serverError')
      return t('login.failed')
    }
    return t('login.network')
  }

  function submit(ev: FormEvent) {
    ev.preventDefault()
    if (!username.trim() || !password) {
      setError(t('login.required'))
      return
    }
    setError(null)
    login.mutate({ username: username.trim(), password }, { onError: (e) => setError(message(e)) })
  }

  return (
    <Box sx={{ minHeight: '100%', display: 'flex', flexDirection: 'column', bgcolor: brand.page }}>
      <Box sx={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', p: 2 }}>
        <Card sx={{ width: '100%', maxWidth: 420 }}>
          <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
            <Box
              component="img"
              src="/brand/dimos-logo.png"
              alt="DIMOS"
              sx={{ display: 'block', height: 40, width: 'auto', mb: 3 }}
            />
            <Typography variant="h5" component="h1">
              {t('app.name')}
            </Typography>
            <Typography color="text.secondary" sx={{ mb: 3 }}>
              {t('login.subtitle')}
            </Typography>
            <Box component="form" onSubmit={submit} noValidate>
              {error && (
                <Alert severity="error" sx={{ mb: 2 }} role="alert">
                  {error}
                </Alert>
              )}
              <TextField
                label={t('login.username')}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
                autoFocus
                fullWidth
                margin="normal"
              />
              <TextField
                label={t('login.password')}
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                fullWidth
                margin="normal"
              />
              <Button type="submit" variant="contained" size="large" fullWidth disabled={login.isPending} sx={{ mt: 2 }}>
                {login.isPending ? t('login.submitting') : t('login.submit')}
              </Button>
            </Box>
          </CardContent>
        </Card>
      </Box>
      <Typography variant="caption" color="text.secondary" align="center" sx={{ pb: 2 }}>
        {t('app.name')} - {t('app.version', { version: __APP_VERSION__ })}
      </Typography>
    </Box>
  )
}
