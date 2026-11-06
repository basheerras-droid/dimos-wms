import { Checkbox, FormControlLabel, List, ListItem, TextField, Typography } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { api } from '../../../api/client'
import { MD, useOptions } from '../../../api/masterdata'
import { errorText } from '../errorText'
import type { Row } from '../types'
import { AssignmentShell, type AssignedIds, type Item } from './common'
import { itemText } from './itemText'

/** Which handling-unit types may be placed on locations of this location type. */
export default function HuTypesOfLocationTypeDialog({ row, canEdit, onClose }: { row: Row; canEdit: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const path = `${MD}/location-types/${row.id}/hu-types`
  const current = useQuery({ queryKey: ['md-assign', path], queryFn: () => api<AssignedIds>(path), gcTime: 0 })
  const all = useOptions<Item>(`${MD}/hu-types`)
  const loading = current.isLoading || all.isLoading
  const loadError = current.isError ? errorText(t, current.error) : all.isError ? errorText(t, all.error) : undefined

  if (!current.data || !all.data) {
    return (
      <AssignmentShell
        title={t('md.assign.huTypesTitle')} subtitle={String(row.code)} loading={loading} loadError={loadError}
        canEdit={false} onSave={() => undefined} onClose={onClose}
      >
        {null}
      </AssignmentShell>
    )
  }
  return (
    <Form path={path} title={String(row.code)} data={current.data} options={all.data.items} canEdit={canEdit} onClose={onClose} />
  )
}

function Form({ path, title, data, options, canEdit, onClose }: {
  path: string; title: string; data: AssignedIds; options: Item[]; canEdit: boolean; onClose: () => void
}) {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const [checked, setChecked] = useState<Set<number>>(() => new Set(data.ids))
  const [filter, setFilter] = useState('')
  const [error, setError] = useState<string | null>(null)

  const save = useMutation({
    mutationFn: () => api<AssignedIds>(path, { method: 'PUT', body: { huTypeIds: [...checked], version: data.version } }),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: ['md', `${MD}/location-types`] })
      void qc.invalidateQueries({ queryKey: ['md-assign', path] })
      onClose()
    },
    onError: (e) => setError(errorText(t, e)),
  })

  const shown = options.filter((o) => itemText(o).toLowerCase().includes(filter.trim().toLowerCase()))
  return (
    <AssignmentShell
      title={t('md.assign.huTypesTitle')}
      subtitle={title}
      canEdit={canEdit}
      saving={save.isPending}
      error={error}
      onSave={() => save.mutate()}
      onClose={onClose}
    >
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1.5 }}>{t('md.assign.huTypesHelp')}</Typography>
      <TextField size="small" fullWidth label={t('md.search')} value={filter} onChange={(e) => setFilter(e.target.value)} sx={{ mb: 1 }} />
      <List dense sx={{ maxHeight: 340, overflowY: 'auto' }}>
        {shown.map((o) => (
          <ListItem key={o.id} disablePadding>
            <FormControlLabel
              disabled={!canEdit}
              label={itemText(o)}
              control={
                <Checkbox
                  checked={checked.has(o.id)}
                  onChange={(e) =>
                    setChecked((prev) => {
                      const next = new Set(prev)
                      if (e.target.checked) next.add(o.id)
                      else next.delete(o.id)
                      return next
                    })
                  }
                />
              }
            />
          </ListItem>
        ))}
        {shown.length === 0 && <Typography color="text.secondary">{t('md.noRows')}</Typography>}
      </List>
      <Typography variant="caption" color="text.secondary">{t('md.assign.selectedCount', { count: checked.size })}</Typography>
    </AssignmentShell>
  )
}
