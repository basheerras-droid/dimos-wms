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
  type GridColDef, type GridPaginationModel, type GridRenderCellParams, type GridSortModel,
} from '@mui/x-data-grid'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useCan } from '../../api/auth'
import { apiDownload } from '../../api/client'
import { MD, useDeleteRecord, useMasterList, type ListParams } from '../../api/masterdata'
import EntityDrawer from './EntityDrawer'
import { errorText } from './errorText'
import type { EntityConfig, Row } from './types'

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
  const [exportAnchor, setExportAnchor] = useState<HTMLElement | null>(null)

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
        if (v === null || v === undefined || v === '') return ''
        return c.optionsLabelKey ? t(`${c.optionsLabelKey}.${String(v)}`, { defaultValue: String(v) }) : String(v)
      },
    }))
    cols.push({
      field: '__actions',
      headerName: '',
      width: 96,
      sortable: false,
      filterable: false,
      disableColumnMenu: true,
      hideable: false,
      renderCell: (p: GridRenderCellParams<Row>) => (
        <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
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
          size="small" label={t('md.search')} value={search} onChange={(e) => setSearch(e.target.value)}
          sx={{ minWidth: { xs: '100%', sm: 280 } }}
        />
        {config.filters?.map((f) => {
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
          showToolbar
          slots={{ toolbar: GridToolbar }}
          autoHeight
          onRowDoubleClick={(p) => setTarget(p.row)}
          localeText={{ noRowsLabel: t('md.noRows') }}
          sx={{ border: 0 }}
        />
      </Paper>

      <EntityDrawer config={config} target={target} canEdit={canEdit} onClose={() => setTarget(null)} />

      <Dialog open={toDelete !== null} onClose={() => setToDelete(null)}>
        <DialogTitle>{t('md.deleteTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('md.deleteText', { name: String(toDelete?.code ?? toDelete?.id ?? '') })}
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
