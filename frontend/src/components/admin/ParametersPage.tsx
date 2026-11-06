import CloseIcon from '@mui/icons-material/Close'
import EditIcon from '@mui/icons-material/EditOutlined'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import {
  Alert, Box, Button, Chip, Divider, Drawer, IconButton, MenuItem, Paper, Stack, Switch, Table, TableBody,
  TableCell, TableHead, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  useParameters, useRemoveParameter, useSetParameter, useWorkstationGroups, useWorkstations,
  type ParameterOverride, type ParameterScope, type ParameterView,
} from '../../api/admin'
import { useCan } from '../../api/auth'
import { ApiError } from '../../api/client'
import { errorText } from '../masterdata/errorText'
import { formatRange, formatValue, isoToMinutes, minutesToIso } from './parameterFormat'

/**
 * System parameters: every setting with the value that applies and where it comes from. Values can be set per scope
 * (everywhere, this site, a workstation group, a single workstation); the most specific one wins. The limits of each
 * parameter are fixed in the software and cannot be widened here.
 */
export default function ParametersPage() {
  const { t } = useTranslation()
  const canEdit = useCan('PARAMETER_EDIT')
  const workstations = useWorkstations({ size: 200 })
  const [workstationId, setWorkstationId] = useState('')
  const [search, setSearch] = useState('')
  const [openCode, setOpenCode] = useState<string | null>(null)
  const list = useParameters(workstationId)

  const needle = search.trim().toLowerCase()
  const rows = (list.data ?? []).filter((p) => !needle || p.name.toLowerCase().includes(needle) || p.code.toLowerCase().includes(needle))
  const modules = [...new Set(rows.map((p) => p.module))]
  const open = list.data?.find((p) => p.code === openCode) ?? null

  const sourceChip = (p: ParameterView) => {
    const e = p.effective
    const label = e.source === 'DEFAULT' ? t('admin.parameters.src.DEFAULT')
      : e.source === 'WS_GROUP' || e.source === 'WORKSTATION' ? t(`admin.parameters.src.${e.source}`, { code: e.sourceRefCode ?? '' })
        : t(`admin.parameters.src.${e.source}`)
    return <Chip size="small" variant={e.source === 'DEFAULT' ? 'outlined' : 'filled'} color={e.source === 'DEFAULT' ? 'default' : 'info'} label={label} />
  }

  return (
    <>
      <Typography variant="h4" component="h1" gutterBottom>{t('admin.parameters.title')}</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>{t('admin.parameters.intro')}</Typography>
      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1.5 }}>
        <TextField size="small" label={t('md.search')} value={search} onChange={(e) => setSearch(e.target.value)} sx={{ minWidth: { xs: '100%', sm: 260 } }} />
        <TextField select size="small" label={t('admin.parameters.viewAs')} value={workstationId} sx={{ minWidth: 280 }}
          onChange={(e) => setWorkstationId(e.target.value)} helperText={t('admin.parameters.viewAsHelp')}>
          <MenuItem value="">{t('admin.parameters.thisSite')}</MenuItem>
          {(workstations.data?.items ?? []).map((w) => <MenuItem key={w.id} value={String(w.id)}>{w.code} - {w.name}</MenuItem>)}
        </TextField>
      </Stack>
      {list.isError && <Alert severity="error" sx={{ mb: 2 }}>{errorText(t, list.error)}</Alert>}

      {modules.map((module) => (
        <Paper key={module} variant="outlined" sx={{ mb: 3, overflowX: 'auto' }}>
          <Typography variant="h6" sx={{ p: 2, pb: 1 }}>{t(`admin.parameters.modules.${module}`, { defaultValue: module })}</Typography>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('admin.parameters.parameter')}</TableCell>
                <TableCell>{t('admin.parameters.value')}</TableCell>
                <TableCell>{t('admin.parameters.source')}</TableCell>
                <TableCell>{t('admin.parameters.default')}</TableCell>
                <TableCell>{t('admin.parameters.range')}</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.filter((p) => p.module === module).map((p) => (
                <TableRow key={p.code} hover sx={{ cursor: 'pointer' }} onClick={() => setOpenCode(p.code)}>
                  <TableCell>
                    <Typography sx={{ fontWeight: 600 }}>{p.name}</Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{p.code}</Typography>
                  </TableCell>
                  <TableCell sx={{ fontWeight: 600 }}>{formatValue(t, p, p.effective.value)}</TableCell>
                  <TableCell>{sourceChip(p)}</TableCell>
                  <TableCell>{formatValue(t, p, p.defaultValue)}</TableCell>
                  <TableCell>{formatRange(t, p)}</TableCell>
                  <TableCell align="right">
                    <Tooltip title={canEdit ? t('md.edit') : t('md.view')}>
                      <IconButton size="small" aria-label={canEdit ? t('md.edit') : t('md.view')} onClick={(e) => { e.stopPropagation(); setOpenCode(p.code) }}>
                        {canEdit ? <EditIcon fontSize="small" /> : <VisibilityIcon fontSize="small" />}
                      </IconButton>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
      ))}

      <Drawer anchor="right" open={open !== null} onClose={() => setOpenCode(null)} slotProps={{ paper: { sx: { width: { xs: '100%', sm: 560 } } } }}>
        {open && <ParameterDrawer view={open} canEdit={canEdit} onClose={() => setOpenCode(null)} />}
      </Drawer>
    </>
  )
}

