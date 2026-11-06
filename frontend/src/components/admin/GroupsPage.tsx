import AddIcon from '@mui/icons-material/Add'
import CloseIcon from '@mui/icons-material/Close'
import DeleteIcon from '@mui/icons-material/DeleteOutlined'
import EditIcon from '@mui/icons-material/EditOutlined'
import {
  Alert, Autocomplete, Box, Button, Checkbox, Chip, Dialog, DialogActions, DialogContent, DialogContentText,
  DialogTitle, Drawer, FormControlLabel, FormGroup, IconButton, MenuItem, Paper, Snackbar, Stack, Switch, TextField,
  Tooltip, Typography,
} from '@mui/material'
import { DataGrid, type GridColDef, type GridPaginationModel, type GridRenderCellParams, type GridSortModel } from '@mui/x-data-grid'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  fetchGroup, useAdminGroups, useAdminUsers, useDeleteGroup, useRoles, useSaveGroup,
  type AdminGroup, type GroupMember, type RoleInfo,
} from '../../api/admin'
import { useMe } from '../../api/auth'
import { ApiError } from '../../api/client'
import { errorText } from '../masterdata/errorText'

/** User groups: some roles and some members; members inherit the roles. */
export default function GroupsPage() {
  const { t } = useTranslation()
  const roles = useRoles()
  const remove = useDeleteGroup()

  const [paging, setPaging] = useState<GridPaginationModel>({ page: 0, pageSize: 25 })
  const [sortModel, setSortModel] = useState<GridSortModel>([])
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [enabled, setEnabled] = useState('')
  const [target, setTarget] = useState<AdminGroup | 'new' | null>(null)
  const [toDelete, setToDelete] = useState<AdminGroup | null>(null)
  const [notice, setNotice] = useState<{ text: string; severity: 'success' | 'error' } | null>(null)

  useEffect(() => {
    const id = setTimeout(() => { setQ(search); setPaging((p) => ({ ...p, page: 0 })) }, 300)
    return () => clearTimeout(id)
  }, [search])

  const sort = sortModel.length > 0 ? `${sortModel[0].field},${sortModel[0].sort}` : 'code,asc'
  const list = useAdminGroups({ q, enabled, page: paging.page, size: paging.pageSize, sort })

  const columns = useMemo<GridColDef<AdminGroup>[]>(() => [
    { field: 'code', headerName: t('md.fields.code'), width: 180, sortable: true },
    { field: 'name', headerName: t('md.fields.name'), flex: 1, minWidth: 180, sortable: true },
    {
      field: 'roles', headerName: t('admin.users.roles'), width: 260, sortable: false,
      renderCell: (p: GridRenderCellParams<AdminGroup>) => (
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', height: '100%' }}>
          {p.row.roles.map((r) => <Chip key={r} size="small" label={r} variant="outlined" />)}
        </Stack>
      ),
    },
    { field: 'memberCount', headerName: t('admin.groups.members'), width: 110, type: 'number', sortable: false },
    {
      field: 'enabled', headerName: t('admin.users.status'), width: 130, sortable: true,
      renderCell: (p: GridRenderCellParams<AdminGroup>) => p.row.enabled
        ? <Chip size="small" color="success" variant="outlined" label={t('admin.users.active')} />
        : <Chip size="small" label={t('admin.users.disabled')} />,
    },
    {
      field: '__actions', headerName: '', width: 100, sortable: false, disableColumnMenu: true,
      renderCell: (p: GridRenderCellParams<AdminGroup>) => (
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
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>{t('admin.groups.title')}</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setTarget('new')}>{t('admin.groups.add')}</Button>
      </Stack>
      <Typography color="text.secondary" sx={{ mb: 2 }}>{t('admin.groups.intro')}</Typography>
      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1.5 }}>
        <TextField size="small" label={t('md.search')} value={search} onChange={(e) => setSearch(e.target.value)} sx={{ minWidth: { xs: '100%', sm: 260 } }} />
        <TextField select size="small" label={t('admin.users.status')} value={enabled} sx={{ minWidth: 160 }}
          onChange={(e) => { setEnabled(e.target.value); setPaging((p) => ({ ...p, page: 0 })) }}>
          <MenuItem value="">{t('md.all')}</MenuItem>
          <MenuItem value="true">{t('admin.users.active')}</MenuItem>
          <MenuItem value="false">{t('admin.users.disabled')}</MenuItem>
        </TextField>
      </Stack>
      {list.isError && <Alert severity="error" sx={{ mb: 2 }}>{errorText(t, list.error)}</Alert>}
      <Paper variant="outlined">
        <DataGrid<AdminGroup>
          rows={list.data?.items ?? []} columns={columns} loading={list.isFetching} rowCount={list.data?.total ?? 0}
          paginationMode="server" sortingMode="server" paginationModel={paging} onPaginationModelChange={setPaging}
          sortModel={sortModel} onSortModelChange={setSortModel} pageSizeOptions={[25, 50, 100]}
          disableRowSelectionOnClick autoHeight onRowDoubleClick={(p) => setTarget(p.row)}
          localeText={{ noRowsLabel: t('admin.groups.none') }} sx={{ border: 0 }}
        />
      </Paper>

      <Drawer anchor="right" open={target !== null} onClose={() => setTarget(null)} slotProps={{ paper: { sx: { width: { xs: '100%', sm: 520 } } } }}>
        {target !== null && (
          <GroupForm key={target === 'new' ? 'new' : target.id} group={target === 'new' ? null : target}
            roles={roles.data ?? []} onClose={() => setTarget(null)}
            onSaved={() => { setTarget(null); setNotice({ text: t('admin.groups.saved'), severity: 'success' }) }} />
        )}
      </Drawer>

      <Dialog open={toDelete !== null} onClose={() => setToDelete(null)}>
        <DialogTitle>{t('md.deleteTitle')}</DialogTitle>
        <DialogContent><DialogContentText>{t('admin.groups.deleteText', { name: toDelete?.code ?? '' })}</DialogContentText></DialogContent>
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

function GroupForm({ group: initial, roles, onClose, onSaved }: {
  group: AdminGroup | null; roles: RoleInfo[]; onClose: () => void; onSaved: () => void
}) {
  const { t } = useTranslation()
  const me = useMe()
  const save = useSaveGroup()
  const iAmSuper = me.data?.roles.includes('SUPER_ADMIN') ?? false

  const [group, setGroup] = useState<AdminGroup | null>(initial)
  const [code, setCode] = useState(initial?.code ?? '')
  const [name, setName] = useState(initial?.name ?? '')
  const [description, setDescription] = useState(initial?.description ?? '')
  const [externalRef, setExternalRef] = useState(initial?.externalRef ?? '')
  const [enabled, setEnabled] = useState(initial?.enabled ?? true)
  const [selected, setSelected] = useState<Set<string>>(() => new Set(initial?.roles ?? []))
  const [members, setMembers] = useState<GroupMember[]>(initial?.members ?? [])
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<{ text: string; stale: boolean } | null>(null)
  const [loaded, setLoaded] = useState(initial === null) // the members come with the single-group read
  const [memberText, setMemberText] = useState('')
  const [memberQuery, setMemberQuery] = useState('')

  const creating = group === null
  const holdsSuper = group?.roles.includes('SUPER_ADMIN') ?? false
  const readOnly = holdsSuper && !iAmSuper

  // the list rows do not carry the members: read the whole group once when the form opens
  useEffect(() => {
    if (!initial) return
    let cancelled = false
    fetchGroup(initial.id).then((g) => {
      if (cancelled) return
      setGroup(g)
      setMembers(g.members ?? [])
      setLoaded(true)
    }).catch((e) => { if (!cancelled) setMessage({ text: errorText(t, e), stale: false }) })
    return () => { cancelled = true }
  }, [initial, t])

  useEffect(() => {
    const id = setTimeout(() => setMemberQuery(memberText), 250)
    return () => clearTimeout(id)
  }, [memberText])
  const found = useAdminUsers({ q: memberQuery, size: 20, sort: 'username,asc' })
  const options: GroupMember[] = (found.data?.items ?? []).map((u) => ({ id: u.id, username: u.username, displayName: u.displayName }))

  function failed(e: unknown) {
    if (e instanceof ApiError) {
      setErrors(e.fieldErrors)
      setMessage({ text: errorText(t, e), stale: e.isStale })
    } else setMessage({ text: errorText(t, e), stale: false })
  }

  function submit() {
    const found2: Record<string, string> = {}
    if (creating && code.trim().length < 2) found2.code = t('admin.groups.codeHint')
    if (name.trim() === '') found2.name = t('md.error.required')
    setErrors(found2)
    if (Object.keys(found2).length > 0) return
    setMessage(null)
    save.mutate({
      id: group?.id,
      body: {
        code: code.trim(), name: name.trim(), description: description.trim() === '' ? null : description.trim(),
        externalRef: externalRef.trim() === '' ? null : externalRef.trim(), enabled, roles: [...selected],
        memberIds: members.map((m) => m.id), version: group?.version,
      },
    }, { onSuccess: onSaved, onError: failed })
  }

  async function reload() {
    if (!group) return
    try {
      const fresh = await fetchGroup(group.id)
      setGroup(fresh); setName(fresh.name); setDescription(fresh.description ?? ''); setExternalRef(fresh.externalRef ?? '')
      setEnabled(fresh.enabled); setSelected(new Set(fresh.roles)); setMembers(fresh.members ?? [])
      setErrors({}); setMessage(null)
    } catch (e) { failed(e) }
  }

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', px: 2.5, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h6" sx={{ flexGrow: 1 }}>{creating ? t('admin.groups.new') : readOnly ? t('admin.groups.view') : t('admin.groups.edit')}</Typography>
        <IconButton aria-label={t('md.close')} onClick={onClose}><CloseIcon /></IconButton>
      </Box>
      <Stack spacing={2.5} sx={{ p: 2.5, overflowY: 'auto', flex: 1 }} component="form" noValidate onSubmit={(e) => { e.preventDefault(); if (!readOnly) submit() }}>
        {message && (
          <Alert severity="error" action={message.stale ? <Button color="inherit" size="small" onClick={() => void reload()}>{t('md.reload')}</Button> : undefined}>
            {message.text}
          </Alert>
        )}
        {readOnly && <Alert severity="info">{t('admin.groups.superOnly')}</Alert>}
        <TextField label={t('md.fields.code')} required={creating} disabled={!creating} value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())} error={Boolean(errors.code)}
          helperText={errors.code ?? (creating ? t('admin.groups.codeHint') : t('admin.groups.codeFixed'))} slotProps={{ htmlInput: { maxLength: 50 } }} />
        <TextField label={t('md.fields.name')} required disabled={readOnly} value={name} onChange={(e) => setName(e.target.value)}
          error={Boolean(errors.name)} helperText={errors.name} slotProps={{ htmlInput: { maxLength: 100 } }} />
        <TextField label={t('admin.roles.description')} disabled={readOnly} value={description} onChange={(e) => setDescription(e.target.value)}
          multiline minRows={2} slotProps={{ htmlInput: { maxLength: 255 } }} />
        <TextField label={t('admin.groups.externalRef')} disabled={readOnly} value={externalRef} onChange={(e) => setExternalRef(e.target.value)}
          helperText={t('admin.groups.externalRefHelp')} slotProps={{ htmlInput: { maxLength: 255 } }} />
        <Box>
          <FormControlLabel control={<Switch checked={enabled} onChange={(e) => setEnabled(e.target.checked)} disabled={readOnly} />} label={t('admin.groups.enabledLabel')} />
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{t('admin.groups.enabledHelp')}</Typography>
        </Box>

        <Box>
          <Typography sx={{ fontWeight: 600 }}>{t('admin.users.roles')}</Typography>
          <Typography variant="caption" color="text.secondary">{t('admin.groups.rolesHelp')}</Typography>
          <FormGroup>
            {roles.map((r) => (
              <FormControlLabel key={r.code} disabled={readOnly || (r.code === 'SUPER_ADMIN' && !iAmSuper)}
                control={<Checkbox checked={selected.has(r.code)} onChange={(e) => setSelected((prev) => {
                  const next = new Set(prev)
                  if (e.target.checked) next.add(r.code)
                  else next.delete(r.code)
                  return next
                })} />}
                label={<Box><Typography>{r.code} - {r.name}</Typography>{r.description && <Typography variant="caption" color="text.secondary">{r.description}</Typography>}</Box>} />
            ))}
          </FormGroup>
          {errors.roles && <Typography variant="caption" color="error">{errors.roles}</Typography>}
        </Box>

        <Box>
          <Typography sx={{ fontWeight: 600, mb: 1 }}>{t('admin.groups.members')} ({members.length})</Typography>
          <Autocomplete<GroupMember, true>
            multiple disabled={readOnly || !loaded} options={options} value={members} filterOptions={(all) => all}
            getOptionLabel={(m) => `${m.username} - ${m.displayName}`} isOptionEqualToValue={(a, b) => a.id === b.id}
            loading={found.isFetching} inputValue={memberText} onInputChange={(_, v, reason) => { if (reason === 'input') setMemberText(v); if (reason === 'reset' || reason === 'clear') setMemberText('') }}
            onChange={(_, v) => setMembers(v)}
            renderInput={(params) => (
              <TextField {...params} label={t('admin.groups.addMembers')} error={Boolean(errors.memberIds)} helperText={errors.memberIds ?? t('admin.groups.membersHelp')} />
            )}
          />
        </Box>
      </Stack>
      <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'flex-end', px: 2.5, py: 1.5, borderTop: 1, borderColor: 'divider' }}>
        <Button onClick={onClose} color="secondary" variant="text">{readOnly ? t('md.close') : t('md.cancel')}</Button>
        {!readOnly && <Button variant="contained" onClick={submit} disabled={save.isPending || !loaded}>{save.isPending ? t('md.saving') : t('md.save')}</Button>}
      </Box>
    </>
  )
}
