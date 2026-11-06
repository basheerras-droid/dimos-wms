import CloseIcon from '@mui/icons-material/Close'
import {
  Alert, Box, Button, Drawer, FormControlLabel, IconButton, MenuItem, Stack, Switch, TextField, Typography,
} from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../../api/client'
import { fetchRecord, useSaveRecord } from '../../api/masterdata'
import { errorText } from './errorText'
import RefField from './RefField'
import type { EntityConfig, FieldDef, Row } from './types'

type Values = Record<string, string | boolean>

/** Turns a record from the server into form values (everything text, so inputs stay simple). */
function toValues(config: EntityConfig, row: Row | null): Values {
  const values: Values = { ...config.defaults }
  if (!row) return values
  for (const f of config.fields) {
    const v = row[f.name]
    if (f.type === 'boolean') values[f.name] = Boolean(v)
    else values[f.name] = v === null || v === undefined ? '' : String(v)
    if (f.type === 'ref') values[labelKey(f.name)] = String(row[f.displayField ?? ''] ?? '')
  }
  return values
}

/** A "ref" field keeps the code of the chosen record next to its id, so it can be shown without a lookup. */
const labelKey = (name: string) => `${name}__label`

/** Turns the form values into the JSON the server expects (blank optional numbers become null). */
function toBody(config: EntityConfig, values: Values, version: number | undefined): Record<string, unknown> {
  const body: Record<string, unknown> = {}
  for (const f of config.fields) {
    if (f.editOnly && version === undefined) continue
    const v = values[f.name]
    if (f.type === 'boolean') body[f.name] = Boolean(v)
    else if (f.type === 'integer' || f.type === 'decimal' || f.type === 'ref') {
      const text = String(v ?? '').trim()
      body[f.name] = text === '' ? null : Number(text)
    } else {
      const text = String(v ?? '').trim()
      body[f.name] = text === '' && !f.required ? null : text
    }
  }
  if (version !== undefined) body.version = version
  return body
}

interface Props {
  config: EntityConfig
  /** null = closed, 'new' = create, a row = edit (or just view when {@code canEdit} is false). */
  target: Row | 'new' | null
  canEdit: boolean
  onClose: () => void
}

/** The side drawer. The form inside is created fresh for every record that is opened. */
export default function EntityDrawer(props: Props) {
  const { target, onClose } = props
  return (
    <Drawer anchor="right" open={target !== null} onClose={onClose} slotProps={{ paper: { sx: { width: { xs: '100%', sm: 440 } } } }}>
      {target !== null && <DrawerForm key={target === 'new' ? 'new' : target.id} {...props} />}
    </Drawer>
  )
}

