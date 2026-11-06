import CloseIcon from '@mui/icons-material/Close'
import {
  Alert, Box, Button, Drawer, IconButton, MenuItem, Paper, Stack, TextField, Typography,
} from '@mui/material'
import {
  DataGrid, type GridColDef, type GridPaginationModel, type GridSortModel,
} from '@mui/x-data-grid'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useAuditFilters, useAuditLog, type AuditRow } from '../../api/admin'
import { formatDateTime, prettyJson } from '../../format'
import { errorText } from '../masterdata/errorText'

/** "2026-11-06T19:05" from a datetime-local box is the viewer's local time; the server wants an instant. */
const toInstant = (local: string) => (local ? new Date(local).toISOString() : '')

/** The audit trail: who did what, when, with the values before and after. Read-only. */
export default function AuditPage() {
  const { t } = useTranslation()
  const filters = useAuditFilters()

  const [paging, setPaging] = useState<GridPaginationModel>({ page: 0, pageSize: 50 })
  const [sortModel, setSortModel] = useState<GridSortModel>([])
  const [actor, setActor] = useState('')
  const [action, setAction] = useState('')
  const [entityType, setEntityType] = useState('')
  const [entityId, setEntityId] = useState('')
  const [text, setText] = useState('')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [typed, setTyped] = useState({ actor: '', entityId: '', text: '' })
  const [selected, setSelected] = useState<AuditRow | null>(null)

  // wait a moment after typing before asking the server
  useEffect(() => {
    const id = setTimeout(() => {
      setActor(typed.actor)
      setEntityId(typed.entityId)
      setText(typed.text)
      setPaging((p) => ({ ...p, page: 0 }))
    }, 300)
    return () => clearTimeout(id)
  }, [typed])

  const dateProblem = from !== '' && to !== '' && new Date(from) > new Date(to)
  const sort = sortModel.length > 0 ? `${sortModel[0].field},${sortModel[0].sort}` : ''
  const list = useAuditLog({
    actor, action, entityType, entityId, q: text,
    from: toInstant(from), to: dateProblem ? '' : toInstant(to),
    page: paging.page, size: paging.pageSize, sort,
  })

  const columns = useMemo<GridColDef<AuditRow>[]>(
    () => [
      {
        field: 'occurredAt', headerName: t('admin.audit.time'), width: 180, sortable: true,
        valueFormatter: (v: string) => formatDateTime(v),
      },
      { field: 'actor', headerName: t('admin.audit.user'), width: 140, sortable: true },
      { field: 'action', headerName: t('admin.audit.action'), width: 200, sortable: true },
      { field: 'entityType', headerName: t('admin.audit.entity'), width: 150, sortable: true },
      { field: 'entityId', headerName: t('admin.audit.entityId'), width: 100, sortable: false },
      { field: 'detail', headerName: t('admin.audit.detail'), flex: 1, minWidth: 200, sortable: false },
    ],
    [t],
  )

  function clear() {
    setTyped({ actor: '', entityId: '', text: '' })
    setAction('')
    setEntityType('')
    setFrom('')
    setTo('')
  }

  return (
    <>
      <Typography variant="h4" component="h1" gutterBottom>{t('admin.audit.title')}</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>{t('admin.audit.intro')}</Typography>

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1.5 }}>
        <TextField size="small" label={t('admin.audit.user')} value={typed.actor} sx={{ width: 160 }}
          onChange={(e) => setTyped((p) => ({ ...p, actor: e.target.value }))} />
        <TextField select size="small" label={t('admin.audit.action')} value={action} sx={{ minWidth: 220 }}
          onChange={(e) => { setAction(e.target.value); setPaging((p) => ({ ...p, page: 0 })) }}>
          <MenuItem value="">{t('md.all')}</MenuItem>
          {(filters.data?.actions ?? []).map((a) => <MenuItem key={a} value={a}>{a}</MenuItem>)}
        </TextField>
        <TextField select size="small" label={t('admin.audit.entity')} value={entityType} sx={{ minWidth: 170 }}
          onChange={(e) => { setEntityType(e.target.value); setPaging((p) => ({ ...p, page: 0 })) }}>
          <MenuItem value="">{t('md.all')}</MenuItem>
          {(filters.data?.entityTypes ?? []).map((a) => <MenuItem key={a} value={a}>{a}</MenuItem>)}
        </TextField>
        <TextField size="small" label={t('admin.audit.entityId')} value={typed.entityId} sx={{ width: 110 }}
          onChange={(e) => setTyped((p) => ({ ...p, entityId: e.target.value }))} />
        <TextField size="small" label={t('admin.audit.text')} value={typed.text} sx={{ minWidth: 200 }}
          onChange={(e) => setTyped((p) => ({ ...p, text: e.target.value }))} />
        <TextField size="small" type="datetime-local" label={t('admin.audit.from')} value={from} error={dateProblem}
          slotProps={{ inputLabel: { shrink: true } }} onChange={(e) => { setFrom(e.target.value); setPaging((p) => ({ ...p, page: 0 })) }} />
        <TextField size="small" type="datetime-local" label={t('admin.audit.to')} value={to} error={dateProblem}
          helperText={dateProblem ? t('admin.audit.dateProblem') : undefined}
          slotProps={{ inputLabel: { shrink: true } }} onChange={(e) => { setTo(e.target.value); setPaging((p) => ({ ...p, page: 0 })) }} />
        <Button color="secondary" onClick={clear}>{t('admin.audit.clear')}</Button>
      </Stack>

      {list.isError && <Alert severity="error" sx={{ mb: 2 }}>{errorText(t, list.error)}</Alert>}

      <Paper variant="outlined">
        <DataGrid<AuditRow>
          rows={list.data?.items ?? []} columns={columns} loading={list.isFetching} rowCount={list.data?.total ?? 0}
          paginationMode="server" sortingMode="server" paginationModel={paging} onPaginationModelChange={setPaging}
          sortModel={sortModel} onSortModelChange={setSortModel} pageSizeOptions={[25, 50, 100, 200]}
          disableRowSelectionOnClick autoHeight density="compact" onRowClick={(p) => setSelected(p.row)}
          localeText={{ noRowsLabel: t('admin.audit.none') }}
          sx={{ border: 0, '& .MuiDataGrid-row': { cursor: 'pointer' } }}
        />
      </Paper>

      <Drawer anchor="right" open={selected !== null} onClose={() => setSelected(null)}
        slotProps={{ paper: { sx: { width: { xs: '100%', md: 640 } } } }}>
        {selected && <AuditDetail row={selected} onClose={() => setSelected(null)} />}
      </Drawer>
    </>
  )
}

