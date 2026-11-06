import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/DeleteOutlined'
import EditIcon from '@mui/icons-material/EditOutlined'
import LockIcon from '@mui/icons-material/LockOutlined'
import {
  Alert, Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, IconButton,
  Paper, Snackbar, Stack, TextField, Tooltip, Typography,
} from '@mui/material'
import { DataGrid, type GridColDef, type GridRenderCellParams } from '@mui/x-data-grid'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useMe } from '../../api/auth'
import { api, ApiError } from '../../api/client'
import { MD, type Page } from '../../api/masterdata'
import { errorText } from './errorText'

interface EquipmentType {
  id: number
  code: string
  name: string
  seeded: boolean
}

const PATH = `${MD}/equipment-types`

/**
 * The shared list of equipment types. Everybody with master-data access can read it; only SUPER_ADMIN can add,
 * rename or delete custom types. The built-in types (marked with a lock) are fixed.
 */
export default function EquipmentTypesPage() {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const me = useMe()
  const isSuper = me.data?.roles.includes('SUPER_ADMIN') ?? false

  const [search, setSearch] = useState('')
  const [target, setTarget] = useState<EquipmentType | 'new' | null>(null)
  const [toDelete, setToDelete] = useState<EquipmentType | null>(null)
  const [notice, setNotice] = useState<{ text: string; severity: 'success' | 'error' } | null>(null)

  const list = useQuery<Page<EquipmentType>>({
    queryKey: ['md', PATH, search],
    queryFn: () => api<Page<EquipmentType>>(`${PATH}?size=200&q=${encodeURIComponent(search)}`),
  })
  const remove = useMutation({
    mutationFn: (id: number) => api<void>(`${PATH}/${id}`, { method: 'DELETE' }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['md', PATH] })
      void qc.invalidateQueries({ queryKey: ['md-options', PATH] })
      setNotice({ text: t('md.deleted'), severity: 'success' })
      setToDelete(null)
    },
    onError: (e) => {
      setNotice({ text: errorText(t, e), severity: 'error' })
      setToDelete(null)
    },
  })

  const columns = useMemo<GridColDef<EquipmentType>[]>(
    () => [
      { field: 'code', headerName: t('md.fields.code'), width: 200 },
      { field: 'name', headerName: t('md.fields.name'), flex: 1, minWidth: 200 },
      {
        field: 'seeded', headerName: t('md.fields.kind'), width: 160, sortable: false,
        renderCell: (p: GridRenderCellParams<EquipmentType>) =>
          p.row.seeded
            ? <Chip size="small" icon={<LockIcon />} label={t('md.equipmentTypes.builtIn')} />
            : <Chip size="small" variant="outlined" label={t('md.equipmentTypes.custom')} />,
      },
      {
        field: '__actions', headerName: '', width: 100, sortable: false, disableColumnMenu: true,
        renderCell: (p: GridRenderCellParams<EquipmentType>) => {
          const editable = isSuper && !p.row.seeded
          return (
            <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
              <Tooltip title={p.row.seeded ? t('md.equipmentTypes.builtInHint') : editable ? t('md.edit') : t('md.view')}>
                <span>
                  <IconButton size="small" aria-label={t('md.edit')} onClick={() => setTarget(p.row)}>
                    {editable ? <EditIcon fontSize="small" /> : <LockIcon fontSize="small" />}
                  </IconButton>
                </span>
              </Tooltip>
              {editable && (
                <Tooltip title={t('md.delete')}>
                  <IconButton size="small" aria-label={t('md.delete')} onClick={() => setToDelete(p.row)}>
                    <DeleteIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
            </Box>
          )
        },
      },
    ],
    [t, isSuper],
  )

  return (
    <>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>{t('md.equipmentTypes.title')}</Typography>
        {isSuper && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setTarget('new')}>{t('md.equipmentTypes.add')}</Button>
        )}
      </Stack>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        {isSuper ? t('md.equipmentTypes.helpSuper') : t('md.equipmentTypes.helpOthers')}
      </Typography>
      <TextField size="small" label={t('md.search')} value={search} onChange={(e) => setSearch(e.target.value)} sx={{ mb: 2, minWidth: { xs: '100%', sm: 280 } }} />
      {list.isError && <Alert severity="error" sx={{ mb: 2 }}>{errorText(t, list.error)}</Alert>}
      <Paper variant="outlined">
        <DataGrid<EquipmentType>
          rows={list.data?.items ?? []} columns={columns} loading={list.isFetching} autoHeight disableRowSelectionOnClick
          pageSizeOptions={[25, 50, 100]} initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          localeText={{ noRowsLabel: t('md.noRows') }} sx={{ border: 0 }}
        />
      </Paper>

      {target !== null && (
        <TypeDialog
          key={target === 'new' ? 'new' : target.id}
          type={target === 'new' ? null : target}
          editable={isSuper && (target === 'new' || !target.seeded)}
          onClose={() => setTarget(null)}
        />
      )}

      <Dialog open={toDelete !== null} onClose={() => setToDelete(null)}>
        <DialogTitle>{t('md.deleteTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>{t('md.deleteText', { name: toDelete?.code ?? '' })}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="secondary" onClick={() => setToDelete(null)}>{t('md.cancel')}</Button>
          <Button variant="contained" disabled={remove.isPending} onClick={() => toDelete && remove.mutate(toDelete.id)}>{t('md.delete')}</Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={notice !== null} autoHideDuration={6000} onClose={() => setNotice(null)}>
        {notice ? <Alert severity={notice.severity} onClose={() => setNotice(null)}>{notice.text}</Alert> : undefined}
      </Snackbar>
    </>
  )
}

function TypeDialog({ type, editable, onClose }: { type: EquipmentType | null; editable: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const [code, setCode] = useState(type?.code ?? '')
  const [name, setName] = useState(type?.name ?? '')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () =>
      type
        ? api<EquipmentType>(`${PATH}/${type.id}`, { method: 'PUT', body: { code: code.trim(), name: name.trim() } })
        : api<EquipmentType>(PATH, { method: 'POST', body: { code: code.trim(), name: name.trim() } }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['md', PATH] })
      void qc.invalidateQueries({ queryKey: ['md-options', PATH] })
      onClose()
    },
    onError: (e) => {
      if (e instanceof ApiError) setErrors(e.fieldErrors)
      setMessage(errorText(t, e))
    },
  })

  function submit() {
    const found: Record<string, string> = {}
    if (code.trim() === '') found.code = t('md.error.required')
    if (name.trim() === '') found.name = t('md.error.required')
    setErrors(found)
    if (Object.keys(found).length === 0) {
      setMessage(null)
      save.mutate()
    }
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <DialogTitle>{type ? (editable ? t('md.equipmentTypes.edit') : t('md.equipmentTypes.view')) : t('md.equipmentTypes.new')}</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2.5} sx={{ pt: 0.5 }}>
          {message && <Alert severity="error">{message}</Alert>}
          {type?.seeded && <Alert severity="info">{t('md.equipmentTypes.builtInHint')}</Alert>}
          <TextField
            label={t('md.fields.code')} required={editable} disabled={!editable} value={code}
            onChange={(e) => setCode(e.target.value)} error={Boolean(errors.code)}
            helperText={errors.code ?? t('md.help.code')} slotProps={{ htmlInput: { maxLength: 30 } }}
          />
          <TextField
            label={t('md.fields.name')} required={editable} disabled={!editable} value={name}
            onChange={(e) => setName(e.target.value)} error={Boolean(errors.name)}
            helperText={errors.name} slotProps={{ htmlInput: { maxLength: 100 } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button color="secondary" onClick={onClose}>{editable ? t('md.cancel') : t('md.close')}</Button>
        {editable && <Button variant="contained" onClick={submit} disabled={save.isPending}>{save.isPending ? t('md.saving') : t('md.save')}</Button>}
      </DialogActions>
    </Dialog>
  )
}
