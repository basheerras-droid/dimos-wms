import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/DeleteOutlined'
import DownloadIcon from '@mui/icons-material/Download'
import EditIcon from '@mui/icons-material/EditOutlined'
import ViewColumnIcon from '@mui/icons-material/ViewColumn'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, IconButton, Menu,
  MenuItem, Paper, Snackbar, Stack, TextField, Tooltip, Typography,
} from '@mui/material'
import {
  ColumnsPanelTrigger, DataGrid, Toolbar, ToolbarButton,
  type GridColDef, type GridPaginationModel, type GridRenderCellParams, type GridRowSelectionModel,
  type GridSortModel,
} from '@mui/x-data-grid'
import { useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useCan } from '../../api/auth'
import { api, apiDownload } from '../../api/client'
import { MD, useDeleteRecord, useMasterList, useOptions, type ListParams } from '../../api/masterdata'
import EntityDrawer from './EntityDrawer'
import { errorText } from './errorText'
import type { BulkAction, EntityConfig, FilterDef, Row, RowAction } from './types'

function GridToolbar() {
  const { t } = useTranslation()
  return (
    <Toolbar>
      <Tooltip title={t('md.columns')}>
        <ColumnsPanelTrigger render={<ToolbarButton />}>
          <ViewColumnIcon fontSize="small" />
        </ColumnsPanelTrigger>
      </Tooltip>
    </Toolbar>
  )
}

const EMPTY_SELECTION: GridRowSelectionModel = { type: 'include', ids: new Set() }

/** A drop-down filter whose choices are the records of another (small) table, e.g. the areas. */
function RefFilter({ filter, value, onChange }: { filter: FilterDef; value: string; onChange: (v: string) => void }) {
  const { t } = useTranslation()
  const options = useOptions<{ id: number; code: string }>(filter.refPath ?? '')
  return (
    <TextField select size="small" label={t(`md.fields.${filter.name}`)} value={value}
      onChange={(e) => onChange(e.target.value)} sx={{ minWidth: 180 }}>
      <MenuItem value="">{t('md.all')}</MenuItem>
      {(options.data?.items ?? []).map((o) => <MenuItem key={o.id} value={String(o.id)}>{o.code}</MenuItem>)}
    </TextField>
  )
}