function ParameterDrawer({ view, canEdit, onClose }: { view: ParameterView; canEdit: boolean; onClose: () => void }) {
  const { t } = useTranslation()
  const groups = useWorkstationGroups({ size: 200 })
  const workstations = useWorkstations({ size: 200 })
  const scopes = view.allowedScopes

  const valueOf = (scope: ParameterScope) => view.overrides.find((o) => o.scope === scope)
  const overridesOf = (scope: ParameterScope) => view.overrides.filter((o) => o.scope === scope)

  return (
    <>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', px: 2.5, py: 1.5, borderBottom: 1, borderColor: 'divider' }}>
        <Box sx={{ flexGrow: 1 }}>
          <Typography variant="h6">{view.name}</Typography>
          <Typography variant="caption" color="text.secondary" sx={{ fontFamily: 'monospace' }}>{view.code}</Typography>
        </Box>
        <IconButton aria-label={t('md.close')} onClick={onClose}><CloseIcon /></IconButton>
      </Box>
      <Stack spacing={2.5} sx={{ p: 2.5, overflowY: 'auto', flex: 1 }}>
        {view.description && <Typography>{view.description}</Typography>}
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <Chip size="small" variant="outlined" label={t('admin.parameters.defaultIs', { value: formatValue(t, view, view.defaultValue) })} />
          {formatRange(t, view) && <Chip size="small" variant="outlined" label={t('admin.parameters.allowedRange', { range: formatRange(t, view) })} />}
          {view.restartNeeded && <Chip size="small" color="warning" label={t('admin.parameters.restartNeeded')} />}
        </Stack>
        <Alert severity="info">{t('admin.parameters.boundsFixed')}</Alert>
        {!canEdit && <Alert severity="info">{t('admin.parameters.viewOnly')}</Alert>}

        <Typography sx={{ fontWeight: 600 }}>{t('admin.parameters.valuesTitle')}</Typography>
        <Typography variant="caption" color="text.secondary" sx={{ mt: -2 }}>{t('admin.parameters.valuesHelp')}</Typography>

        {scopes.includes('GLOBAL') && <ScopeRow view={view} scope="GLOBAL" scopeRefId={null} override={valueOf('GLOBAL')} canEdit={canEdit} label={t('admin.parameters.scope.GLOBAL')} />}
        {scopes.includes('SITE') && <ScopeRow view={view} scope="SITE" scopeRefId={null} override={valueOf('SITE')} canEdit={canEdit} label={t('admin.parameters.scope.SITE')} />}

        {scopes.includes('WS_GROUP') && (
          <ScopeGroup view={view} scope="WS_GROUP" canEdit={canEdit} title={t('admin.parameters.scope.WS_GROUP_PLURAL')}
            overrides={overridesOf('WS_GROUP')} options={(groups.data?.items ?? []).map((g) => ({ id: g.id, code: g.code }))} />
        )}
        {scopes.includes('WORKSTATION') && (
          <ScopeGroup view={view} scope="WORKSTATION" canEdit={canEdit} title={t('admin.parameters.scope.WORKSTATION_PLURAL')}
            overrides={overridesOf('WORKSTATION')} options={(workstations.data?.items ?? []).map((w) => ({ id: w.id, code: w.code }))} />
        )}
      </Stack>
    </>
  )
}

