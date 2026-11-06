import AddIcon from '@mui/icons-material/Add'
import CloseIcon from '@mui/icons-material/Close'
import DeleteIcon from '@mui/icons-material/DeleteOutlined'
import EditIcon from '@mui/icons-material/EditOutlined'
import {
  Alert, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Drawer,
  FormControlLabel, IconButton, Paper, Snackbar, Stack, Switch, TextField, Tooltip, Typography,
} from '@mui/material'
import { DataGrid, type GridColDef, type GridPaginationModel, type GridRenderCellParams } from '@mui/x-data-grid'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  fetchWorkstationGroup, useDeleteWorkstationGroup, usePermissions, useSaveWorkstationGroup, useWorkstationGroups,
  type Permission, type WorkstationGroup,
} from '../../api/admin'
import { ApiError } from '../../api/client'
import { errorText } from '../masterdata/errorText'

/** Workstation groups: an optional list of the permissions allowed at the workstations of the group. */
export default function WorkstationGroupsPage() {
  const { t } = useTranslation()
  const remove = useDeleteWorkstationGroup()
  const permissions = usePermissions()
  const [paging, setPaging] = useState<GridPaginationModel>({ page: 0, pageSize: 25 })
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [target, setTarget] = useState<WorkstationGroup | 'new' | null>(null)
  const [toDelete, setToDelete] = useState<WorkstationGroup | null>(null)
  const [notice, setNotice] = useState<{ text: string; severity: 'success' | 'error' } | null>(null)

  useEffect(() => {
    const id = setTimeout(() => { setQ(search); setPaging((p) => ({ ...p, page: 0 })) }, 300)
    return () => clearTimeout(id)
  }, [search])

  const list = useWorkstationGroups({ q, page: paging.page, size: paging.pageSize })
  const columns = useMemo<GridColDef<WorkstationGroup>[]>(() => [
    { field: 'code', headerName: t('md.fields.code'), width: 180 },
    { field: 'name', headerName: t('md.fields.name'), flex: 1, minWidth: 180 },
    {
      field: 'allowedPermissions', headerName: t('admin.wsGroups.restriction'), width: 280, sortable: false,
      renderCell: (p: GridRenderCellParams<WorkstationGroup>) => p.row.allowedPermissions.length === 0
        ? <Chip size="small" variant="outlined" label={t('admin.wsGroups.noRestriction')} />
        : <Chip size="small" color="warning" variant="outlined" label={t('admin.wsGroups.onlyN', { count: p.row.allowedPermissions.length })} />,
    },
    { field: 'workstationCount', headerName: t('admin.wsGroups.workstations'), width: 130, type: 'number', sortable: false },
    {
      field: 'enabled', headerName: t('admin.users.status'), width: 130,
      renderCell: (p: GridRenderCellParams<WorkstationGroup>) => p.row.enabled
        ? <Chip size="small" color="success" variant="outlined" label={t('admin.users.active')} />
        : <Chip size="small" label={t('admin.users.disabled')} />,
    },
    {
      field: '__actions', headerName: '', width: 100, sortable: false, disableColumnMenu: true,
      renderCell: (p: GridRenderCellParams<WorkstationGroup>) => (
        <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
          <Tooltip title={t('md.edit')}><IconButton size="small" aria-label={t('md.edit')} onClick={() => setTarget(p.row)}><EditIcon fontSize="small" /></IconButton></Tooltip>
          <Tooltip title={t('md.delete')}><IconButton size="small" aria-label={t('md.delete')} onClick={() => setToDelete(p.row)}><DeleteIcon fontSize="small" /></IconButton></Tooltip>
        </Box>
      ),
    },
  ], [t])

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
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>{t('admin.wsGroups.title')}</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setTarget('new')}>{t('admin.wsGroups.add')}</Button>
      </Stack>
      <Typography color="text.secondary" sx={{ mb: 2 }}>{t('admin.wsGroups.intro')}</Typography>
      <TextField size="small" label={t('md.search')} value={search} onChange={(e) => setSearch(e.target.value)} sx={{ mb: 2, minWidth: { xs: '100%', sm: 260 } }} />
      {list.isError && <Alert severity="error" sx={{ mb: 2 }}>{errorText(t, list.error)}</Alert>}
      <Paper variant="outlined">
        <DataGrid<WorkstationGroup>
          rows={list.data?.items ?? []} columns={columns} loading={list.isFetching} rowCount={list.data?.total ?? 0}
          paginationMode="server" paginationModel={paging} onPaginationModelChange={setPaging} pageSizeOptions={[25, 50, 100]}
          disableRowSelectionOnClick autoHeight onRowDoubleClick={(p) => setTarget(p.row)} disableColumnSorting
          localeText={{ noRowsLabel: t('md.noRows') }} sx={{ border: 0 }}
        />
      </Paper>
      <Drawer anchor="right" open={target !== null} onClose={() => setTarget(null)} slotProps={{ paper: { sx: { width: { xs: '100%', sm: 520 } } } }}>
        {target !== null && (
          <GroupForm key={target === 'new' ? 'new' : target.id} group={target === 'new' ? null : target} permissions={permissions.data ?? []}
            onClose={() => setTarget(null)} onSaved={() => { setTarget(null); setNotice({ text: t('admin.wsGroups.saved'), severity: 'success' }) }} />
        )}
      </Drawer>
      <Dialog open={toDelete !== null} onClose={() => setToDelete(null)}>
        <DialogTitle>{t('md.deleteTitle')}</DialogTitle>
        <DialogContent><DialogContentText>{t('md.deleteText', { name: toDelete?.code ?? '' })}</DialogContentText></DialogContent>
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

