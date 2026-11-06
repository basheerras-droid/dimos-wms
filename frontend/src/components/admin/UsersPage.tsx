import AddIcon from '@mui/icons-material/Add'
import CloseIcon from '@mui/icons-material/Close'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import EditIcon from '@mui/icons-material/EditOutlined'
import LockOpenIcon from '@mui/icons-material/LockOpen'
import {
  Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle,
  Divider, Drawer, FormControlLabel, FormGroup, IconButton, MenuItem, Paper, Snackbar, Stack, Switch, TextField,
  Tooltip, Typography,
} from '@mui/material'
import {
  DataGrid, type GridColDef, type GridPaginationModel, type GridRenderCellParams, type GridSortModel,
} from '@mui/x-data-grid'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  fetchUser, useAdminUsers, useResetPassword, useRoles, useSaveUser, useUnlockUser, type AdminUser, type RoleInfo,
} from '../../api/admin'
import { useMe } from '../../api/auth'
import { ApiError } from '../../api/client'
import { formatDateTime } from '../../format'
import { errorText } from '../masterdata/errorText'

type Target = AdminUser | 'new' | null

/** Users: list, create, edit name / e-mail / roles, enable or disable, unlock, and reset the password. */
export default function UsersPage() {
  const { t } = useTranslation()
  const me = useMe()
  const roles = useRoles()

  const [paging, setPaging] = useState<GridPaginationModel>({ page: 0, pageSize: 25 })
  const [sortModel, setSortModel] = useState<GridSortModel>([])
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [enabled, setEnabled] = useState('')
  const [role, setRole] = useState('')
  const [target, setTarget] = useState<Target>(null)
  const [password, setPassword] = useState<{ username: string; value: string; created: boolean } | null>(null)
  const [notice, setNotice] = useState<{ text: string; severity: 'success' | 'error' } | null>(null)

  useEffect(() => {
    const id = setTimeout(() => {
      setQ(search)
      setPaging((p) => ({ ...p, page: 0 }))
    }, 300)
    return () => clearTimeout(id)
  }, [search])

  const sort = sortModel.length > 0 ? `${sortModel[0].field},${sortModel[0].sort}` : 'username,asc'
  const list = useAdminUsers({ q, enabled, role, page: paging.page, size: paging.pageSize, sort })

  const columns = useMemo<GridColDef<AdminUser>[]>(
    () => [
      { field: 'username', headerName: t('admin.users.username'), width: 170, sortable: true },
      { field: 'displayName', headerName: t('admin.users.displayName'), flex: 1, minWidth: 160, sortable: true },
      { field: 'email', headerName: t('admin.users.email'), flex: 1, minWidth: 180, sortable: true },
      {
        field: 'roles', headerName: t('admin.users.roles'), width: 220, sortable: false,
        renderCell: (p: GridRenderCellParams<AdminUser>) => (
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', height: '100%' }}>
            {p.row.roles.map((r) => <Chip key={r} size="small" label={r} variant={r === 'SUPER_ADMIN' ? 'filled' : 'outlined'} />)}
          </Stack>
        ),
      },
      {
        field: 'status', headerName: t('admin.users.status'), width: 260, sortable: false,
        renderCell: (p: GridRenderCellParams<AdminUser>) => (
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', height: '100%' }}>
            {!p.row.enabled
              ? <Chip size="small" color="default" label={t('admin.users.disabled')} />
              : <Chip size="small" color="success" variant="outlined" label={t('admin.users.active')} />}
            {p.row.locked && <Chip size="small" color="error" label={t('admin.users.locked')} />}
            {p.row.enabled && p.row.mustChangePassword && <Chip size="small" color="warning" variant="outlined" label={t('admin.users.mustChange')} />}
            {!p.row.hasPassword && <Chip size="small" color="warning" label={t('admin.users.noPassword')} />}
          </Stack>
        ),
      },
      {
        field: 'lastLoginAt', headerName: t('admin.users.lastLogin'), width: 180, sortable: true,
        valueFormatter: (v: string | null) => formatDateTime(v),
      },
      {
        field: '__actions', headerName: '', width: 70, sortable: false, disableColumnMenu: true,
        renderCell: (p: GridRenderCellParams<AdminUser>) => (
          <Tooltip title={t('md.edit')}>
            <IconButton size="small" aria-label={t('md.edit')} onClick={() => setTarget(p.row)}><EditIcon fontSize="small" /></IconButton>
          </Tooltip>
        ),
      },
    ],
    [t],
  )

  return (
    <>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>{t('admin.users.title')}</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setTarget('new')}>{t('admin.users.add')}</Button>
      </Stack>

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1.5 }}>
        <TextField size="small" label={t('admin.users.search')} value={search} onChange={(e) => setSearch(e.target.value)}
          sx={{ minWidth: { xs: '100%', sm: 280 } }} />
        <TextField select size="small" label={t('admin.users.status')} value={enabled} sx={{ minWidth: 160 }}
          onChange={(e) => { setEnabled(e.target.value); setPaging((p) => ({ ...p, page: 0 })) }}>
          <MenuItem value="">{t('md.all')}</MenuItem>
          <MenuItem value="true">{t('admin.users.active')}</MenuItem>
          <MenuItem value="false">{t('admin.users.disabled')}</MenuItem>
        </TextField>
        <TextField select size="small" label={t('admin.users.role')} value={role} sx={{ minWidth: 180 }}
          onChange={(e) => { setRole(e.target.value); setPaging((p) => ({ ...p, page: 0 })) }}>
          <MenuItem value="">{t('md.all')}</MenuItem>
          {(roles.data ?? []).map((r) => <MenuItem key={r.code} value={r.code}>{r.code}</MenuItem>)}
        </TextField>
      </Stack>

      {list.isError && <Alert severity="error" sx={{ mb: 2 }}>{errorText(t, list.error)}</Alert>}

      <Paper variant="outlined">
        <DataGrid<AdminUser>
          rows={list.data?.items ?? []} columns={columns} loading={list.isFetching} rowCount={list.data?.total ?? 0}
          paginationMode="server" sortingMode="server" paginationModel={paging} onPaginationModelChange={setPaging}
          sortModel={sortModel} onSortModelChange={setSortModel} pageSizeOptions={[25, 50, 100]}
          disableRowSelectionOnClick autoHeight onRowDoubleClick={(p) => setTarget(p.row)}
          localeText={{ noRowsLabel: t('md.noRows') }} sx={{ border: 0 }}
        />
      </Paper>

      <Drawer anchor="right" open={target !== null} onClose={() => setTarget(null)}
        slotProps={{ paper: { sx: { width: { xs: '100%', sm: 480 } } } }}>
        {target !== null && (
          <UserForm
            key={target === 'new' ? 'new' : target.id}
            user={target === 'new' ? null : target}
            roles={roles.data ?? []}
            meUsername={me.data?.username ?? ''}
            meRoles={me.data?.roles ?? []}
            onClose={() => setTarget(null)}
            onPassword={(username, value, created) => setPassword({ username, value, created })}
            onNotice={(text, severity) => setNotice({ text, severity })}
          />
        )}
      </Drawer>

      <TemporaryPasswordDialog info={password} onClose={() => setPassword(null)} />

      <Snackbar open={notice !== null} autoHideDuration={6000} onClose={() => setNotice(null)}>
        {notice ? <Alert severity={notice.severity} onClose={() => setNotice(null)}>{notice.text}</Alert> : undefined}
      </Snackbar>
    </>
  )
}

