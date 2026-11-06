import CloseIcon from '@mui/icons-material/Close'
import {
  Alert, Box, Button, Chip, IconButton, LinearProgress, MenuItem, Paper, Stack, TextField, Typography,
} from '@mui/material'
import { useTheme } from '@mui/material/styles'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { useRackFace, useRacks, type RackCell, type RackFace } from '../../api/layout'
import { errorText } from '../masterdata/errorText'
import { StateLegend, StatePatterns } from './StateLegend'
import { cellFill } from './stateUtils'

const CELL_W = 20
const CELL_H = 24
const LEFT = 34
const TOP = 26

const keyOf = (aisle: string, side: string | null) => `${aisle}|${side ?? ''}`

/**
 * The front of one aisle side as a grid: column number across, level number up, one cell per location, coloured by
 * state. Made for pallet racks of about 74 columns x 12 levels.
 */
export default function RackFaceView() {
  const { t } = useTranslation()
  const racks = useRacks()
  const [chosen, setChosen] = useState<string | null>(null)

  const list = racks.data ?? []
  const current = chosen !== null ? list.find((r) => keyOf(r.aisle, r.side) === chosen) : list[0]
  const face = useRackFace(current?.aisle, current?.side)

  if (racks.isError) return <Alert severity="error">{errorText(t, racks.error)}</Alert>
  if (racks.isSuccess && list.length === 0) return <Alert severity="info">{t('md.layout.noRacks')}</Alert>
  if (!current) return null

  return (
    <>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
        <TextField select size="small" label={t('md.layout.rack')} value={keyOf(current.aisle, current.side)} sx={{ minWidth: 300 }}
          onChange={(e) => setChosen(e.target.value)}>
          {list.map((r) => (
            <MenuItem key={keyOf(r.aisle, r.side)} value={keyOf(r.aisle, r.side)}>
              {t('md.layout.rackItem', { aisle: r.aisle, side: r.side ?? '-', count: r.locations })}
            </MenuItem>
          ))}
        </TextField>
      </Stack>
      {face.isError && <Alert severity="error">{errorText(t, face.error)}</Alert>}
      {face.data && <FaceGrid key={keyOf(face.data.aisle, face.data.side)} face={face.data} />}
    </>
  )
}

