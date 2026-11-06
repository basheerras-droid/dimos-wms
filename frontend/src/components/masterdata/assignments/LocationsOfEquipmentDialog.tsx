import { Alert, Box, Button, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { DataGrid, type GridColDef, type GridRowSelectionModel } from '@mui/x-data-grid'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, ApiError } from '../../../api/client'
import { MD, useOptions, type Page } from '../../../api/masterdata'
import { errorText } from '../errorText'
import RefField from '../RefField'
import type { Row } from '../types'
import { AssignmentShell, type AssignedIds, type Item } from './common'

const MAX_ADD_AT_ONCE = 5000
const NONE: GridRowSelectionModel = { type: 'include', ids: new Set() }

/** The locations that belong to one piece of equipment (e.g. the positions of a conveyor line or an aisle's crane). */
export default function LocationsOfEquipmentDialog({ row, canEdit, onClose }: { row: Row; canEdit: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const path = `${MD}/equipment/${row.id}/locations`
  const current = useQuery({ queryKey: ['md-assign', path], queryFn: () => api<AssignedIds>(path), gcTime: 0 })

  if (!current.data) {
    return (
      <AssignmentShell
        title={t('md.assign.locationsTitle')} subtitle={String(row.code)} maxWidth="md"
        loading={current.isLoading} loadError={current.isError ? errorText(t, current.error) : undefined}
        canEdit={false} onSave={() => undefined} onClose={onClose}
      >
        {null}
      </AssignmentShell>
    )
  }
  return <Form path={path} title={String(row.code)} data={current.data} canEdit={canEdit} onClose={onClose} />
}

function Form({ path, title, data, canEdit, onClose }: { path: string; title: string; data: AssignedIds; canEdit: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const [assigned, setAssigned] = useState<Map<number, Item>>(() => new Map(data.items.map((i) => [i.id, i])))
  const [selection, setSelection] = useState<GridRowSelectionModel>(NONE)
  const [filter, setFilter] = useState('')
  const [areaId, setAreaId] = useState('')
  const [matchText, setMatchText] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [info, setInfo] = useState<string | null>(null)
  const areas = useOptions<{ id: number; code: string }>(`${MD}/areas`)

  const save = useMutation({
    mutationFn: () => api<AssignedIds>(path, { method: 'PUT', body: { locationIds: [...assigned.keys()], version: data.version } }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['md', `${MD}/equipment`] })
      void qc.invalidateQueries({ queryKey: ['md-assign', path] })
      onClose()
    },
    onError: (e) => setError(errorText(t, e)),
  })

  function addOne(id: string, code: string) {
    if (!id) return
    setInfo(null)
    setAssigned((prev) => (prev.has(Number(id)) ? prev : new Map(prev).set(Number(id), { id: Number(id), code, name: null })))
  }

  /** Adds every location that matches the area and/or the text (all pages, up to a safe limit). */
  async function addMatching() {
    setBusy(true)
    setError(null)
    setInfo(null)
    try {
      const found: Item[] = []
      for (let page = 0; page < MAX_ADD_AT_ONCE / 200; page++) {
        const url = `${MD}/locations?size=200&sort=code&page=${page}&q=${encodeURIComponent(matchText)}${areaId ? `&areaId=${areaId}` : ''}`
        const result = await api<Page<Item>>(url)
        if (result.total > MAX_ADD_AT_ONCE) {
          setError(t('md.assign.tooManyMatches', { count: result.total, max: MAX_ADD_AT_ONCE }))
          return
        }
        found.push(...result.items)
        if ((page + 1) * 200 >= result.total) break
      }
      const before = assigned.size
      const next = new Map(assigned)
      found.forEach((i) => next.set(i.id, i))
      setAssigned(next)
      setInfo(t('md.assign.addedCount', { added: next.size - before, found: found.length }))
    } catch (e) {
      setError(e instanceof ApiError ? errorText(t, e) : t('md.error.network'))
    } finally {
      setBusy(false)
    }
  }

  function removeSelected() {
    const gone = new Set([...selection.ids].map(Number))
    setAssigned((prev) => new Map([...prev].filter(([id]) => !gone.has(id))))
    setSelection(NONE)
  }

  const needle = filter.trim().toLowerCase()
  const rows = [...assigned.values()]
    .filter((i) => !needle || i.code.toLowerCase().includes(needle) || (i.name ?? '').toLowerCase().includes(needle))
    .sort((a, b) => a.code.localeCompare(b.code))
  const columns: GridColDef<Item>[] = [
    { field: 'code', headerName: t('md.fields.code'), width: 200 },
    { field: 'name', headerName: t('md.fields.name'), flex: 1 },
  ]

  return (
    <AssignmentShell
      title={t('md.assign.locationsTitle')} subtitle={title} maxWidth="md"
      canEdit={canEdit} saving={save.isPending} error={error} onSave={() => save.mutate()} onClose={onClose}
    >
      {canEdit && (
        <Stack spacing={2} sx={{ mb: 2 }}>
          <Typography variant="body2" color="text.secondary">{t('md.assign.locationsHelp')}</Typography>
          <RefField
            label={t('md.assign.addOne')} refPath={`${MD}/locations`} value="" valueLabel=""
            onChange={addOne}
          />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'flex-start' } }}>
            <TextField select size="small" label={t('md.fields.areaId')} value={areaId} onChange={(e) => setAreaId(e.target.value)} sx={{ minWidth: 160 }}>
              <MenuItem value="">{t('md.all')}</MenuItem>
              {(areas.data?.items ?? []).map((a) => <MenuItem key={a.id} value={String(a.id)}>{a.code}</MenuItem>)}
            </TextField>
            <TextField size="small" label={t('md.assign.matchText')} value={matchText} onChange={(e) => setMatchText(e.target.value)} sx={{ flex: 1 }} />
            <Button variant="outlined" color="secondary" onClick={() => void addMatching()} disabled={busy || (!areaId && matchText.trim() === '')}>
              {t('md.assign.addMatching')}
            </Button>
          </Stack>
          {info && <Alert severity="success" sx={{ py: 0 }}>{info}</Alert>}
        </Stack>
      )}

      <Stack direction="row" spacing={1.5} sx={{ mb: 1, alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
        <TextField size="small" label={t('md.search')} value={filter} onChange={(e) => setFilter(e.target.value)} />
        <Typography variant="body2" color="text.secondary" sx={{ flexGrow: 1 }}>
          {t('md.assign.assignedCount', { count: assigned.size })}
        </Typography>
        {canEdit && (
          <Button size="small" variant="outlined" color="secondary" disabled={selection.ids.size === 0} onClick={removeSelected}>
            {t('md.assign.removeSelected', { count: selection.ids.size })}
          </Button>
        )}
      </Stack>
      <Box sx={{ height: 340 }}>
        <DataGrid<Item>
          rows={rows} columns={columns} density="compact"
          checkboxSelection={canEdit} disableRowSelectionOnClick
          rowSelectionModel={selection} onRowSelectionModelChange={setSelection}
          pageSizeOptions={[25, 50, 100]} initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
          localeText={{ noRowsLabel: t('md.assign.noneAssigned') }}
        />
      </Box>
    </AssignmentShell>
  )
}