interface FormProps {
  user: AdminUser | null
  roles: RoleInfo[]
  meUsername: string
  meRoles: string[]
  onClose: () => void
  onPassword: (username: string, value: string, created: boolean) => void
  onNotice: (text: string, severity: 'success' | 'error') => void
}

function UserForm({ user: initial, roles, meUsername, meRoles, onClose, onPassword, onNotice }: FormProps) {
  const { t } = useTranslation()
  const save = useSaveUser()
  const reset = useResetPassword()
  const unlock = useUnlockUser()

  const [user, setUser] = useState<AdminUser | null>(initial)
  const [username, setUsername] = useState(initial?.username ?? '')
  const [displayName, setDisplayName] = useState(initial?.displayName ?? '')
  const [email, setEmail] = useState(initial?.email ?? '')
  const [selected, setSelected] = useState<Set<string>>(() => new Set(initial?.roles ?? ['VIEWER']))
  const [enabled, setEnabled] = useState(initial?.enabled ?? true)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<{ text: string; stale: boolean } | null>(null)
  const [confirmReset, setConfirmReset] = useState(false)

  const creating = user === null
  const iAmSuper = meRoles.includes('SUPER_ADMIN')
  const isSelf = user !== null && user.username === meUsername
  const targetIsSuper = user?.roles.includes('SUPER_ADMIN') ?? false
  // Only a super administrator may change a super administrator; nobody changes their own roles or disables themselves.
  const readOnly = targetIsSuper && !iAmSuper

  function failed(e: unknown) {
    if (e instanceof ApiError) {
      setErrors(e.fieldErrors)
      const rest = Object.entries(e.fieldErrors).filter(([k]) => !['username', 'displayName', 'email', 'roles'].includes(k))
      const text = errorText(t, e)
      setMessage({ text: rest.length ? `${text} ${rest.map(([k, v]) => `${k}: ${v}`).join(' ')}` : text, stale: e.isStale })
    } else setMessage({ text: errorText(t, e), stale: false })
  }

  function submit() {
    const found: Record<string, string> = {}
    if (creating && username.trim().length < 3) found.username = t('admin.users.usernameHint')
    if (displayName.trim() === '') found.displayName = t('md.error.required')
    if (selected.size === 0) found.roles = t('admin.users.rolesRequired')
    setErrors(found)
    if (Object.keys(found).length > 0) return
    setMessage(null)
    const body = {
      username: creating ? username.trim() : undefined,
      displayName: displayName.trim(),
      email: email.trim() === '' ? null : email.trim(),
      roles: [...selected],
      enabled,
      version: user?.version,
    }
    save.mutate({ id: user?.id, body }, {
      onSuccess: (result) => {
        if ('temporaryPassword' in result) onPassword(result.user.username, result.temporaryPassword, true)
        else onNotice(t('admin.users.saved'), 'success')
        onClose()
      },
      onError: failed,
    })
  }

  async function reloadRecord() {
    if (!user) return
    try {
      const fresh = await fetchUser(user.id)
      setUser(fresh)
      setDisplayName(fresh.displayName)
      setEmail(fresh.email ?? '')
      setSelected(new Set(fresh.roles))
      setEnabled(fresh.enabled)
      setErrors({})
      setMessage(null)
    } catch (e) {
      failed(e)
    }
  }

  function doReset() {
    if (!user) return
    reset.mutate(user.id, {
      onSuccess: (r) => { setConfirmReset(false); onPassword(user.username, r.temporaryPassword, false); onClose() },
      onError: (e) => { setConfirmReset(false); failed(e) },
    })
  }

  function doUnlock() {
    if (!user) return
    unlock.mutate(user.id, {
      onSuccess: (fresh) => { setUser(fresh); onNotice(t('admin.users.unlocked'), 'success') },
      onError: failed,
    })
  }

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', px: 2.5, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h6" sx={{ flexGrow: 1 }}>
          {creating ? t('admin.users.new') : readOnly ? t('admin.users.view') : t('admin.users.edit')}
        </Typography>
        <IconButton aria-label={t('md.close')} onClick={onClose}><CloseIcon /></IconButton>
      </Box>

      <Stack spacing={2.5} sx={{ p: 2.5, overflowY: 'auto', flex: 1 }} component="form" noValidate
        onSubmit={(e) => { e.preventDefault(); if (!readOnly) submit() }}>
        {message && (
          <Alert severity="error" action={message.stale ? <Button color="inherit" size="small" onClick={() => void reloadRecord()}>{t('md.reload')}</Button> : undefined}>
            {message.text}
          </Alert>
        )}
        {readOnly && <Alert severity="info">{t('admin.users.superOnly')}</Alert>}
        {creating && <Alert severity="info">{t('admin.users.passwordInfo')}</Alert>}

        <TextField label={t('admin.users.username')} value={username} onChange={(e) => setUsername(e.target.value)}
          required={creating} disabled={!creating} error={Boolean(errors.username)}
          helperText={errors.username ?? (creating ? t('admin.users.usernameHint') : t('admin.users.usernameFixed'))}
          slotProps={{ htmlInput: { maxLength: 100, autoCapitalize: 'none' } }} />
        <TextField label={t('admin.users.displayName')} value={displayName} onChange={(e) => setDisplayName(e.target.value)}
          required disabled={readOnly} error={Boolean(errors.displayName)} helperText={errors.displayName}
          slotProps={{ htmlInput: { maxLength: 150 } }} />
        <TextField label={t('admin.users.email')} value={email} onChange={(e) => setEmail(e.target.value)} type="email"
          disabled={readOnly} error={Boolean(errors.email)} helperText={errors.email}
          slotProps={{ htmlInput: { maxLength: 255 } }} />

        <Box>
          <Typography sx={{ fontWeight: 600 }}>{t('admin.users.roles')} *</Typography>
          {isSelf && <Typography variant="caption" color="text.secondary">{t('admin.users.ownRolesFixed')}</Typography>}
          <FormGroup>
            {roles.map((r) => (
              <FormControlLabel
                key={r.code}
                disabled={readOnly || isSelf || (r.code === 'SUPER_ADMIN' && !iAmSuper)}
                control={
                  <Checkbox
                    checked={selected.has(r.code)}
                    onChange={(e) => setSelected((prev) => {
                      const next = new Set(prev)
                      if (e.target.checked) next.add(r.code)
                      else next.delete(r.code)
                      return next
                    })}
                  />
                }
                label={<Box><Typography>{r.code} - {r.name}</Typography>
                  {r.description && <Typography variant="caption" color="text.secondary">{r.description}</Typography>}</Box>}
              />
            ))}
          </FormGroup>
          {errors.roles && <Typography variant="caption" color="error">{errors.roles}</Typography>}
        </Box>

        {!creating && (
          <Box>
            <FormControlLabel
              control={<Switch checked={enabled} onChange={(e) => setEnabled(e.target.checked)} disabled={readOnly || isSelf} />}
              label={t('admin.users.enabledLabel')}
            />
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
              {isSelf ? t('admin.users.ownAccountFixed') : t('admin.users.enabledHelp')}
            </Typography>
          </Box>
        )}

        {user && (
          <>
            <Divider />
            <Box>
              <Typography sx={{ fontWeight: 600, mb: 1 }}>{t('admin.users.account')}</Typography>
              <Typography variant="body2" color="text.secondary">
                {t('admin.users.lastLoginAt', { time: formatDateTime(user.lastLoginAt) || t('admin.users.never') })}
              </Typography>
              {user.locked && (
                <Alert severity="warning" sx={{ mt: 1 }}
                  action={!readOnly && <Button color="inherit" size="small" startIcon={<LockOpenIcon />} onClick={doUnlock} disabled={unlock.isPending}>{t('admin.users.unlock')}</Button>}>
                  {t('admin.users.lockedInfo', { until: formatDateTime(user.lockedUntil), count: user.failedAttempts })}
                </Alert>
              )}
              {!readOnly && !isSelf && (
                <Button variant="outlined" color="secondary" sx={{ mt: 1.5 }} onClick={() => setConfirmReset(true)}>
                  {t('admin.users.resetPassword')}
                </Button>
              )}
              {isSelf && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>{t('admin.users.ownPassword')}</Typography>}
            </Box>
          </>
        )}
        <Typography variant="caption" color="text.secondary">{t('md.requiredHint')}</Typography>
      </Stack>

      <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'flex-end', px: 2.5, py: 1.5, borderTop: 1, borderColor: 'divider' }}>
        <Button onClick={onClose} color="secondary" variant="text">{readOnly ? t('md.close') : t('md.cancel')}</Button>
        {!readOnly && (
          <Button variant="contained" onClick={submit} disabled={save.isPending}>
            {save.isPending ? t('md.saving') : creating ? t('admin.users.create') : t('md.save')}
          </Button>
        )}
      </Box>

      <Dialog open={confirmReset} onClose={() => setConfirmReset(false)}>
        <DialogTitle>{t('admin.users.resetTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>{t('admin.users.resetText', { name: user?.username ?? '' })}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="secondary" onClick={() => setConfirmReset(false)}>{t('md.cancel')}</Button>
          <Button variant="contained" onClick={doReset} disabled={reset.isPending}>{t('admin.users.resetPassword')}</Button>
        </DialogActions>
      </Dialog>
    </>
  )
}