function FaceGrid({ face }: { face: RackFace }) {
  const { t } = useTranslation()
  const { palette } = useTheme()
  const navigate = useNavigate()
  const [selected, setSelected] = useState<RackCell | null>(null)

  const { summary } = face
  const columns = face.maxColumn - face.minColumn + 1
  const levels = face.maxLevel - face.minLevel + 1
  const grid = useMemo(() => {
    const m = new Map<string, RackCell[]>()
    face.cells.forEach((c) => {
      const k = `${c.column}/${c.level}`
      m.set(k, [...(m.get(k) ?? []), c])
    })
    return m
  }, [face.cells])

  const blocked = summary.blockedIn + summary.blockedOut + summary.blockedBoth
  // rounded DOWN: 884 of 888 usable must not read "100%"
  const share = summary.total === 0 ? 0 : Math.floor((summary.usable / summary.total) * 100)
  const width = LEFT + columns * CELL_W + 8
  const height = TOP + levels * CELL_H + 8
  const columnNumbers = Array.from({ length: columns }, (_, i) => face.minColumn + i)

  return (
    <>
      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={3} sx={{ alignItems: { md: 'center' } }}>
          <Box sx={{ minWidth: 260 }}>
            <Typography variant="h6">{t('md.layout.fillTitle')}</Typography>
            <Typography variant="h4" component="div" sx={{ fontWeight: 700 }}>
              {face.fill.available && face.fill.occupied !== null
                ? `${Math.round((face.fill.occupied / Math.max(face.fill.capacity, 1)) * 100)}%`
                : `${share}%`}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {face.fill.available && face.fill.occupied !== null
                ? t('md.layout.occupied', { occupied: face.fill.occupied, capacity: face.fill.capacity })
                : t('md.layout.usable', { usable: summary.usable, total: summary.total })}
            </Typography>
            {!face.fill.available && <Typography variant="caption" color="text.secondary">{t('md.layout.occupancyLater')}</Typography>}
            <LinearProgress variant="determinate" value={share} sx={{ mt: 1, height: 8, borderRadius: 4 }} aria-label={t('md.layout.fillTitle')} />
          </Box>
          <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
            <Chip label={t('md.layout.sumTotal', { count: summary.total })} />
            <Chip variant="outlined" label={t('md.layout.sumDisabled', { count: summary.disabled })} />
            <Chip variant="outlined" label={t('md.layout.sumBlocked', { count: blocked })} />
            <Chip variant="outlined" label={t('md.layout.sumRange', { c1: face.minColumn, c2: face.maxColumn, l1: face.minLevel, l2: face.maxLevel })} />
          </Stack>
        </Stack>
      </Paper>

      {summary.duplicates > 0 && <Alert severity="warning" sx={{ mb: 2 }}>{t('md.layout.duplicates', { count: summary.duplicates })}</Alert>}
      {summary.unplaced > 0 && <Alert severity="info" sx={{ mb: 2 }}>{t('md.layout.unplaced', { count: summary.unplaced })}</Alert>}

      <Paper variant="outlined" sx={{ position: 'relative', overflow: 'auto', maxHeight: '70vh' }}>
        <svg width={width} height={height} role="img" aria-label={t('md.layout.rackLabel', { aisle: face.aisle, side: face.side ?? '-' })}>
          <StatePatterns prefix="rf" />
          {columnNumbers.map((c, i) => (
            (c === face.minColumn || c % 5 === 0 || (c === face.maxColumn && c % 5 !== 1)) && (
              <text key={c} x={LEFT + i * CELL_W + CELL_W / 2} y={TOP - 8} textAnchor="middle" fontSize="10" fill={palette.text.secondary}>{c}</text>
            )
          ))}
          {Array.from({ length: levels }, (_, i) => {
            const level = face.maxLevel - i // highest level at the top
            return (
              <text key={level} x={LEFT - 8} y={TOP + i * CELL_H + CELL_H / 2 + 4} textAnchor="end" fontSize="11" fill={palette.text.secondary}>{level}</text>
            )
          })}
          {columnNumbers.flatMap((c, ci) => Array.from({ length: levels }, (_, li) => {
            const level = face.maxLevel - li
            const cells = grid.get(`${c}/${level}`)
            const x = LEFT + ci * CELL_W + 1, y = TOP + li * CELL_H + 1
            if (!cells) {
              return <rect key={`${c}/${level}`} x={x} y={y} width={CELL_W - 2} height={CELL_H - 2} rx="2" fill="none" stroke={palette.layout.empty} />
            }
            const cell = cells[0]
            const dup = cells.length > 1
            return (
              <g key={`${c}/${level}`} style={{ cursor: 'pointer' }} onClick={() => setSelected(cell)}>
                <title>{`${cells.map((x2) => x2.code).join(', ')} - ${t(`md.layout.state.${cell.state}`)}${dup ? ` - ${t('md.layout.dupCell')}` : ''}`}</title>
                <rect x={x} y={y} width={CELL_W - 2} height={CELL_H - 2} rx="2"
                  fill={cellFill(cell.state, 'rf', palette.layout.ok, palette.layout.blockedIn)}
                  stroke={dup ? palette.layout.conflict : cell.state === 'DISABLED' ? palette.layout.disabledLine : 'rgba(0,0,0,0.25)'}
                  strokeWidth={dup ? 2.5 : 1} strokeDasharray={cell.state === 'DISABLED' ? '3 2' : undefined} />
                {selected?.id === cell.id && <rect x={x - 1} y={y - 1} width={CELL_W} height={CELL_H} rx="3" fill="none" stroke={palette.layout.selected} strokeWidth="2.5" />}
              </g>
            )
          }))}
        </svg>
      </Paper>

      <Stack spacing={1.5} sx={{ mt: 2 }}>
        <StateLegend />
        <Typography variant="caption" color="text.secondary">{t('md.layout.rackHelp')}</Typography>
      </Stack>

      {selected && (
        <Paper elevation={3} sx={{ position: 'fixed', right: 24, bottom: 24, p: 2, width: 280, zIndex: 5 }}>
          <Box sx={{ display: 'flex', alignItems: 'flex-start' }}>
            <Typography variant="h6" sx={{ flexGrow: 1, wordBreak: 'break-all' }}>{selected.code}</Typography>
            <IconButton size="small" aria-label={t('md.close')} onClick={() => setSelected(null)}><CloseIcon fontSize="small" /></IconButton>
          </Box>
          <Typography variant="body2">{t('md.layout.cellPosition', { column: selected.column, level: selected.level })}</Typography>
          <Typography variant="body2" sx={{ mb: 1.5 }}>{t('md.layout.stateLabel')}: {t(`md.layout.state.${selected.state}`)}</Typography>
          <Button size="small" variant="contained" onClick={() => navigate(`/masterdata/locations?open=${selected.id}`)}>{t('md.layout.openRecord')}</Button>
        </Paper>
      )}
    </>
  )
}