function AuditDetail({ row, onClose }: { row: AuditRow; onClose: () => void }) {
  const { t } = useTranslation()
  const fact = (label: string, value: string | number | null) => (
    <Box key={label} sx={{ display: 'flex', gap: 2 }}>
      <Typography variant="body2" color="text.secondary" sx={{ width: 130, flexShrink: 0 }}>{label}</Typography>
      <Typography variant="body2" sx={{ wordBreak: 'break-word' }}>{value === null || value === '' ? '-' : value}</Typography>
    </Box>
  )
  const block = (title: string, value: string | null) => (
    <Box sx={{ flex: 1, minWidth: 0 }}>
      <Typography sx={{ fontWeight: 600, mb: 0.5 }}>{title}</Typography>
      <Paper variant="outlined" sx={{ p: 1.5, bgcolor: 'background.default', maxHeight: 360, overflow: 'auto' }}>
        <Typography component="pre" variant="body2" sx={{ m: 0, fontFamily: 'monospace', whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
          {value ? prettyJson(value) : t('admin.audit.nothing')}
        </Typography>
      </Paper>
    </Box>
  )
  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', px: 2.5, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h6" sx={{ flexGrow: 1 }}>{row.action}</Typography>
        <IconButton aria-label={t('md.close')} onClick={onClose}><CloseIcon /></IconButton>
      </Box>
      <Stack spacing={2.5} sx={{ p: 2.5, overflowY: 'auto' }}>
        <Stack spacing={0.75}>
          {fact(t('admin.audit.time'), formatDateTime(row.occurredAt))}
          {fact(t('admin.audit.user'), row.actor)}
          {fact(t('admin.audit.entity'), row.entityType)}
          {fact(t('admin.audit.entityId'), row.entityId)}
          {fact(t('admin.audit.detail'), row.detail)}
          {fact(t('admin.audit.address'), row.remoteAddr)}
          {fact(t('admin.audit.correlation'), row.correlationId)}
        </Stack>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          {block(t('admin.audit.before'), row.beforeValue)}
          {block(t('admin.audit.after'), row.afterValue)}
        </Stack>
      </Stack>
    </>
  )
}