function GroupForm({ group: initial, permissions, onClose, onSaved }: {
  group: WorkstationGroup | null; permissions: Permission[]; onClose: () => void; onSaved: () => void
}) {
  const { t } = useTranslation()
  const save = useSaveWorkstationGroup()
  const [group, setGroup] = useState<WorkstationGroup | null>(initial)
  const [code, setCode] = useState(initial?.code ?? '')
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [enabled, setEnabled] = useState(initial?.enabled ?? true)
  const [allowed, setAllowed] = useState<Set<string>>(() => new Set(initial?.allowedPermissions ?? []))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<{ text: string; stale: boolean } | null>(null)
  const creating = group === null
  const modules = [...new Set(permissions.map((p) => p.module))]

  function failed(e: unknown) {
    if (e instanceof ApiError) {
      setErrors(e.fieldErrors)
      setMessage({ text: errorText(t, e), stale: e.isStale })
    } else setMessage({ text: errorText(t, e), stale: false })
  }

  function submit() {
    const found: Record<string, string> = {}
    if (creating && code.trim().length < 2) found.code = t('admin.groups.codeHint')
    if (name.trim() === '') found.name = t('md.error.required')
    setErrors(found)
    if (Object.keys(found).length > 0) return
    setMessage(null)
    save.mutate({
      id: group?.id,
      body: { code: code.trim(), name: name.trim(), description: description.trim() === '' ? null : description.trim(),
        enabled, allowedPermissions: [...allowed], version: group?.version },
    }, { onSuccess: onSaved, onError: failed })
  }

  async function reload() {
    if (!group) return
    try {
      const fresh = await fetchWorkstationGroup(group.id)
      setGroup(fresh); setName(fresh.name); setDescription(fresh.description ?? ''); setEnabled(fresh.enabled)
      setAllowed(new Set(fresh.allowedPermissions)); setErrors({}); setMessage(null)
    } catch (e) { failed(e) }
  }

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', px: 2.5, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h6" sx={{ flexGrow: 1 }}>{creating ? t('admin.wsGroups.new') : t('admin.wsGroups.edit')}</Typography>
        <IconButton aria-label={t('md.close')} onClick={onClose}><CloseIcon /></IconButton>
      </Box>
      <Stack spacing={2.5} sx={{ p: 2.5, overflowY: 'auto', flex: 1 }} component="form" noValidate onSubmit={(e) => { e.preventDefault(); submit() }}>
        {message && (
          <Alert severity="error" action={message.stale ? <Button color="inherit" size="small" onClick={() => void reload()}>{t('md.reload')}</Button> : undefined}>
            {message.text}
          </Alert>
        )}
        <TextField label={t('md.fields.code')} required={creating} disabled={!creating} value={code} onChange={(e) => setCode(e.target.value.toUpperCase())}
          error={Boolean(errors.code)} helperText={errors.code ?? (creating ? t('admin.groups.codeHint') : t('admin.groups.codeFixed'))} slotProps={{ htmlInput: { maxLength: 50 } }} />
        <TextField label={t('md.fields.name')} required value={name} onChange={(e) => setName(e.target.value)} error={Boolean(errors.name)} helperText={errors.name}
          slotProps={{ htmlInput: { maxLength: 100 } }} />
        <TextField label={t('admin.roles.description')} value={description} onChange={(e) => setDescription(e.target.value)} multiline minRows={2}
          slotProps={{ htmlInput: { maxLength: 255 } }} />
        <Box>
          <FormControlLabel control={<Switch checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />} label={t('admin.wsGroups.enabledLabel')} />
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{t('admin.wsGroups.enabledHelp')}</Typography>
        </Box>

        <Box>
          <Typography sx={{ fontWeight: 600 }}>{t('admin.wsGroups.allowedTitle')}</Typography>
          <Alert severity={allowed.size === 0 ? 'info' : 'warning'} sx={{ my: 1 }}>
            {allowed.size === 0 ? t('admin.wsGroups.allowedNone') : t('admin.wsGroups.allowedSome', { count: allowed.size })}
          </Alert>
          {errors.allowedPermissions && <Typography variant="caption" color="error">{errors.allowedPermissions}</Typography>}
          {modules.map((m) => (
            <Box key={m} sx={{ mb: 1 }}>
              <Typography variant="overline" color="text.secondary">{t(`admin.modules.${m}`, { defaultValue: m })}</Typography>
              {permissions.filter((p) => p.module === m).map((p) => (
                <FormControlLabel key={p.code} sx={{ display: 'flex', alignItems: 'flex-start' }}
                  control={<Checkbox size="small" checked={allowed.has(p.code)} onChange={(e) => setAllowed((prev) => {
                    const next = new Set(prev)
                    if (e.target.checked) next.add(p.code)
                    else next.delete(p.code)
                    return next
                  })} />}
                  label={<Box><Typography sx={{ fontFamily: 'monospace', fontSize: 13 }}>{p.code}</Typography>
                    <Typography variant="caption" color="text.secondary">{p.description}</Typography></Box>} />
              ))}
            </Box>
          ))}
          <Typography variant="caption" color="text.secondary">{t('admin.wsGroups.superKeeps')}</Typography>
        </Box>
      </Stack>
      <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'flex-end', px: 2.5, py: 1.5, borderTop: 1, borderColor: 'divider' }}>
        <Button onClick={onClose} color="secondary" variant="text">{t('md.cancel')}</Button>
        <Button variant="contained" onClick={submit} disabled={save.isPending}>{save.isPending ? t('md.saving') : t('md.save')}</Button>
      </Box>
    </>
  )
}
