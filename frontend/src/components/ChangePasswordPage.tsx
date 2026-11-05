import { Alert, Box, Button, Card, CardContent, TextField, Typography } from '@mui/material'
import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, useNavigate } from 'react-router'
import { ApiError } from '../api/client'
import { useChangePassword, useLogout, useMe } from '../api/auth'
import { brand } from '../theme'

export default function ChangePasswordPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const me = useMe()
  const change = useChangePassword()
  const logout = useLogout()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [repeat, setRepeat] = useState('')
  const [error, setError] = useState<string | null>(null)

  if (me.isLoading) return null
  if (!me.data) return <Navigate to="/login" replace />
  const forced = me.data.mustChangePassword

  function message(e: unknown): string {
    if (e instanceof ApiError) {
      if (e.code === 'WRONG_CURRENT_PASSWORD') return t('changePassword.wrongCurrent')
      if (e.code === 'SAME_PASSWORD') return t('changePassword.same')
      if (e.code === 'VALIDATION_ERROR') return t('changePassword.tooShort')
    }
    return t('changePassword.failed')
  }

  function submit(ev: FormEvent) {
    ev.preventDefault()
    if (next.length < 8) return setError(t('changePassword.tooShort'))
    if (next !== repeat) return setError(t('changePassword.mismatch'))
    setError(null)
    change.mutate(
      { currentPassword: current, newPassword: next },
      { onSuccess: () => navigate('/', { replace: true }), onError: (e) => setError(message(e)) },
    )
  }

  return (
    <Box sx={{ minHeight: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', p: 2, bgcolor: brand.page }}>
      <Card sx={{ width: '100%', maxWidth: 460 }}>
        <CardContent sx={{ p: { xs: 3, sm: 4 } }}>
          <Box component="img" src="/brand/dimos-logo.png" alt="DIMOS" sx={{ display: 'block', height: 40, width: 'auto', mb: 3 }} />
          <Typography variant="h5" component="h1" gutterBottom>
            {t('changePassword.title')}
          </Typography>
          {forced && (
            <Alert severity="info" sx={{ mb: 2 }}>
              {t('changePassword.forced')}
            </Alert>
          )}
          <Box component="form" onSubmit={submit} noValidate>
            {error && (
              <Alert severity="error" sx={{ mb: 2 }} role="alert">
                {error}
              </Alert>
            )}
            <TextField label={t('changePassword.current')} type="password" value={current} onChange={(e) => setCurrent(e.target.value)} autoComplete="current-password" autoFocus fullWidth margin="normal" />
            <TextField label={t('changePassword.new')} type="password" value={next} onChange={(e) => setNext(e.target.value)} autoComplete="new-password" helperText={t('changePassword.hint')} fullWidth margin="normal" />
            <TextField label={t('changePassword.confirm')} type="password" value={repeat} onChange={(e) => setRepeat(e.target.value)} autoComplete="new-password" fullWidth margin="normal" />
            <Button type="submit" variant="contained" size="large" fullWidth disabled={change.isPending} sx={{ mt: 2 }}>
              {change.isPending ? t('changePassword.submitting') : t('changePassword.submit')}
            </Button>
            <Button color="secondary" fullWidth sx={{ mt: 1 }} onClick={() => logout.mutate(undefined, { onSettled: () => navigate('/login', { replace: true }) })}>
              {t('changePassword.cancel')}
            </Button>
          </Box>
        </CardContent>
      </Card>
    </Box>
  )
}