/** Shows a temporary password once. It is not stored anywhere readable, so it cannot be shown again. */
function TemporaryPasswordDialog({ info, onClose }: { info: { username: string; value: string; created: boolean } | null; onClose: () => void }) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  return (
    <Dialog open={info !== null} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle>{info?.created ? t('admin.users.createdTitle') : t('admin.users.resetDoneTitle')}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>{t('admin.users.tempText', { name: info?.username ?? '' })}</DialogContentText>
        <Paper variant="outlined" sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 1 }}>
          <Typography sx={{ fontFamily: 'monospace', fontSize: 22, letterSpacing: 1, flexGrow: 1, userSelect: 'all' }}>
            {info?.value}
          </Typography>
          <Tooltip title={copied ? t('admin.users.copied') : t('admin.users.copy')}>
            <IconButton aria-label={t('admin.users.copy')}
              onClick={() => { void navigator.clipboard?.writeText(info?.value ?? '').then(() => setCopied(true)) }}>
              <ContentCopyIcon />
            </IconButton>
          </Tooltip>
        </Paper>
        <Alert severity="warning" sx={{ mt: 2 }}>{t('admin.users.tempWarning')}</Alert>
      </DialogContent>
      <DialogActions>
        <Button variant="contained" onClick={() => { setCopied(false); onClose() }}>{t('admin.users.noted')}</Button>
      </DialogActions>
    </Dialog>
  )
}