/** One fixed scope (everywhere / this site): shows the value if set, or "not set", with Save / Remove. */
function ScopeRow({ view, scope, scopeRefId, override, canEdit, label }: {
  view: ParameterView; scope: ParameterScope; scopeRefId: number | null; override: ParameterOverride | undefined; canEdit: boolean; label: string
}) {
  const { t } = useTranslation()
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography sx={{ fontWeight: 600, mb: 1 }}>{label}</Typography>
      <ValueLine key={override ? `${override.id}-${override.version}` : 'new'} view={view} scope={scope} scopeRefId={scopeRefId}
        override={override} canEdit={canEdit} notSet={t('admin.parameters.notSet')} />
    </Paper>
  )
}

/** Overrides for workstation groups or single workstations: the existing ones, and a line to add another. */
function ScopeGroup({ view, scope, title, overrides, options, canEdit }: {
  view: ParameterView; scope: ParameterScope; title: string; overrides: ParameterOverride[];
  options: { id: number; code: string }[]; canEdit: boolean
}) {
  const { t } = useTranslation()
  const [ref, setRef] = useState('')
  const free = options.filter((o) => !overrides.some((x) => x.scopeRefId === o.id))
  return (
    <Paper variant="outlined" sx={{ p: 2 }}>
      <Typography sx={{ fontWeight: 600, mb: 1 }}>{title}</Typography>
      {overrides.length === 0 && <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{t('admin.parameters.noOverrides')}</Typography>}
      <Stack spacing={1.5} divider={<Divider flexItem />}>
        {overrides.map((o) => (
          <Box key={`${o.id}-${o.version}`}>
            <Typography variant="body2" sx={{ fontFamily: 'monospace', mb: 0.5 }}>{o.scopeRefCode}</Typography>
            <ValueLine view={view} scope={scope} scopeRefId={o.scopeRefId} override={o} canEdit={canEdit} notSet="" />
          </Box>
        ))}
      </Stack>
      {canEdit && free.length > 0 && (
        <Box sx={{ mt: 2 }}>
          <TextField select size="small" fullWidth label={t(scope === 'WS_GROUP' ? 'admin.parameters.addForGroup' : 'admin.parameters.addForWorkstation')}
            value={ref} onChange={(e) => setRef(e.target.value)}>
            {free.map((o) => <MenuItem key={o.id} value={String(o.id)}>{o.code}</MenuItem>)}
          </TextField>
          {ref && (
            <Box sx={{ mt: 1.5 }}>
              <ValueLine key={`new-${ref}`} view={view} scope={scope} scopeRefId={Number(ref)} override={undefined} canEdit
                notSet="" onSaved={() => setRef('')} />
            </Box>
          )}
        </Box>
      )}
    </Paper>
  )
}

