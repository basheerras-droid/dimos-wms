import AddIcon from '@mui/icons-material/Add'
import ContentCopyIcon from '@mui/icons-material/ContentCopy'
import DeleteIcon from '@mui/icons-material/DeleteOutlined'
import EditIcon from '@mui/icons-material/EditOutlined'
import LockIcon from '@mui/icons-material/LockOutlined'
import {
  Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, IconButton,
  MenuItem, Paper, Snackbar, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField,
  Tooltip, Typography,
} from '@mui/material'
import { Fragment, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useDeleteRole, usePermissions, useRoles, useSaveRole, type Permission, type RoleInfo,
} from '../../api/admin'
import { useMe } from '../../api/auth'
import { ApiError } from '../../api/client'
import { errorText } from '../masterdata/errorText'

type Dialog_ = { mode: 'new' | 'edit' | 'copy'; role?: RoleInfo } | null

/**
 * Roles x permissions as a tick matrix. The built-in roles are locked; custom roles can be created, copied, edited
 * and deleted by a super administrator. Ticks are saved per role.
 */
export default function RolesPage() {
  const { t } = useTranslation()
  const me = useMe()
  const roles = useRoles()
  const permissions = usePermissions()
  const save = useSaveRole()
  const remove = useDeleteRole()

  const canEdit = (me.data?.roles.includes('SUPER_ADMIN') ?? false) && (me.data?.permissions.includes('ROLE_MANAGE') ?? false)
  const [edits, setEdits] = useState<Record<number, Set<string>>>({})
  const [dialog, setDialog] = useState<Dialog_>(null)
  const [toDelete, setToDelete] = useState<RoleInfo | null>(null)
  const [notice, setNotice] = useState<{ text: string; severity: 'success' | 'error' } | null>(null)

  const roleList = roles.data ?? []
  const modules = [...new Set((permissions.data ?? []).map((p) => p.module))]

  const ticked = (r: RoleInfo, code: string) => (edits[r.id] ? edits[r.id].has(code) : r.permissions.includes(code))

  function toggle(r: RoleInfo, code: string) {
    setEdits((prev) => {
      const next = new Set(prev[r.id] ?? r.permissions)
      if (next.has(code)) next.delete(code)
      else next.add(code)
      return { ...prev, [r.id]: next }
    })
  }

  function discard(r: RoleInfo) {
    setEdits((prev) => {
      const { [r.id]: _gone, ...rest } = prev
      return rest
    })
  }

  function saveTicks(r: RoleInfo) {
    save.mutate(
      { id: r.id, body: { code: r.code, name: r.name, description: r.description, permissions: [...(edits[r.id] ?? [])], version: r.version } },
      {
        onSuccess: () => { discard(r); setNotice({ text: t('admin.roles.saved', { name: r.name }), severity: 'success' }) },
        onError: (e) => setNotice({ text: errorText(t, e), severity: 'error' }),
      },
    )
  }

  function confirmDelete() {
    if (!toDelete) return
    remove.mutate(toDelete.id, {
      onSuccess: () => { setNotice({ text: t('md.deleted'), severity: 'success' }); setToDelete(null) },
      onError: (e) => { setNotice({ text: errorText(t, e), severity: 'error' }); setToDelete(null) },
    })
  }

  return (
    <>
      <Stack direction="row" spacing={2} sx={{ mb: 1, alignItems: 'center' }}>
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>{t('admin.roles.title')}</Typography>
        {canEdit && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setDialog({ mode: 'new' })}>{t('admin.roles.add')}</Button>}
      </Stack>
      <Typography color="text.secondary" sx={{ mb: 2 }}>{t('admin.roles.intro')}</Typography>
      {!canEdit && <Alert severity="info" sx={{ mb: 2 }}>{t('admin.roles.superOnly')}</Alert>}
      {(roles.isError || permissions.isError) && <Alert severity="error" sx={{ mb: 2 }}>{errorText(t, roles.error ?? permissions.error)}</Alert>}

      <TableContainer component={Paper} variant="outlined" sx={{ maxHeight: '72vh' }}>
        <Table size="small" stickyHeader>
          <TableHead>
            <TableRow>
              <TableCell sx={{ minWidth: 300, position: 'sticky', left: 0, zIndex: 3, bgcolor: 'background.paper' }}>{t('admin.roles.permission')}</TableCell>
              {roleList.map((r) => (
                <TableCell key={r.id} align="center" sx={{ minWidth: 130, verticalAlign: 'top' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 0.5 }}>
                    {r.builtin && <Tooltip title={t('admin.roles.builtInHint')}><LockIcon fontSize="small" color="disabled" /></Tooltip>}
                    <Typography sx={{ fontWeight: 700 }}>{r.name}</Typography>
                  </Box>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {r.code} - {t('admin.roles.usedBy', { users: r.userCount, groups: r.groupCount })}
                  </Typography>
                  {canEdit && (
                    <Stack direction="row" spacing={0} sx={{ justifyContent: 'center', mt: 0.5 }}>
                      <Tooltip title={t('admin.roles.copy')}>
                        <IconButton size="small" aria-label={t('admin.roles.copy')} onClick={() => setDialog({ mode: 'copy', role: r })}><ContentCopyIcon fontSize="small" /></IconButton>
                      </Tooltip>
                      {!r.builtin && (
                        <>
                          <Tooltip title={t('md.edit')}>
                            <IconButton size="small" aria-label={t('md.edit')} onClick={() => setDialog({ mode: 'edit', role: r })}><EditIcon fontSize="small" /></IconButton>
                          </Tooltip>
                          <Tooltip title={t('md.delete')}>
                            <IconButton size="small" aria-label={t('md.delete')} onClick={() => setToDelete(r)}><DeleteIcon fontSize="small" /></IconButton>
                          </Tooltip>
                        </>
                      )}
                    </Stack>
                  )}
                  {edits[r.id] && (
                    <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'center', mt: 0.5 }}>
                      <Button size="small" variant="contained" disabled={save.isPending} onClick={() => saveTicks(r)}>{t('md.save')}</Button>
                      <Button size="small" color="secondary" onClick={() => discard(r)}>{t('admin.roles.discard')}</Button>
                    </Stack>
                  )}
                </TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {modules.map((module) => (
              <Fragment key={module}>
                <TableRow>
                  <TableCell colSpan={roleList.length + 1} sx={{ bgcolor: 'background.default', fontWeight: 700 }}>
                    {t(`admin.modules.${module}`, { defaultValue: module })}
                  </TableCell>
                </TableRow>
                {(permissions.data ?? []).filter((p: Permission) => p.module === module).map((p) => (
                  <TableRow key={p.code} hover>
                    <TableCell sx={{ position: 'sticky', left: 0, zIndex: 1, bgcolor: 'background.paper' }}>
                      <Typography sx={{ fontFamily: 'monospace', fontSize: 13 }}>{p.code}</Typography>
                      <Typography variant="caption" color="text.secondary">{p.description}</Typography>
                    </TableCell>
                    {roleList.map((r) => (
                      <TableCell key={r.id} align="center" sx={r.builtin ? { bgcolor: 'action.hover' } : undefined}>
                        <Checkbox
                          checked={ticked(r, p.code)}
                          disabled={r.builtin || !canEdit}
                          onChange={() => toggle(r, p.code)}
                          slotProps={{ input: { 'aria-label': `${r.name}: ${p.code}` } }}
                        />
                      </TableCell>
                    ))}
                  </TableRow>
                ))}
              </Fragment>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>{t('admin.roles.help')}</Typography>

      {dialog && (
        <RoleDialog
          key={`${dialog.mode}-${dialog.role?.id ?? 'new'}`}
          state={dialog}
          roles={roleList}
          onClose={() => setDialog(null)}
          onDone={(text) => { setDialog(null); setNotice({ text, severity: 'success' }) }}
        />
      )}

      <Dialog open={toDelete !== null} onClose={() => setToDelete(null)}>
        <DialogTitle>{t('md.deleteTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>{t('md.deleteText', { name: toDelete?.code ?? '' })}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="secondary" onClick={() => setToDelete(null)}>{t('md.cancel')}</Button>
          <Button variant="contained" disabled={remove.isPending} onClick={confirmDelete}>{t('md.delete')}</Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={notice !== null} autoHideDuration={6000} onClose={() => setNotice(null)}>
        {notice ? <Alert severity={notice.severity} onClose={() => setNotice(null)}>{notice.text}</Alert> : undefined}
      </Snackbar>
    </>
  )
}

function RoleDialog({ state, roles, onClose, onDone }: {
  state: NonNullable<Dialog_>; roles: RoleInfo[]; onClose: () => void; onDone: (text: string) => void
}) {
  const { t } = useTranslation()
  const save = useSaveRole()
  const source = state.role
  const editing = state.mode === 'edit'
  const [code, setCode] = useState(editing ? (source?.code ?? '') : '')
  const [name, setName] = useState(editing ? (source?.name ?? '') : source ? t('admin.roles.copyOf', { name: source.name }) : '')
  const [description, setDescription] = useState(editing ? (source?.description ?? '') : '')
  const [copyFrom, setCopyFrom] = useState(source && !editing ? String(source.id) : '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)

  function submit() {
    const found: Record<string, string> = {}
    if (!editing && !/^[A-Z][A-Z0-9_]{1,49}$/.test(code)) found.code = t('admin.roles.codeHint')
    if (name.trim() === '') found.name = t('md.error.required')
    setErrors(found)
    if (Object.keys(found).length > 0) return
    const template = roles.find((r) => String(r.id) === copyFrom)
    save.mutate(
      {
        id: editing ? source?.id : undefined,
        body: {
          code, name: name.trim(), description: description.trim() === '' ? null : description.trim(),
          permissions: editing ? (source?.permissions ?? []) : (template?.permissions ?? []), version: editing ? source?.version : undefined,
        },
      },
      {
        onSuccess: () => onDone(t('admin.roles.saved', { name: name.trim() })),
        onError: (e) => {
          if (e instanceof ApiError) setErrors(e.fieldErrors)
          setMessage(errorText(t, e))
        },
      },
    )
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>{editing ? t('admin.roles.edit') : t('admin.roles.new')}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2.5} sx={{ pt: 0.5 }}>
          {message && <Alert severity="error">{message}</Alert>}
          <TextField label={t('md.fields.code')} required={!editing} disabled={editing} value={code}
            onChange={(e) => setCode(e.target.value.toUpperCase())} error={Boolean(errors.code)}
            helperText={errors.code ?? t('admin.roles.codeHint')} slotProps={{ htmlInput: { maxLength: 50 } }} />
          <TextField label={t('md.fields.name')} required value={name} onChange={(e) => setName(e.target.value)}
            error={Boolean(errors.name)} helperText={errors.name} slotProps={{ htmlInput: { maxLength: 100 } }} />
          <TextField label={t('admin.roles.description')} value={description} onChange={(e) => setDescription(e.target.value)}
            multiline minRows={2} slotProps={{ htmlInput: { maxLength: 255 } }} />
          {!editing && (
            <TextField select label={t('admin.roles.copyFrom')} value={copyFrom} onChange={(e) => setCopyFrom(e.target.value)}
              helperText={t('admin.roles.copyFromHelp')}>
              <MenuItem value="">{t('admin.roles.nothing')}</MenuItem>
              {roles.map((r) => <MenuItem key={r.id} value={String(r.id)}>{r.name} ({r.code})</MenuItem>)}
            </TextField>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button color="secondary" onClick={onClose}>{t('md.cancel')}</Button>
        <Button variant="contained" onClick={submit} disabled={save.isPending}>{save.isPending ? t('md.saving') : t('md.save')}</Button>
      </DialogActions>
    </Dialog>
  )
}