function DrawerForm({ config, target, canEdit, onClose }: Props) {
  const { t } = useTranslation()
  const save = useSaveRecord<Row>(config.path)
  const initial = target === 'new' || target === null ? null : target
  const [row, setRow] = useState<Row | null>(initial)
  const [values, setValues] = useState<Values>(() => toValues(config, initial))
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [message, setMessage] = useState<{ text: string; stale: boolean } | null>(null)

  const creating = target === 'new'
  const readOnly = !canEdit

  const label = (f: FieldDef) => t(`md.fields.${f.name}`)

  function submit() {
    const found: Record<string, string> = {}
    for (const f of config.fields) {
      if (f.editOnly && creating) continue
      const v = values[f.name]
      if (f.required && f.type !== 'boolean' && String(v ?? '').trim() === '') found[f.name] = t('md.error.required')
      if ((f.type === 'integer' || f.type === 'decimal') && String(v ?? '').trim() !== '') {
        const n = Number(String(v))
        if (Number.isNaN(n) || (f.type === 'integer' && !Number.isInteger(n))) {
          found[f.name] = t(f.type === 'integer' ? 'md.error.notInteger' : 'md.error.notNumber')
        } else if (f.min !== undefined && n < f.min) found[f.name] = t('md.error.min', { min: f.min })
      }
    }
    setErrors(found)
    if (Object.keys(found).length > 0) return

    setMessage(null)
    save.mutate(
      { id: row?.id, body: toBody(config, values, row?.version) },
      {
        onSuccess: () => onClose(),
        onError: (e) => {
          if (e instanceof ApiError) {
            setErrors(e.fieldErrors)
            const extra = [...e.otherDetails]
            // A field error that does not match any visible field would otherwise be invisible.
            const shown = new Set(config.fields.map((f) => f.name))
            for (const [k, v] of Object.entries(e.fieldErrors)) if (!shown.has(k)) extra.push(`${k}: ${v}`)
            const text = errorText(t, e)
            setMessage({ text: extra.length ? `${text} ${extra.join(' ')}` : text, stale: e.isStale })
          } else setMessage({ text: errorText(t, e), stale: false })
        },
      },
    )
  }

  async function reload() {
    if (!row) return
    try {
      const fresh = await fetchRecord<Row>(config.path, row.id)
      setRow(fresh)
      setValues(toValues(config, fresh))
      setErrors({})
      setMessage(null)
    } catch (e) {
      setMessage({ text: errorText(t, e), stale: false })
    }
  }

  function input(f: FieldDef) {
    const common = {
      label: label(f),
      required: f.required && !readOnly,
      error: Boolean(errors[f.name]),
      helperText: errors[f.name] ?? (f.helpKey ? t(f.helpKey) : undefined),
      disabled: readOnly,
      fullWidth: true,
    }
    const v = values[f.name]
    const set = (value: string | boolean) => setValues((p) => ({ ...p, [f.name]: value }))
    if (f.type === 'ref') {
      return (
        <RefField
          key={f.name}
          label={label(f)}
          refPath={f.refPath ?? ''}
          value={String(v ?? '')}
          valueLabel={String(values[labelKey(f.name)] ?? '')}
          required={common.required}
          disabled={readOnly}
          error={common.error}
          helperText={common.helperText}
          onChange={(id, code) => setValues((p) => ({ ...p, [f.name]: id, [labelKey(f.name)]: code }))}
        />
      )
    }
    if (f.type === 'boolean') {
      return (
        <Box key={f.name}>
          <FormControlLabel
            control={<Switch checked={Boolean(v)} onChange={(e) => set(e.target.checked)} disabled={readOnly} />}
            label={label(f)}
          />
          {errors[f.name] && <Typography variant="caption" color="error" sx={{ display: 'block' }}>{errors[f.name]}</Typography>}
        </Box>
      )
    }
    if (f.type === 'select') {
      return (
        <TextField key={f.name} select value={String(v ?? '')} onChange={(e) => set(e.target.value)} {...common}>
          {(f.options ?? []).map((o) => (
            <MenuItem key={o} value={o}>{f.optionsLabelKey ? t(`${f.optionsLabelKey}.${o}`) : o}</MenuItem>
          ))}
        </TextField>
      )
    }
    return (
      <TextField
        key={f.name}
        value={String(v ?? '')}
        onChange={(e) => set(e.target.value)}
        multiline={f.type === 'multiline'}
        minRows={f.type === 'multiline' ? 3 : undefined}
        slotProps={{
          htmlInput: {
            maxLength: f.maxLength,
            inputMode: f.type === 'integer' ? 'numeric' : f.type === 'decimal' ? 'decimal' : undefined,
          },
        }}
        {...common}
      />
    )
  }

  const title = creating ? t(`md.${config.key}.new`) : t(`md.${config.key}.edit`)

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'center', px: 2.5, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <Typography variant="h6" sx={{ flexGrow: 1 }}>{readOnly ? t(`md.${config.key}.view`) : title}</Typography>
        <IconButton aria-label={t('md.close')} onClick={onClose}><CloseIcon /></IconButton>
      </Box>
      <Stack spacing={2.5} sx={{ p: 2.5, overflowY: 'auto', flex: 1 }} component="form" noValidate
        onSubmit={(e) => { e.preventDefault(); if (!readOnly) submit() }}>
        {message && (
          <Alert severity="error" action={message.stale ? <Button color="inherit" size="small" onClick={() => void reload()}>{t('md.reload')}</Button> : undefined}>
            {message.text}
          </Alert>
        )}
        {config.fields.filter((f) => !(f.editOnly && creating)).map(input)}
        {!readOnly && <Typography variant="caption" color="text.secondary">{t('md.requiredHint')}</Typography>}
      </Stack>
      <Box sx={{ display: 'flex', gap: 1.5, justifyContent: 'flex-end', px: 2.5, py: 1.5, borderTop: 1, borderColor: 'divider' }}>
        <Button onClick={onClose} color="secondary" variant="text">{readOnly ? t('md.close') : t('md.cancel')}</Button>
        {!readOnly && (
          <Button variant="contained" onClick={submit} disabled={save.isPending}>
            {save.isPending ? t('md.saving') : t('md.save')}
          </Button>
        )}
      </Box>
    </>
  )
}