/** A list of one master-data table with search, filters, paging from the server, and an edit drawer. */
export default function EntityPage({ config }: { config: EntityConfig }) {
  const { t } = useTranslation()
  const canEdit = useCan('MASTERDATA_EDIT')

  const [paging, setPaging] = useState<GridPaginationModel>({ page: 0, pageSize: 25 })
  const [sortModel, setSortModel] = useState<GridSortModel>([])
  const [search, setSearch] = useState('')
  const [q, setQ] = useState('')
  const [filters, setFilters] = useState<Record<string, string>>({})
  const [target, setTarget] = useState<Row | 'new' | null>(null)
  const [toDelete, setToDelete] = useState<Row | null>(null)
  const [notice, setNotice] = useState<{ text: string; severity: 'success' | 'error' } | null>(null)
  const [activeAction, setActiveAction] = useState<{ action: RowAction; row: Row } | null>(null)
  const [exportAnchor, setExportAnchor] = useState<HTMLElement | null>(null)
  const [selection, setSelection] = useState<GridRowSelectionModel>(EMPTY_SELECTION)
  const [bulkAsk, setBulkAsk] = useState<BulkAction | null>(null)
  const [reason, setReason] = useState('')
  const [bulkBusy, setBulkBusy] = useState(false)
  const qc = useQueryClient()
  const bulk = canEdit ? config.bulk : undefined
  const selectedIds = [...selection.ids].map(Number)

  // Wait a moment after typing before asking the server.
  useEffect(() => {
    const id = setTimeout(() => {
      setQ(search)
      setPaging((p) => ({ ...p, page: 0 }))
    }, 300)
    return () => clearTimeout(id)
  }, [search])

  const sort = sortModel.length > 0 ? `${sortModel[0].field},${sortModel[0].sort}` : config.defaultSort
  const params: ListParams = { q, page: paging.page, size: paging.pageSize, sort, ...filters }
  const list = useMasterList<Row>(config.path, params)
  const remove = useDeleteRecord(config.path)

  const columns = useMemo<GridColDef<Row>[]>(() => {
    const cols: GridColDef<Row>[] = config.columns.map((c) => ({
      field: c.field,
      headerName: t(`md.fields.${c.field}`),
      width: c.width,
      flex: c.flex ?? (c.width ? undefined : 1),
      minWidth: 90,
      sortable: c.sortable ?? false,
      filterable: false,
      type: c.type === 'number' ? 'number' : 'string',
      renderCell: (p: GridRenderCellParams<Row>) => {
        const v = p.row[c.field]
        if (c.type === 'boolean') return v ? t('md.yes') : t('md.no')
        if (v === null || v === undefined || v === '') return c.emptyKey ? t(c.emptyKey) : ''
        return c.optionsLabelKey ? t(`${c.optionsLabelKey}.${String(v)}`, { defaultValue: String(v) }) : String(v)
      },
    }))
    cols.push({
      field: '__actions',
      headerName: '',
      width: 96 + 40 * (config.rowActions?.length ?? 0),
      sortable: false,
      filterable: false,
      disableColumnMenu: true,
      hideable: false,
      renderCell: (p: GridRenderCellParams<Row>) => (
        <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
          {config.rowActions?.map((a) => (
            <Tooltip key={a.key} title={t(a.labelKey)}>
              <IconButton size="small" aria-label={t(a.labelKey)} onClick={() => setActiveAction({ action: a, row: p.row })}>
                {a.icon}
              </IconButton>
            </Tooltip>
          ))}
          <Tooltip title={canEdit ? t('md.edit') : t('md.view')}>
            <IconButton size="small" aria-label={canEdit ? t('md.edit') : t('md.view')} onClick={() => setTarget(p.row)}>
              {canEdit ? <EditIcon fontSize="small" /> : <VisibilityIcon fontSize="small" />}
            </IconButton>
          </Tooltip>
          {canEdit && (
            <Tooltip title={t('md.delete')}>
              <IconButton size="small" aria-label={t('md.delete')} onClick={() => setToDelete(p.row)}>
                <DeleteIcon fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Box>
      ),
    })
    return cols
  }, [config, t, canEdit])

  const columnVisibility = useMemo(
    () => Object.fromEntries(config.columns.filter((c) => c.hiddenByDefault).map((c) => [c.field, false])),
    [config],
  )

  async function runBulk(action: BulkAction, why: string) {
    if (!bulk) return
    setBulkBusy(true)
    try {
      const result = await api<{ updated: number }>(bulk.path, {
        method: 'POST',
        body: { ids: selectedIds, action: action.action, reason: why.trim() || undefined },
      })
      await qc.invalidateQueries({ queryKey: ['md', config.path] })
      setSelection(EMPTY_SELECTION)
      setBulkAsk(null)
      setReason('')
      setNotice({ text: t('md.bulk.done', { count: result.updated }), severity: 'success' })
    } catch (e) {
      setNotice({ text: errorText(t, e), severity: 'error' })
      setBulkAsk(null)
    } finally {
      setBulkBusy(false)
    }
  }

  function startBulk(action: BulkAction) {
    if (action.needsReason) {
      setReason('')
      setBulkAsk(action)
    } else void runBulk(action, '')
  }

  async function download(format: 'csv' | 'xlsx') {
    setExportAnchor(null)
    try {
      await apiDownload(`${MD}/export/${config.exportType}?format=${format}`)
    } catch (e) {
      setNotice({ text: errorText(t, e), severity: 'error' })
    }
  }

  function confirmDelete() {
    if (!toDelete) return
    remove.mutate(toDelete.id, {
      onSuccess: () => {
        setNotice({ text: t('md.deleted'), severity: 'success' })
        setToDelete(null)
      },
      onError: (e) => {
        setNotice({ text: errorText(t, e), severity: 'error' })
        setToDelete(null)
      },
    })
  }

  return (
    <>
      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1, alignItems: 'center' }}>
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>{t(`md.${config.key}.title`)}</Typography>
        <Button variant="outlined" color="secondary" startIcon={<DownloadIcon />} onClick={(e) => setExportAnchor(e.currentTarget)}>
          {t('md.export')}
        </Button>
        <Menu anchorEl={exportAnchor} open={Boolean(exportAnchor)} onClose={() => setExportAnchor(null)}>
          <MenuItem onClick={() => void download('csv')}>{t('md.exportCsv')}</MenuItem>
          <MenuItem onClick={() => void download('xlsx')}>{t('md.exportXlsx')}</MenuItem>
        </Menu>
        {canEdit && (
          <Button variant="contained" startIcon={<AddIcon />} onClick={() => setTarget('new')}>{t(`md.${config.key}.add`)}</Button>
        )}
      </Stack>

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1.5 }}>
        <TextField
          size="small" label={t(config.searchKey ?? 'md.search')} value={search} onChange={(e) => setSearch(e.target.value)}
          sx={{ minWidth: { xs: '100%', sm: 280 } }}
        />
        {config.filters?.map((f) => {
          const change = (v: string) => {
            setFilters((p) => ({ ...p, [f.name]: v }))
            setPaging((p) => ({ ...p, page: 0 }))
          }
          if (f.type === 'ref') return <RefFilter key={f.name} filter={f} value={filters[f.name] ?? ''} onChange={change} />
          if (f.type === 'integer') {
            return (
              <TextField key={f.name} size="small" type="number" label={t(`md.fields.${f.name}`)}
                value={filters[f.name] ?? ''} onChange={(e) => change(e.target.value)} sx={{ width: 140 }} />
            )
          }
          const options = f.type === 'boolean' ? ['true', 'false'] : (f.options ?? [])
          return (
            <TextField
              key={f.name} select size="small" label={t(`md.fields.${f.name}`)} value={filters[f.name] ?? ''}
              onChange={(e) => {
                setFilters((p) => ({ ...p, [f.name]: e.target.value }))
                setPaging((p) => ({ ...p, page: 0 }))
              }}
              sx={{ minWidth: 180 }}
            >
              <MenuItem value="">{t('md.all')}</MenuItem>
              {options.map((o) => (
                <MenuItem key={o} value={o}>
                  {f.type === 'boolean' ? t(o === 'true' ? 'md.yes' : 'md.no') : f.optionsLabelKey ? t(`${f.optionsLabelKey}.${o}`) : o}
                </MenuItem>
              ))}
            </TextField>
          )
        })}
      </Stack>

      {list.isError && <Alert severity="error" sx={{ mb: 2 }}>{errorText(t, list.error)}</Alert>}

      {bulk && selectedIds.length > 0 && (
        <Paper variant="outlined" sx={{ p: 1.5, mb: 2, display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
          <Typography sx={{ fontWeight: 600, mr: 1 }}>{t('md.bulk.selected', { count: selectedIds.length })}</Typography>
          {bulk.actions.map((a) => (
            <Button key={a.action} size="small" variant="outlined" color="secondary" disabled={bulkBusy || selectedIds.length > bulk.max}
              onClick={() => startBulk(a)}>
              {t(a.labelKey)}
            </Button>
          ))}
          <Button size="small" variant="text" color="secondary" onClick={() => setSelection(EMPTY_SELECTION)}>{t('md.bulk.clear')}</Button>
          {selectedIds.length > bulk.max && <Alert severity="warning" sx={{ py: 0 }}>{t('md.bulk.tooMany', { max: bulk.max })}</Alert>}
        </Paper>
      )}

      <Paper variant="outlined" sx={{ width: '100%' }}>
        <DataGrid<Row>
          rows={list.data?.items ?? []}
          columns={columns}
          loading={list.isFetching}
          rowCount={list.data?.total ?? 0}
          paginationMode="server"
          sortingMode="server"
          filterMode="server"
          paginationModel={paging}
          onPaginationModelChange={setPaging}
          sortModel={sortModel}
          onSortModelChange={setSortModel}
          pageSizeOptions={[25, 50, 100, 200]}
          initialState={{ columns: { columnVisibilityModel: columnVisibility } }}
          disableRowSelectionOnClick
          checkboxSelection={Boolean(bulk)}
          keepNonExistentRowsSelected
          rowSelectionModel={selection}
          onRowSelectionModelChange={setSelection}
          showToolbar
          slots={{ toolbar: GridToolbar }}
          autoHeight
          onRowDoubleClick={(p) => setTarget(p.row)}
          localeText={{ noRowsLabel: t('md.noRows') }}
          sx={{ border: 0 }}
        />
      </Paper>

      {activeAction && (
        <activeAction.action.dialog row={activeAction.row} canEdit={canEdit} onClose={() => setActiveAction(null)} />
      )}

      <EntityDrawer config={config} target={target} canEdit={canEdit} onClose={() => setTarget(null)} />

      <Dialog open={bulkAsk !== null} onClose={() => setBulkAsk(null)} fullWidth maxWidth="xs">
        <DialogTitle>{bulkAsk ? t(bulkAsk.labelKey) : ''}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ mb: 2 }}>{t('md.bulk.reasonText', { count: selectedIds.length })}</DialogContentText>
          <TextField autoFocus fullWidth required label={t('md.fields.blockReason')} value={reason}
            onChange={(e) => setReason(e.target.value)} slotProps={{ htmlInput: { maxLength: 255 } }} />
        </DialogContent>
        <DialogActions>
          <Button color="secondary" onClick={() => setBulkAsk(null)}>{t('md.cancel')}</Button>
          <Button variant="contained" disabled={reason.trim() === '' || bulkBusy}
            onClick={() => bulkAsk && void runBulk(bulkAsk, reason)}>
            {t('md.bulk.apply')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={toDelete !== null} onClose={() => setToDelete(null)}>
        <DialogTitle>{t('md.deleteTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('md.deleteText', { name: toDelete ? (config.rowLabel?.(toDelete) ?? String(toDelete.code ?? toDelete.id)) : '' })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="secondary" onClick={() => setToDelete(null)}>{t('md.cancel')}</Button>
          <Button variant="contained" onClick={confirmDelete} disabled={remove.isPending}>{t('md.delete')}</Button>
        </DialogActions>
      </Dialog>

      <Snackbar open={notice !== null} autoHideDuration={6000} onClose={() => setNotice(null)}>
        {notice ? <Alert severity={notice.severity} onClose={() => setNotice(null)}>{notice.text}</Alert> : undefined}
      </Snackbar>
    </>
  )
}