/** The editor for one value with its Save / Remove buttons and the server's message. */
function ValueLine({ view, scope, scopeRefId, override, canEdit, notSet, onSaved }: {
  view: ParameterView; scope: ParameterScope; scopeRefId: number | null; override: ParameterOverride | undefined;
  canEdit: boolean; notSet: string; onSaved?: () => void
}) {
  const { t } = useTranslation()
  const set = useSetParameter()
  const remove = useRemoveParameter()
  const [value, setValue] = useState(override?.value ?? view.defaultValue)
  const [error, setError] = useState<string | null>(null)

  if (!canEdit) {
    return <Typography>{override ? formatValue(t, view, override.value) : notSet}</Typography>
  }

  function fail(e: unknown) {
    setError(e instanceof ApiError && e.fieldErrors.value ? e.fieldErrors.value : e instanceof ApiError && e.fieldErrors.scopeRefId ? e.fieldErrors.scopeRefId : errorText(t, e))
  }

  return (
    <Stack spacing={1}>
      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start', flexWrap: 'wrap', rowGap: 1 }}>
        <Box sx={{ flexGrow: 1, minWidth: 180 }}>
          <Editor view={view} value={value} onChange={(v) => { setValue(v); setError(null) }} error={error} />
        </Box>
        <Button variant="contained" size="small" sx={{ mt: 0.5 }} disabled={set.isPending}
          onClick={() => set.mutate({ code: view.code, scope, scopeRefId, value, version: override?.version }, {
            onSuccess: () => { setError(null); onSaved?.() }, onError: fail })}>
          {t('md.save')}
        </Button>
        {override && (
          <Button size="small" color="secondary" sx={{ mt: 0.5 }} disabled={remove.isPending}
            onClick={() => remove.mutate({ code: view.code, scope, scopeRefId }, { onError: fail })}>
            {t('admin.parameters.remove')}
          </Button>
        )}
      </Stack>
      {!override && notSet && <Typography variant="caption" color="text.secondary">{notSet}</Typography>}
      {override && <Typography variant="caption" color="text.secondary">{t('admin.parameters.changedBy', { by: override.updatedBy })}</Typography>}
    </Stack>
  )
}

function Editor({ view, value, onChange, error }: { view: ParameterView; value: string; onChange: (v: string) => void; error: string | null }) {
  const { t } = useTranslation()
  const help = error ?? undefined
  switch (view.type) {
    case 'BOOL':
      return <Switch checked={value === 'true'} onChange={(e) => onChange(e.target.checked ? 'true' : 'false')} />
    case 'CHOICE':
      return (
        <TextField select size="small" fullWidth value={value} onChange={(e) => onChange(e.target.value)} error={Boolean(error)} helperText={help}>
          {view.choices.map((c) => <MenuItem key={c} value={c}>{c}</MenuItem>)}
        </TextField>
      )
    case 'DURATION': {
      const minutes = isoToMinutes(value)
      const inHours = minutes !== null && minutes >= 60 && minutes % 60 === 0
      return (
        <Stack direction="row" spacing={1}>
          <TextField size="small" type="number" value={minutes === null ? '' : inHours ? minutes / 60 : minutes} error={Boolean(error)} helperText={help}
            onChange={(e) => onChange(e.target.value === '' ? '' : minutesToIso(Math.round(Number(e.target.value) * (inHours ? 60 : 1))))} sx={{ width: 130 }} />
          <TextField select size="small" value={inHours ? 'h' : 'min'} sx={{ width: 120 }}
            onChange={(e) => {
              if (minutes !== null) onChange(minutesToIso(e.target.value === 'h' ? Math.max(60, Math.round(minutes / 60) * 60) : minutes))
            }}>
            <MenuItem value="min">{t('admin.parameters.unitMinutes')}</MenuItem>
            <MenuItem value="h">{t('admin.parameters.unitHours')}</MenuItem>
          </TextField>
        </Stack>
      )
    }
    case 'INT':
      return (
        <TextField size="small" type="number" fullWidth value={value} onChange={(e) => onChange(e.target.value)} error={Boolean(error)}
          helperText={help ?? (view.min !== null ? `${view.min} - ${view.max}${view.unit ? ` ${view.unit}` : ''}` : undefined)} />
      )
    default:
      return <TextField size="small" fullWidth value={value} onChange={(e) => onChange(e.target.value)} error={Boolean(error)} helperText={help} />
  }
}
