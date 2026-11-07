import CheckCircleIcon from '@mui/icons-material/CheckCircleOutlined'
import ErrorIcon from '@mui/icons-material/ErrorOutlined'
import {
  Alert, Box, Button, Card, CardContent, Chip, FormControlLabel, MenuItem, Radio, RadioGroup, Stack, Table,
  TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import {
  loadProject, useProjectPreview, useProjects, type ProjectMode, type ProjectResult,
} from '../../api/projects'
import { errorText } from './errorText'

/** Counts that "Replace" deletes, in the order they are shown. */
const COUNT_KEYS = [
  'areas', 'locationTypes', 'huTypes', 'huTypeNesting', 'locationTypeHuTypes', 'locations', 'locationLinks',
  'equipment', 'equipmentLocations', 'customEquipmentTypes',
]

/**
 * Load a project data set (SUPER_ADMIN). "Replace master data" deletes all master data first and loads the data set in
 * one step (everything or nothing); "Add / update" only adds and changes. Always check first: the check does the whole
 * load and then rolls it back.
 */
export default function ProjectDataPage() {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const projects = useProjects()
  const [code, setCode] = useState<string | null>(null)
  const selected = code ?? projects.data?.[0]?.code ?? null
  const preview = useProjectPreview(selected)
  const [mode, setMode] = useState<ProjectMode>('REPLACE')
  const [confirm, setConfirm] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<ProjectResult | null>(null)

  const project = preview.data?.project
  const existing = preview.data?.existing ?? {}
  const replace = mode === 'REPLACE'
  const nameOk = !replace || (project !== undefined && confirm.trim().toLowerCase() === project.name.trim().toLowerCase())

  const run = async (dryRun: boolean) => {
    if (!selected) return
    setBusy(true)
    setError(null)
    try {
      const r = await loadProject(selected, mode, dryRun, confirm)
      setResult(r)
      if (r.applied) {
        setConfirm('')
        qc.invalidateQueries() // every list and the layout show other data now
      }
    } catch (e) {
      setResult(null)
      setError(errorText(t, e))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Stack spacing={2.5} sx={{ maxWidth: 1000 }}>
      <Typography variant="h1">{t('md.project.title')}</Typography>
      <Typography color="text.secondary">{t('md.project.intro')}</Typography>

      {projects.data?.length === 0 && <Alert severity="info">{t('md.project.none')}</Alert>}

      {projects.data && projects.data.length > 0 && (
        <Card variant="outlined">
          <CardContent>
            <Stack spacing={2}>
              <TextField
                select size="small" label={t('md.project.dataSet')} value={selected ?? ''} sx={{ maxWidth: 420 }}
                onChange={(e) => { setCode(e.target.value); setResult(null); setConfirm('') }}
              >
                {projects.data.map((p) => <MenuItem key={p.code} value={p.code}>{p.name} ({p.code})</MenuItem>)}
              </TextField>
              {project && (
                <>
                  <Typography>{project.description}</Typography>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>{t('md.project.fileType')}</TableCell>
                        <TableCell>{t('md.project.file')}</TableCell>
                        <TableCell align="right">{t('md.project.rows')}</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {project.files.map((f) => (
                        <TableRow key={f.type}>
                          <TableCell>{f.type}</TableCell>
                          <TableCell>{f.file}</TableCell>
                          <TableCell align="right">{f.rows < 0 ? '?' : f.rows.toLocaleString()}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </>
              )}
            </Stack>
          </CardContent>
        </Card>
      )}

      {project && (
        <Card variant="outlined">
          <CardContent>
            <Stack spacing={2}>
              <Typography variant="h2">{t('md.project.modeTitle')}</Typography>
              <RadioGroup value={mode} onChange={(e) => { setMode(e.target.value as ProjectMode); setResult(null); setConfirm('') }}>
                <FormControlLabel value="REPLACE" control={<Radio />} label={t('md.project.modeReplace')} />
                <FormControlLabel value="UPSERT" control={<Radio />} label={t('md.project.modeUpsert')} />
              </RadioGroup>

              {replace && (
                <Alert severity="warning">
                  <Typography sx={{ fontWeight: 600, mb: 1 }}>{t('md.project.willDelete')}</Typography>
                  <Box component="ul" sx={{ m: 0, pl: 3 }}>
                    {COUNT_KEYS.map((k) => (
                      <li key={k}>{t(`md.project.count.${k}`)}: <b>{(existing[k] ?? 0).toLocaleString()}</b></li>
                    ))}
                  </Box>
                  <Typography sx={{ mt: 1 }}>
                    {t('md.project.workstationsNote', { count: existing.workstationsWithLocation ?? 0 })}
                  </Typography>
                  <Typography sx={{ mt: 1 }}>{t('md.project.keeps')}</Typography>
                </Alert>
              )}

              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' } }}>
                <Button variant="outlined" disabled={busy} onClick={() => run(true)}>
                  {busy ? t('md.project.working') : t('md.project.check')}
                </Button>
                {replace && (
                  <TextField
                    size="small" value={confirm} onChange={(e) => setConfirm(e.target.value)} sx={{ minWidth: 260 }}
                    label={t('md.project.typeName', { name: project.name })}
                  />
                )}
                <Button variant="contained" disabled={busy || !nameOk} onClick={() => run(false)}>
                  {replace ? t('md.project.applyReplace') : t('md.project.applyUpsert')}
                </Button>
              </Stack>
              <Typography variant="caption" color="text.secondary">{t('md.project.checkHint')}</Typography>
            </Stack>
          </CardContent>
        </Card>
      )}

      {error && <Alert severity="error">{error}</Alert>}

      {result && <ResultView result={result} />}
    </Stack>
  )
}

function ResultView({ result }: { result: ProjectResult }) {
  const { t } = useTranslation()
  const failed = result.problems.length > 0 || result.steps.some((s) => s.errors > 0)
  return (
    <Card variant="outlined">
      <CardContent>
        <Stack spacing={2}>
          {result.applied ? (
            <Alert severity="success" icon={<CheckCircleIcon />}>{t('md.project.done', { seconds: (result.durationMs / 1000).toFixed(1) })}</Alert>
          ) : failed ? (
            <Alert severity="error" icon={<ErrorIcon />}>{t('md.project.failed')}</Alert>
          ) : (
            <Alert severity="info">{t('md.project.checkedOnly', { seconds: (result.durationMs / 1000).toFixed(1) })}</Alert>
          )}
          {result.problems.map((p) => <Alert key={p} severity="error">{p}</Alert>)}

          {Object.keys(result.deleted).length > 0 && (
            <Typography>
              {t(result.applied ? 'md.project.deleted' : 'md.project.wouldDelete')}{' '}
              {COUNT_KEYS.map((k) => `${t(`md.project.count.${k}`)} ${(result.deleted[k] ?? 0).toLocaleString()}`).join(', ')}
            </Typography>
          )}

          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('md.project.fileType')}</TableCell>
                <TableCell align="right">{t('md.project.created')}</TableCell>
                <TableCell align="right">{t('md.project.updated')}</TableCell>
                <TableCell align="right">{t('md.project.unchanged')}</TableCell>
                <TableCell align="right">{t('md.project.errors')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {result.steps.map((s) => (
                <TableRow key={s.type}>
                  <TableCell>
                    {s.type}
                    {s.messages.map((m) => <Typography key={m} variant="caption" color="error" sx={{ display: 'block' }}>{m}</Typography>)}
                  </TableCell>
                  <TableCell align="right">{s.created.toLocaleString()}</TableCell>
                  <TableCell align="right">{s.updated.toLocaleString()}</TableCell>
                  <TableCell align="right">{s.unchanged.toLocaleString()}</TableCell>
                  <TableCell align="right">{s.errors > 0 ? <Chip size="small" color="error" label={s.errors} /> : 0}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {result.check && (
            <Alert severity={result.check.status === 'PASS' ? 'success' : result.check.status === 'WARNING' ? 'warning' : 'error'}>
              {t('md.project.configCheck', { status: t(`md.validation.status.${result.check.status}`) })}
              {result.check.messages.map((m) => <Typography key={m} variant="body2">{m}</Typography>)}
            </Alert>
          )}
        </Stack>
      </CardContent>
    </Card>
  )
}
