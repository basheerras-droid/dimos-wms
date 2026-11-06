import CheckCircleIcon from '@mui/icons-material/CheckCircleOutlined'
import DownloadIcon from '@mui/icons-material/Download'
import ErrorIcon from '@mui/icons-material/ErrorOutlined'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import UploadFileIcon from '@mui/icons-material/UploadFile'
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Card, CardContent, Chip, Dialog,
  DialogActions, DialogContent, DialogContentText, DialogTitle, MenuItem, Paper, Stack, Step, StepLabel,
  Stepper, Table, TableBody, TableCell, TableHead, TableRow, TextField, ToggleButton, Typography,
} from '@mui/material'
import { DataGrid, type GridColDef, type GridRenderCellParams } from '@mui/x-data-grid'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ApiError } from '../../api/client'
import {
  downloadExport, downloadTemplate, sendImport, useImportEntities,
  type ImportResult, type ImportRowResult, type ImportStatus,
} from '../../api/importExport'
import { errorText } from './errorText'

const STATUS_COLOR: Record<ImportStatus, 'success' | 'info' | 'default' | 'error'> = {
  CREATE: 'success', ADD: 'success', UPDATE: 'info', UNCHANGED: 'default', ERROR: 'error',
}

/**
 * Import from a spreadsheet in three steps: choose the table and get the template, upload a file and check it
 * (nothing is changed), then apply it (all rows or nothing).
 */
