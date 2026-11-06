import AddIcon from '@mui/icons-material/Add'
import CloseIcon from '@mui/icons-material/Close'
import DeleteIcon from '@mui/icons-material/DeleteOutlined'
import EditIcon from '@mui/icons-material/EditOutlined'
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, Divider, Drawer,
  FormControlLabel, IconButton, MenuItem, Paper, Snackbar, Stack, Switch, TextField, Tooltip, Typography,
} from '@mui/material'
import { DataGrid, type GridColDef, type GridPaginationModel, type GridRenderCellParams } from '@mui/x-data-grid'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  fetchWorkstation, useDeleteWorkstation, useRegistration, useSaveWorkstation, useWorkstationGroups, useWorkstations,
  WORKSTATION_TYPES, type Workstation, type WorkstationType,
} from '../../api/admin'
import { useMe } from '../../api/auth'
import { ApiError } from '../../api/client'
import { formatDateTime } from '../../format'
import { errorText } from '../masterdata/errorText'
import RefField from '../masterdata/RefField'

/** Workstations: the PCs, handhelds, HMIs and kiosks, and which browser is which one. */
export default function WorkstationsPage() {
  const { t } = useTranslation()
  const remove = useDeleteWorkstation()
  const groups = useWorkstationGroups({ size: 200 })
  const [paging, setPaging] = useState<GridPaginationModel>({ page: 0, pageSize: 25 })
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [type, setType] = useState('')
  const [groupId, setGroupId] = useState('')
  const [target, setTarget] = useState<Workstation | 'new' | null>(null)
  const [toDelete, setToDelete] = useState<Workstation | null>(null)
  const [notice, setNotice] = useState<{ text: string; severity: 'success' | 'error' } | null>(null)

  useEffect(() => {
    const id = setTimeout(() => { setQ(search); setPaging((p) => ({ ...p, page: 0 })) }, 300)
    return () => clearTimeout(id)
  }, [search])

  const list = useWorkstations({ q, type, groupId, page: paging.page, size: paging.pageSize })
  const columns = useMemo<GridColDef<Workstation>[]>(() => [
    { field: 'code', headerName: t('md.fields.code'), width: 160 },
    { field: 'name', headerName: t('md.fields.name'), flex: 1, minWidth: 160 },
    { field: 'type', headerName: t('admin.workstations.type'), width: 120, valueFormatter: (v: string) => t(`admin.workstations.types.${v}`, { defaultValue: v }) },
    { field: 'locationCode', headerName: t('admin.workstations.location'), width: 150 },
    { field: 'workstationGroupCode', headerName: t('admin.workstations.group'), width: 150 },
    { field: 'hostName', headerName: t('admin.workstations.hostName'), width: 160 },
    {
      field: 'registered', headerName: t('admin.workstations.registration'), width: 200, sortable: false,
      renderCell: (p: GridRenderCellParams<Workstation>) => (
        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', height: '100%' }}>
          {p.row.registered
            ? <Chip size="small" color="success" variant="outlined" label={t('admin.workstations.registered')} />
            : <Chip size="small" variant="outlined" label={t('admin.workstations.notRegistered')} />}
          {!p.row.enabled && <Chip size="small" label={t('admin.users.disabled')} />}
        </Stack>
      ),
    },
    {
      field: '__actions', headerName: '', width: 100, sortable: false, disableColumnMenu: true,
      renderCell: (p: GridRenderCellParams<Workstation>) => (
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
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>{t('admin.workstations.title')}</Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setTarget('new')}>{t('admin.workstations.add')}</Button>
      </Stack>
      <Typography color="text.secondary" sx={{ mb: 2 }}>{t('admin.workstations.intro')}</Typography>
      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1.5 }}>
        <TextField size="small" label={t('md.search')} value={search} onChange={(e) => setSearch(e.target.value)} sx={{ minWidth: { xs: '100%', sm: 260 } }} />
        <TextField select size="small" label={t('admin.workstations.type')} value={type} sx={{ minWidth: 160 }}
          onChange={(e) => { setType(e.target.value); setPaging((p) => ({ ...p, page: 0 })) }}>
          <MenuItem value="">{t('md.all')}</MenuItem>
          {WORKSTATION_TYPES.map((x) => <MenuItem key={x} value={x}>{t(`admin.workstations.types.${x}`)}</MenuItem>)}
        </TextField>
        <TextField select size="small" label={t('admin.workstations.group')} value={groupId} sx={{ minWidth: 180 }}
          onChange={(e) => { setGroupId(e.target.value); setPaging((p) => ({ ...p, page: 0 })) }}>
          <MenuItem value="">{t('md.all')}</MenuItem>
          {(groups.data?.items ?? []).map((g) => <MenuItem key={g.id} value={String(g.id)}>{g.code}</MenuItem>)}
        </TextField>
      </Stack>
      {list.isError && <Alert severity="error" sx={{ mb: 2 }}>{errorText(t, list.error)}</Alert>}
      <Paper variant="outlined">
        <DataGrid<Workstation>
          rows={list.data?.items ?? []} columns={columns} loading={list.isFetching} rowCount={list.data?.total ?? 0}
          paginationMode="server" paginationModel={paging} onPaginationModelChange={setPaging} pageSizeOptions={[25, 50, 100]}
          disableRowSelectionOnClick autoHeight disableColumnSorting onRowDoubleClick={(p) => setTarget(p.row)}
          localeText={{ noRowsLabel: t('admin.workstations.none') }} sx={{ border: 0 }}
        />
      </Paper>
      <Drawer anchor="right" open={target !== null} onClose={() => setTarget(null)} slotProps={{ paper: { sx: { width: { xs: '100%', sm: 520 } } } }}>
        {target !== null && (
          <WorkstationForm key={target === 'new' ? 'new' : target.id} workstation={target === 'new' ? null : target}
            groups={(groups.data?.items ?? []).map((g) => ({ id: g.id, code: g.code, enabled: g.enabled }))}
            onClose={() => setTarget(null)} onNotice={(text, severity) => setNotice({ text, severity })}
            onSaved={() => { setTarget(null); setNotice({ text: t('admin.workstations.saved'), severity: 'success' }) }} />
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
      <Snackbar open={notice !== null} autoHideDuration={7000} onClose={() => setNotice(null)}>
        {notice ? <Alert severity={notice.severity} onClose={() => setNotice(null)}>{notice.text}</Alert> : undefined}
      </Snackbar>
    </>
  )
}

function WorkstationForm({ workstation: initial, groups, onClose, onSaved, onNotice }: {
  workstation: Workstation | null; groups: { id: number; code: string; enabled: boolean }[]; onClose: () => void;
  onSaved: () => void; onNotice: (text: string, severity: 'success' | 'error') => void
}) {
  const { t } = useTranslation()
  const me = useMe()
  const save = useSaveWorkstation()
  const { register, revoke } = useRegistration()

  const [ws, setWs] = useState<Workstation | null>(initial)
  const [code, setCode] = useState(initial?.code ?? '')
  const [name, setName] = useState(initial?.name ?? '')
  const [type, setType] = useState<WorkstationType>(initial?.type ?? 'PC')
  const [locationId, setLocationId] = useState(initial?.locationId ? String(initial.locationId) : '')
  const [locationLabel, setLocationLabel] = useState(initial?.locationCode ?? '')
  const [groupId, setGroupId] = useState(initial?.workstationGroupId ? String(initial.workstationGroupId) : '')
  const [hostName, setHostName] = useState(initial?.hostName ?? '')
  const [ipAddress, setIpAddress] = useState(initial?.ipAddress ?? '')
  const [checkIp, setCheckIp] = useState(initial?.checkIp ?? false)
  const [printer, setPrinter] = useState(initial?.defaultPrinter ?? '')
  const [enabled, setEnabled] = useState(initial?.enabled ?? true)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<{ text: string; stale: boolean } | null>(null)
  const [confirm, setConfirm] = useState<'register' | 'revoke' | null>(null)
  const creating = ws === null
  // this very browser, as the server sees it
  const thisBrowser = me.data?.workstation

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
    if (checkIp && ipAddress.trim() === '') found.ipAddress = t('admin.workstations.ipNeeded')
    setErrors(found)
    if (Object.keys(found).length > 0) return
    setMessage(null)
    save.mutate({
      id: ws?.id,
      body: {
        code: code.trim(), name: name.trim(), type, locationId: locationId ? Number(locationId) : null,
        workstationGroupId: groupId ? Number(groupId) : null, hostName: hostName.trim() || null,
        ipAddress: ipAddress.trim() || null, checkIp, defaultPrinter: printer.trim() || null, enabled, version: ws?.version,
      },
    }, { onSuccess: onSaved, onError: failed })
  }

  async function reload() {
    if (!ws) return
    try {
      const f = await fetchWorkstation(ws.id)
      setWs(f); setName(f.name); setType(f.type); setLocationId(f.locationId ? String(f.locationId) : ''); setLocationLabel(f.locationCode ?? '')
      setGroupId(f.workstationGroupId ? String(f.workstationGroupId) : ''); setHostName(f.hostName ?? ''); setIpAddress(f.ipAddress ?? '')
      setCheckIp(f.checkIp); setPrinter(f.defaultPrinter ?? ''); setEnabled(f.enabled); setErrors({}); setMessage(null)
    } catch (e) { failed(e) }
  }

  function doRegistration() {
    if (!ws || !confirm) return
    const action = confirm
    const mutation = action === 'register' ? register : revoke
    mutation.mutate(ws.id, {
      onSuccess: (fresh) => {
        setWs(fresh); setConfirm(null)
        onNotice(t(action === 'register' ? 'admin.workstations.registeredNow' : 'admin.workstations.revokedNow', { code: fresh.code }), 'success')
      },
      onError: (e) => { setConfirm(null); failed(e) },
    })
  }

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', px: 2.5, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h6" sx={{ flexGrow: 1 }}>{creating ? t('admin.workstations.new') : t('admin.workstations.edit')}</Typography>
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
        <TextField select label={t('admin.workstations.type')} required value={type} onChange={(e) => setType(e.target.value as WorkstationType)}
          error={Boolean(errors.type)} helperText={errors.type}>
          {WORKSTATION_TYPES.map((x) => <MenuItem key={x} value={x}>{t(`admin.workstations.types.${x}`)}</MenuItem>)}
        </TextField>
        <RefField label={t('admin.workstations.location')} refPath="/api/masterdata/locations" value={locationId} valueLabel={locationLabel}
          error={Boolean(errors.locationId)} helperText={errors.locationId ?? t('admin.workstations.locationHelp')}
          onChange={(id, label) => { setLocationId(id); setLocationLabel(label) }} />
        <TextField select label={t('admin.workstations.group')} value={groupId} onChange={(e) => setGroupId(e.target.value)}
          error={Boolean(errors.workstationGroupId)} helperText={errors.workstationGroupId ?? t('admin.workstations.groupHelp')}>
          <MenuItem value="">{t('admin.workstations.noGroup')}</MenuItem>
          {groups.map((g) => <MenuItem key={g.id} value={String(g.id)}>{g.code}{g.enabled ? '' : ` (${t('admin.users.disabled')})`}</MenuItem>)}
        </TextField>
        <TextField label={t('admin.workstations.hostName')} value={hostName} onChange={(e) => setHostName(e.target.value)} slotProps={{ htmlInput: { maxLength: 255 } }} />
        <TextField label={t('admin.workstations.ipAddress')} value={ipAddress} onChange={(e) => setIpAddress(e.target.value)}
          error={Boolean(errors.ipAddress)} helperText={errors.ipAddress} slotProps={{ htmlInput: { maxLength: 64 } }} />
        <Box>
          <FormControlLabel control={<Switch checked={checkIp} onChange={(e) => setCheckIp(e.target.checked)} />} label={t('admin.workstations.checkIp')} />
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{t('admin.workstations.checkIpHelp')}</Typography>
        </Box>
        <TextField label={t('admin.workstations.printer')} value={printer} onChange={(e) => setPrinter(e.target.value)}
          helperText={t('admin.workstations.printerHelp')} slotProps={{ htmlInput: { maxLength: 255 } }} />
        <Box>
          <FormControlLabel control={<Switch checked={enabled} onChange={(e) => setEnabled(e.target.checked)} />} label={t('admin.workstations.enabledLabel')} />
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{t('admin.workstations.enabledHelp')}</Typography>
        </Box>

        {ws ? (
          <>
            <Divider />
            <Box>
              <Typography sx={{ fontWeight: 600, mb: 1 }}>{t('admin.workstations.registration')}</Typography>
              {ws.registered
                ? <Alert severity="success">{t('admin.workstations.registeredInfo', { by: ws.registeredBy ?? '', at: formatDateTime(ws.registeredAt) })}</Alert>
                : <Alert severity="info">{t('admin.workstations.notRegisteredInfo')}</Alert>}
              {thisBrowser && thisBrowser.mode !== 'OFFICE' && thisBrowser.code && thisBrowser.code !== ws.code && (
                <Alert severity="warning" sx={{ mt: 1 }}>{t('admin.workstations.thisBrowserIs', { code: thisBrowser.code })}</Alert>
              )}
              <Stack direction="row" spacing={1} sx={{ mt: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
                <Button variant="outlined" color="secondary" onClick={() => setConfirm('register')}>{t('admin.workstations.registerThis')}</Button>
                {ws.registered && <Button variant="text" color="secondary" onClick={() => setConfirm('revoke')}>{t('admin.workstations.revoke')}</Button>}
              </Stack>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>{t('admin.workstations.registerHelp')}</Typography>
            </Box>
          </>
        ) : <Typography variant="caption" color="text.secondary">{t('admin.workstations.saveFirst')}</Typography>}
      </Stack>
      <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'flex-end', px: 2.5, py: 1.5, borderTop: 1, borderColor: 'divider' }}>
        <Button onClick={onClose} color="secondary" variant="text">{t('md.cancel')}</Button>
        <Button variant="contained" onClick={submit} disabled={save.isPending}>{save.isPending ? t('md.saving') : t('md.save')}</Button>
      </Box>

      <Dialog open={confirm !== null} onClose={() => setConfirm(null)}>
        <DialogTitle>{confirm === 'register' ? t('admin.workstations.registerTitle') : t('admin.workstations.revokeTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {confirm === 'register' ? t('admin.workstations.registerText', { code: ws?.code ?? '' }) : t('admin.workstations.revokeText', { code: ws?.code ?? '' })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="secondary" onClick={() => setConfirm(null)}>{t('md.cancel')}</Button>
          <Button variant="contained" disabled={register.isPending || revoke.isPending} onClick={doRegistration}>
            {confirm === 'register' ? t('admin.workstations.registerThis') : t('admin.workstations.revoke')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
