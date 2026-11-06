import { Checkbox, FormControlLabel, List, ListItem, TextField, Typography } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api, ApiError } from '../../../api/client'
import { MD, useOptions } from '../../../api/masterdata'
import { errorText } from '../errorText'
import type { Row } from '../types'
import { AssignmentShell, type Item } from './common'
import { itemText } from './itemText'

interface Nesting {
  outerHuTypeId: number
  maxQty: number | null
}

/** Which other handling-unit types this type may be placed in or on (carton on pallet, pallet in ULD, ...). */
export default function PlacedInDialog({ row, canEdit, onClose }: { row: Row; canEdit: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const path = `${MD}/hu-types/${row.id}/placed-in`
  const current = useQuery({ queryKey: ['md-assign', path], queryFn: () => api<Nesting[]>(path), gcTime: 0 })
  const all = useOptions<Item>(`${MD}/hu-types`)

  if (!current.data || !all.data) {
    const failed = current.isError ? current.error : all.isError ? all.error : null
    return (
      <AssignmentShell
        title={t('md.assign.placedInTitle')} subtitle={String(row.code)}
        loading={current.isLoading || all.isLoading} loadError={failed ? errorText(t, failed) : undefined}
        canEdit={false} onSave={() => undefined} onClose={onClose}
      >
        {null}
      </AssignmentShell>
    )
  }
  return (
    <Form
      row={row} path={path} data={current.data}
      options={all.data.items.filter((o) => o.id !== row.id)} canEdit={canEdit} onClose={onClose}
    />
  )
}

function Form({ row, path, data, options, canEdit, onClose }: {
  row: Row; path: string; data: Nesting[]; options: Item[]; canEdit: boolean; onClose: () => void
}) {
  const { t } = useTranslation()
  const qc = useQueryClient()
  // outer type id -> max quantity text ('' = no limit); present in the map = ticked
  const [chosen, setChosen] = useState<Map<number, string>>(
    () => new Map(data.map((n) => [n.outerHuTypeId, n.maxQty === null ? '' : String(n.maxQty)])),
  )
  const [filter, setFilter] = useState('')
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => {
      const placedIn = [...chosen].map(([outerHuTypeId, qty]) => ({ outerHuTypeId, maxQty: qty.trim() === '' ? null : Number(qty) }))
      return api<Nesting[]>(path, { method: 'PUT', body: { placedIn, version: row.version } })
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['md', `${MD}/hu-types`] })
      void qc.invalidateQueries({ queryKey: ['md-assign', path] })
      onClose()
    },
    onError: (e) => {
      const detail = e instanceof ApiError ? (e.fieldErrors.placedIn ?? e.otherDetails.join(' ')) : ''
      setError(detail ? `${errorText(t, e)} ${detail}` : errorText(t, e))
    },
  })

  const shown = options.filter((o) => itemText(o).toLowerCase().includes(filter.trim().toLowerCase()))
  const badQty = [...chosen.values()].some((q) => q.trim() !== '' && (!/^\d+$/.test(q.trim()) || Number(q) < 1))

  return (
    <AssignmentShell
      title={t('md.assign.placedInTitle')} subtitle={String(row.code)}
      canEdit={canEdit} saveDisabled={badQty} saving={save.isPending} error={error} onSave={() => save.mutate()} onClose={onClose}
    >
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>
        {t('md.assign.placedInHelp', { code: String(row.code) })}
      </Typography>
      <TextField size="small" fullWidth label={t('md.search')} value={filter} onChange={(e) => setFilter(e.target.value)} sx={{ mb: 1 }} />
      <List dense sx={{ maxHeight: 340, overflowY: 'auto' }}>
        {shown.map((o) => {
          const on = chosen.has(o.id)
          return (
            <ListItem key={o.id} secondaryAction={on ? (
              <TextField
                size="small" sx={{ width: 150 }} label={t('md.fields.maxQty')} disabled={!canEdit}
                value={chosen.get(o.id) ?? ''}
                error={chosen.get(o.id)?.trim() !== '' && !/^\d+$/.test((chosen.get(o.id) ?? '').trim())}
                onChange={(e) => setChosen((prev) => new Map(prev).set(o.id, e.target.value))}
                slotProps={{ htmlInput: { inputMode: 'numeric' } }}
              />
            ) : undefined}>
              <FormControlLabel
                disabled={!canEdit}
                label={itemText(o)}
                control={
                  <Checkbox
                    checked={on}
                    onChange={(e) =>
                      setChosen((prev) => {
                        const next = new Map(prev)
                        if (e.target.checked) next.set(o.id, '')
                        else next.delete(o.id)
                        return next
                      })
                    }
                  />
                }
              />
            </ListItem>
          )
        })}
        {shown.length === 0 && <Typography color="text.secondary">{t('md.noRows')}</Typography>}
      </List>
      {badQty && <Typography variant="caption" color="error">{t('md.assign.badQty')}</Typography>}
    </AssignmentShell>
  )
}
