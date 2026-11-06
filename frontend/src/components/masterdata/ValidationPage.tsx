import CheckCircleIcon from '@mui/icons-material/CheckCircle'
import ErrorIcon from '@mui/icons-material/Error'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import RefreshIcon from '@mui/icons-material/Refresh'
import WarningIcon from '@mui/icons-material/Warning'
import {
  Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Chip, CircularProgress, List, ListItem,
  ListItemText, Paper, Stack, Typography,
} from '@mui/material'
import { useTranslation } from 'react-i18next'
import { Link as RouterLink } from 'react-router'
import { useValidationReport, type ValidationItem, type ValidationRule } from '../../api/importExport'
import { errorText } from './errorText'

type Status = 'PASS' | 'WARNING' | 'ERROR'

/** Where the record of a finding can be opened (the list page opens its drawer for ?open=<id>). */
const PAGE_OF: Record<string, string> = {
  location: '/masterdata/locations',
  'location-link': '/masterdata/location-links',
  'location-type': '/masterdata/location-types',
  'hu-type': '/masterdata/hu-types',
  equipment: '/masterdata/equipment',
}

const ICON = { PASS: <CheckCircleIcon color="success" />, WARNING: <WarningIcon color="warning" />, ERROR: <ErrorIcon color="error" /> }
const COLOR = { PASS: 'success', WARNING: 'warning', ERROR: 'error' } as const

function StatusChip({ status }: { status: Status }) {
  const { t } = useTranslation()
  return <Chip size="small" color={COLOR[status]} icon={status === 'PASS' ? <CheckCircleIcon /> : status === 'ERROR' ? <ErrorIcon /> : <WarningIcon />} label={t(`md.validation.status.${status}`)} />
}

function ItemRow({ item }: { item: ValidationItem }) {
  const { t } = useTranslation()
  const page = item.entityType ? PAGE_OF[item.entityType] : undefined
  const link = page && item.entityId !== null ? `${page}?open=${item.entityId}` : undefined
  return (
    <ListItem divider sx={{ alignItems: 'flex-start', gap: 1.5 }}>
      <Box sx={{ pt: 0.5 }}>{ICON[item.severity]}</Box>
      <ListItemText
        primary={
          link
            ? <RouterLink to={link} style={{ fontWeight: 600 }}>{item.code ?? t('md.validation.openRecord')}</RouterLink>
            : <Typography sx={{ fontWeight: 600 }}>{item.code ?? t('md.validation.wholePlant')}</Typography>
        }
        secondary={item.message}
      />
    </ListItem>
  )
}

function RuleBlock({ rule }: { rule: ValidationRule }) {
  const { t } = useTranslation()
  const title = t(`md.validation.rules.${rule.code}`, { defaultValue: rule.title })
  return (
    <Accordion variant="outlined" disableGutters defaultExpanded={rule.status === 'ERROR'}>
      <AccordionSummary expandIcon={<ExpandMoreIcon />}>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
          <StatusChip status={rule.status} />
          <Typography sx={{ fontWeight: 600 }}>{title}</Typography>
          <Chip size="small" variant="outlined" label={t('md.validation.count', { count: rule.count })} />
        </Stack>
      </AccordionSummary>
      <AccordionDetails sx={{ p: 0 }}>
        <List disablePadding>{rule.items.map((i, n) => <ItemRow key={`${i.entityId}-${n}`} item={i} />)}</List>
        {rule.truncated && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', p: 2 }}>
            {t('md.validation.truncated', { shown: rule.items.length, count: rule.count })}
          </Typography>
        )}
      </AccordionDetails>
    </Accordion>
  )
}

/** "Check configuration": PASS / WARNING / ERROR per rule with links to the records. Reads only - changes nothing. */
export default function ValidationPage() {
  const { t } = useTranslation()
  const report = useValidationReport()
  const data = report.data

  const problems = data?.rules.filter((r) => r.status !== 'PASS') ?? []
  const order = { ERROR: 0, WARNING: 1, PASS: 2 }
  problems.sort((a, b) => order[a.status] - order[b.status])
  const passed = data?.rules.filter((r) => r.status === 'PASS') ?? []

  return (
    <>
      <Stack direction="row" spacing={2} sx={{ mb: 1, alignItems: 'center' }}>
        <Typography variant="h4" component="h1" sx={{ flexGrow: 1 }}>{t('md.validation.title')}</Typography>
        <Button variant="contained" startIcon={report.isFetching ? <CircularProgress size={16} color="inherit" /> : <RefreshIcon />}
          disabled={report.isFetching} onClick={() => void report.refetch()}>
          {t('md.validation.run')}
        </Button>
      </Stack>
      <Typography color="text.secondary" sx={{ mb: 2 }}>{t('md.validation.intro')}</Typography>

      {report.isError && <Alert severity="error">{errorText(t, report.error)}</Alert>}
      {report.isLoading && <CircularProgress />}

      {data && (
        <>
          <Paper variant="outlined" sx={{ p: 2.5, mb: 3 }}>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ alignItems: { sm: 'center' } }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexGrow: 1 }}>
                {ICON[data.status]}
                <Box>
                  <Typography variant="h6">{t(`md.validation.overall.${data.status}`)}</Typography>
                  <Typography variant="body2" color="text.secondary">
                    {t('md.validation.summary', {
                      errors: data.rules.filter((r) => r.status === 'ERROR').length,
                      warnings: data.rules.filter((r) => r.status === 'WARNING').length,
                      passed: passed.length,
                    })}
                  </Typography>
                </Box>
              </Box>
              <Typography variant="body2" color="text.secondary">
                {t('md.validation.checked', {
                  locations: data.counts.locations ?? 0, links: data.counts.links ?? 0,
                  equipment: data.counts.equipment ?? 0, ms: data.durationMs,
                })}
              </Typography>
            </Stack>
          </Paper>

          <Stack spacing={1}>
            {problems.map((r) => <RuleBlock key={r.code} rule={r} />)}
          </Stack>

          {passed.length > 0 && (
            <>
              <Typography variant="h6" sx={{ mt: 3, mb: 1 }}>{t('md.validation.passedTitle', { count: passed.length })}</Typography>
              <Paper variant="outlined">
                <List dense disablePadding>
                  {passed.map((r) => (
                    <ListItem key={r.code} divider>
                      <Box sx={{ mr: 1.5, display: 'flex' }}>{ICON.PASS}</Box>
                      <ListItemText primary={t(`md.validation.rules.${r.code}`, { defaultValue: r.title })} />
                    </ListItem>
                  ))}
                </List>
              </Paper>
            </>
          )}
        </>
      )}
    </>
  )
}