export default function ImportPage() {
  const { t } = useTranslation()
  const entities = useImportEntities()
  const input = useRef<HTMLInputElement>(null)

  const [entityKey, setEntityKey] = useState('areas')
  const [file, setFile] = useState<File | null>(null)
  const [checked, setChecked] = useState<ImportResult | null>(null) // result of the last check or apply
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirm, setConfirm] = useState(false)
  const [onlyErrors, setOnlyErrors] = useState(false)
  const [done, setDone] = useState<ImportResult | null>(null)

  const list = entities.data ?? []
  const entity = list.find((e) => e.key === entityKey)
  const title = (key: string, fallback: string) => t(`md.import.entities.${key}`, { defaultValue: fallback })

  function reset() {
    setChecked(null)
    setDone(null)
    setError(null)
  }

  async function run(dryRun: boolean) {
    if (!file) return
    setBusy(true)
    setError(null)
    try {
      const result = await sendImport(entityKey, file, dryRun)
      if (dryRun) {
        setChecked(result)
        setDone(null)
      } else if (result.applied) {
        setDone(result)
        setChecked(null)
        setFile(null)
        if (input.current) input.current.value = ''
      } else {
        setChecked(result) // refused: show which rows are wrong
        setError(t('md.import.refused'))
      }
    } catch (e) {
      setError(e instanceof ApiError ? `${errorText(t, e)} ${e.fieldErrors.file ?? ''}`.trim() : errorText(t, e))
    } finally {
      setBusy(false)
      setConfirm(false)
    }
  }

  async function download(fn: () => Promise<void>) {
    try {
      await fn()
    } catch (e) {
      setError(errorText(t, e))
    }
  }

  const problems = checked ? checked.errors : 0
  const changes = checked ? checked.created + checked.updated + checked.added : 0
  const canApply = checked !== null && checked.dryRun && problems === 0 && changes > 0 && file !== null
  const step = done ? 3 : checked ? (canApply ? 2 : 1) : file ? 1 : 0

  const columns: GridColDef<ImportRowResult>[] = [
    { field: 'row', headerName: t('md.import.row'), width: 90, type: 'number' },
    {
      field: 'status', headerName: t('md.import.result'), width: 150,
      renderCell: (p: GridRenderCellParams<ImportRowResult>) => (
        <Chip size="small" color={STATUS_COLOR[p.row.status]} variant={p.row.status === 'UNCHANGED' ? 'outlined' : 'filled'}
          icon={p.row.status === 'ERROR' ? <ErrorIcon /> : undefined} label={t(`md.import.status.${p.row.status}`)} />
      ),
    },
    { field: 'key', headerName: t('md.import.record'), flex: 1, minWidth: 160 },
    { field: 'message', headerName: t('md.import.message'), flex: 2, minWidth: 260 },
  ]
  const rows = (checked?.rows ?? []).filter((r) => !onlyErrors || r.status === 'ERROR')

  return (
    <>
      <Typography variant="h4" component="h1" gutterBottom>{t('md.import.title')}</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>{t('md.import.intro')}</Typography>

      <Stepper activeStep={step} alternativeLabel sx={{ mb: 3 }}>
        {['stepChoose', 'stepCheck', 'stepApply'].map((k) => <Step key={k}><StepLabel>{t(`md.import.${k}`)}</StepLabel></Step>)}
      </Stepper>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>{error}</Alert>}
      {done && (
        <Alert severity="success" icon={<CheckCircleIcon />} sx={{ mb: 2 }}>
          {t('md.import.applied', { created: done.created, updated: done.updated, added: done.added, unchanged: done.unchanged })}
        </Alert>
      )}

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>{t('md.import.step1')}</Typography>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'center' } }}>
            <TextField select size="small" label={t('md.import.table')} value={entityKey} sx={{ minWidth: 300 }}
              onChange={(e) => { setEntityKey(e.target.value); setFile(null); reset(); if (input.current) input.current.value = '' }}>
              {list.map((e, i) => <MenuItem key={e.key} value={e.key}>{i + 1}. {title(e.key, e.title)}</MenuItem>)}
            </TextField>
            <Button variant="outlined" color="secondary" startIcon={<DownloadIcon />} onClick={() => void download(() => downloadTemplate(entityKey, 'csv'))}>
              {t('md.import.templateCsv')}
            </Button>
            <Button variant="outlined" color="secondary" startIcon={<DownloadIcon />} onClick={() => void download(() => downloadTemplate(entityKey, 'xlsx'))}>
              {t('md.import.templateXlsx')}
            </Button>
            <Button variant="text" color="secondary" onClick={() => void download(() => downloadExport(entityKey, 'xlsx'))}>
              {t('md.import.exportCurrent')}
            </Button>
          </Stack>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 1.5 }}>{t('md.import.orderHint')}</Typography>

          {entity && (
            <Accordion variant="outlined" disableGutters sx={{ mt: 2 }}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>{t('md.import.columnsTitle', { count: entity.columns.length })}</AccordionSummary>
              <AccordionDetails sx={{ overflowX: 'auto' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>{t('md.import.colName')}</TableCell>
                      <TableCell>{t('md.import.colRequired')}</TableCell>
                      <TableCell>{t('md.import.colExample')}</TableCell>
                      <TableCell>{t('md.import.colMeaning')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {entity.columns.map((c) => (
                      <TableRow key={c.name}>
                        <TableCell sx={{ fontFamily: 'monospace' }}>{c.name}</TableCell>
                        <TableCell>{c.required ? t('md.yes') : t('md.no')}</TableCell>
                        <TableCell sx={{ fontFamily: 'monospace' }}>{c.example}</TableCell>
                        <TableCell>{c.help}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </AccordionDetails>
            </Accordion>
          )}
        </CardContent>
      </Card>

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Typography variant="h6" gutterBottom>{t('md.import.step2')}</Typography>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' } }}>
            <input ref={input} type="file" hidden accept=".csv,.txt,.xlsx"
              onChange={(e) => { setFile(e.target.files?.[0] ?? null); reset() }} />
            <Button variant="outlined" color="secondary" startIcon={<UploadFileIcon />} onClick={() => input.current?.click()}>
              {t('md.import.chooseFile')}
            </Button>
            <Typography sx={{ flexGrow: 1 }} color={file ? 'text.primary' : 'text.secondary'}>
              {file ? `${file.name} (${Math.max(1, Math.round(file.size / 1024))} KB)` : t('md.import.noFile')}
            </Typography>
            <Button variant="contained" disabled={!file || busy} onClick={() => void run(true)}>
              {busy && !confirm ? t('md.import.checking') : t('md.import.check')}
            </Button>
          </Stack>

          {checked && (
            <Box sx={{ mt: 3 }}>
              <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1, mb: 2, alignItems: 'center' }}>
                <Chip color="success" label={t('md.import.countCreated', { count: checked.created })} />
                <Chip color="info" label={t('md.import.countUpdated', { count: checked.updated })} />
                {checked.added > 0 && <Chip color="success" label={t('md.import.countAdded', { count: checked.added })} />}
                <Chip variant="outlined" label={t('md.import.countUnchanged', { count: checked.unchanged })} />
                <Chip color={problems > 0 ? 'error' : 'default'} variant={problems > 0 ? 'filled' : 'outlined'}
                  icon={problems > 0 ? <ErrorIcon /> : undefined} label={t('md.import.countErrors', { count: problems })} />
                {checked.errors > 0 && (
                  <ToggleButton size="small" value="errors" selected={onlyErrors} onChange={() => setOnlyErrors((v) => !v)} sx={{ ml: 1 }}>
                    {t('md.import.onlyErrors')}
                  </ToggleButton>
                )}
              </Stack>

              {checked.fileErrors.length > 0 && (
                <Alert severity="error" sx={{ mb: 2 }}>
                  <Typography sx={{ fontWeight: 600 }}>{t('md.import.fileProblems')}</Typography>
                  <ul style={{ margin: 0, paddingLeft: 20 }}>{checked.fileErrors.map((m) => <li key={m}>{m}</li>)}</ul>
                </Alert>
              )}
              {checked.dryRun && problems === 0 && changes === 0 && checked.fileErrors.length === 0 && (
                <Alert severity="info" sx={{ mb: 2 }}>{t('md.import.nothingToDo')}</Alert>
              )}
              {checked.dryRun && canApply && <Alert severity="success" sx={{ mb: 2 }}>{t('md.import.readyToApply')}</Alert>}
              {problems > 0 && checked.fileErrors.length === 0 && <Alert severity="warning" sx={{ mb: 2 }}>{t('md.import.fixFirst')}</Alert>}
              {checked.truncated && <Alert severity="info" sx={{ mb: 2 }}>{t('md.import.truncated', { count: checked.rows.length })}</Alert>}

              {checked.rows.length > 0 && (
                <Paper variant="outlined">
                  <DataGrid<ImportRowResult>
                    rows={rows} columns={columns} getRowId={(r) => r.row} autoHeight density="compact" disableRowSelectionOnClick
                    pageSizeOptions={[25, 50, 100]} initialState={{ pagination: { paginationModel: { pageSize: 25 } } }}
                    localeText={{ noRowsLabel: t('md.noRows') }} sx={{ border: 0 }}
                  />
                </Paper>
              )}
            </Box>
          )}
        </CardContent>
      </Card>

      <Card>
        <CardContent>
          <Typography variant="h6" gutterBottom>{t('md.import.step3')}</Typography>
          <Typography color="text.secondary" sx={{ mb: 2 }}>{t('md.import.applyHelp')}</Typography>
          <Button variant="contained" disabled={!canApply || busy} onClick={() => setConfirm(true)}>{t('md.import.apply')}</Button>
        </CardContent>
      </Card>

      <Dialog open={confirm} onClose={() => !busy && setConfirm(false)}>
        <DialogTitle>{t('md.import.confirmTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>
            {t('md.import.confirmText', {
              table: entity ? title(entity.key, entity.title) : '', created: checked?.created ?? 0,
              updated: checked?.updated ?? 0, added: checked?.added ?? 0,
            })}
          </DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button color="secondary" disabled={busy} onClick={() => setConfirm(false)}>{t('md.cancel')}</Button>
          <Button variant="contained" disabled={busy} onClick={() => void run(false)}>
            {busy ? t('md.import.applying') : t('md.import.apply')}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  )
}
