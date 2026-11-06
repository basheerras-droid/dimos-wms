import AddIcon from '@mui/icons-material/Add'
import CloseIcon from '@mui/icons-material/Close'
import FitScreenIcon from '@mui/icons-material/FitScreen'
import RemoveIcon from '@mui/icons-material/Remove'
import {
  Alert, Box, Button, Chip, IconButton, MenuItem, Paper, Stack, TextField, Tooltip, Typography,
} from '@mui/material'
import { useTheme } from '@mui/material/styles'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { useNavigate } from 'react-router'
import { usePlan, type Plan, type PlanLocation } from '../../api/layout'
import { errorText } from '../masterdata/errorText'
import { StateLegend } from './StateLegend'

/** The SVG is always 1000 x 600 "screen units"; the view decides which part of the plant (in mm) is shown. */
const W = 1000
const H = 600
interface View {
  x: number
  y: number
  w: number
}

/** 2D plan of one level: locations by x / y coloured by area, links as lines, zoom and pan, click to open. */
export default function PlanView() {
  const { t } = useTranslation()
  const [level, setLevel] = useState<number | undefined>(undefined)
  const plan = usePlan(level)

  if (plan.isError) return <Alert severity="error">{errorText(t, plan.error)}</Alert>
  if (!plan.data) return null
  const data = plan.data
  if (data.level === null) return <Alert severity="info">{t('md.layout.noLocations')}</Alert>

  return (
    <>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
        <TextField select size="small" label={t('md.fields.levelNo')} value={data.level} sx={{ minWidth: 130 }}
          onChange={(e) => setLevel(Number(e.target.value))}>
          {data.levels.map((l) => <MenuItem key={l} value={l}>{l}</MenuItem>)}
        </TextField>
        <Typography variant="body2" color="text.secondary">
          {t('md.layout.planCounts', { locations: data.locations.length, links: data.links.length })}
        </Typography>
      </Stack>
      {data.withoutPosition > 0 && <Alert severity="warning" sx={{ mb: 2 }}>{t('md.layout.withoutPosition', { count: data.withoutPosition })}</Alert>}
      {data.crossLevelLinks > 0 && <Alert severity="info" sx={{ mb: 2 }}>{t('md.layout.crossLevel', { count: data.crossLevelLinks })}</Alert>}
      {data.locations.length === 0
        ? <Alert severity="info">{t('md.layout.nothingOnLevel')}</Alert>
        // a new canvas (fresh zoom) for every level
        : <PlanCanvas key={data.level} plan={data} />}
    </>
  )
}

function bounds(locations: PlanLocation[]) {
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity
  for (const l of locations) {
    minX = Math.min(minX, l.x); maxX = Math.max(maxX, l.x)
    minY = Math.min(minY, -l.y); maxY = Math.max(maxY, -l.y) // +y points up on the plan
  }
  return { minX, maxX, minY, maxY }
}

/** A typical distance between neighbours (mm), to size the markers. */
function spacingOf(locations: PlanLocation[]): number {
  const b = bounds(locations)
  const width = Math.max(b.maxX - b.minX, 1)
  const height = Math.max(b.maxY - b.minY, width * 0.02, 1)
  return Math.max(Math.sqrt((width * height) / Math.max(locations.length, 1)), 50)
}

function fit(locations: PlanLocation[]): View {
  const b = bounds(locations)
  const spacing = spacingOf(locations)
  const width = b.maxX - b.minX, height = b.maxY - b.minY
  const w = Math.max(width * 1.16, (height * 1.16) / (H / W), spacing * 8)
  const cx = (b.minX + b.maxX) / 2, cy = (b.minY + b.maxY) / 2
  return { x: cx - w / 2, y: cy - (w * H) / W / 2, w }
}

function PlanCanvas({ plan }: { plan: Plan }) {
  const { t } = useTranslation()
  const { palette } = useTheme()
  const navigate = useNavigate()
  const svg = useRef<SVGSVGElement>(null)
  const drag = useRef<{ px: number; py: number; view: View; moved: boolean } | null>(null)
  const [view, setView] = useState<View>(() => fit(plan.locations))
  const [selected, setSelected] = useState<PlanLocation | null>(null)
  const [hidden, setHidden] = useState<Set<number>>(() => new Set())

  const spacing = useMemo(() => spacingOf(plan.locations), [plan.locations])
  const radius = Math.max(spacing * 0.3, 15)
  const byId = useMemo(() => new Map(plan.locations.map((l) => [l.id, l])), [plan.locations])
  const colourOf = useMemo(() => {
    const m = new Map<number, string>()
    plan.areas.forEach((a, i) => m.set(a.id, palette.layout.areas[i % palette.layout.areas.length]))
    return m
  }, [plan.areas, palette])
  const usedAreas = useMemo(() => {
    const ids = new Set(plan.locations.map((l) => l.areaId))
    return plan.areas.filter((a) => ids.has(a.id))
  }, [plan])
  const areaOf = (id: number) => plan.areas.find((a) => a.id === id)?.code ?? ''

  // one screen unit = view.w / W millimetres; names and arrows appear once the markers are big enough to read
  const detail = (spacing * W) / view.w >= 42

  function zoomAt(clientX: number, clientY: number, factor: number) {
    const el = svg.current
    if (!el) return
    const rect = el.getBoundingClientRect()
    setView((v) => {
      const w = Math.min(Math.max(v.w * factor, spacing * 4), fit(plan.locations).w * 6)
      const f = w / v.w
      const wx = v.x + ((clientX - rect.left) / rect.width) * v.w
      const wy = v.y + ((clientY - rect.top) / rect.height) * ((v.w * H) / W)
      return { x: wx - (wx - v.x) * f, y: wy - (wy - v.y) * f, w }
    })
  }

  // the browser only allows cancelling the page scroll from a non-passive listener
  useEffect(() => {
    const el = svg.current
    if (!el) return
    const onWheel = (e: WheelEvent) => {
      e.preventDefault()
      zoomAt(e.clientX, e.clientY, e.deltaY < 0 ? 0.8 : 1.25)
    }
    el.addEventListener('wheel', onWheel, { passive: false })
    return () => el.removeEventListener('wheel', onWheel)
  })

  function zoomCentre(factor: number) {
    const rect = svg.current?.getBoundingClientRect()
    if (rect) zoomAt(rect.left + rect.width / 2, rect.top + rect.height / 2, factor)
  }

  return (
    <>
      <Paper variant="outlined" sx={{ position: 'relative', overflow: 'hidden', bgcolor: 'background.paper' }}>
        <svg
          ref={svg}
          role="img"
          aria-label={t('md.layout.planLabel')}
          viewBox={`${view.x} ${view.y} ${view.w} ${(view.w * H) / W}`}
          style={{ width: '100%', aspectRatio: `${W} / ${H}`, display: 'block', touchAction: 'none', cursor: 'grab', background: palette.layout.empty }}
          onPointerDown={(e) => {
            drag.current = { px: e.clientX, py: e.clientY, view, moved: false }
          }}
          onPointerMove={(e) => {
            const d = drag.current
            const rect = svg.current?.getBoundingClientRect()
            if (!d || !rect) return
            const dx = e.clientX - d.px, dy = e.clientY - d.py
            if (!d.moved && Math.abs(dx) + Math.abs(dy) > 4) {
              d.moved = true
              e.currentTarget.setPointerCapture(e.pointerId) // only when really dragging, so clicks on locations still work
            }
            if (!d.moved) return
            setView({ ...d.view, x: d.view.x - (dx / rect.width) * d.view.w, y: d.view.y - (dy / rect.height) * ((d.view.w * H) / W) })
          }}
          onPointerUp={() => { window.setTimeout(() => { drag.current = null }, 0) }}
        >
          {plan.links.map((k) => {
            const a = byId.get(k.from), b = byId.get(k.to)
            if (!a || !b || hidden.has(a.areaId) || hidden.has(b.areaId)) return null
            return (
              <line key={k.id} x1={a.x} y1={-a.y} x2={b.x} y2={-b.y} vectorEffect="non-scaling-stroke"
                stroke={k.enabled ? palette.layout.link : palette.layout.linkDisabled} strokeWidth={k.enabled ? 1.2 : 1}
                strokeDasharray={k.enabled ? undefined : '4 3'} />
            )
          })}
          {detail && plan.links.map((k) => {
            const a = byId.get(k.from), b = byId.get(k.to)
            if (!a || !b || !k.enabled || hidden.has(a.areaId) || hidden.has(b.areaId)) return null
            // a small arrowhead near the target shows the direction of the link
            const dx = b.x - a.x, dy = -b.y + a.y, len = Math.hypot(dx, dy) || 1
            const ux = dx / len, uy = dy / len
            const tipX = b.x - ux * radius * 1.2, tipY = -b.y - uy * radius * 1.2, s = radius * 0.7
            return (
              <path key={`h${k.id}`} d={`M${tipX} ${tipY} L${tipX - ux * s - uy * s * 0.6} ${tipY - uy * s + ux * s * 0.6} L${tipX - ux * s + uy * s * 0.6} ${tipY - uy * s - ux * s * 0.6} Z`}
                fill={palette.layout.link} />
            )
          })}
          {plan.locations.map((l) => {
            if (hidden.has(l.areaId)) return null
            const colour = colourOf.get(l.areaId) ?? palette.layout.link
            const disabled = l.state === 'DISABLED'
            const blocked = l.state.startsWith('BLOCKED')
            return (
              <g key={l.id} transform={`translate(${l.x} ${-l.y})`} style={{ cursor: 'pointer' }}
                onClick={() => { if (!drag.current?.moved) setSelected(l) }}>
                <title>{`${l.code} - ${areaOf(l.areaId)} - ${t(`md.layout.state.${l.state}`)}`}</title>
                <circle r={radius} fill={disabled ? palette.background.paper : colour} stroke={disabled ? palette.layout.disabledLine : colour}
                  strokeWidth={radius * 0.18} strokeDasharray={disabled ? `${radius * 0.5} ${radius * 0.35}` : undefined} />
                {blocked && (
                  <>
                    <circle r={radius * 1.25} fill="none" stroke={l.state === 'BLOCKED_OUT' ? palette.layout.blockedOut : l.state === 'BLOCKED_BOTH' ? palette.layout.blockedBoth : palette.layout.blockedIn}
                      strokeWidth={radius * 0.25} />
                    <path d={`M${-radius * 0.7} ${-radius * 0.7} L${radius * 0.7} ${radius * 0.7} M${radius * 0.7} ${-radius * 0.7} L${-radius * 0.7} ${radius * 0.7}`}
                      stroke="#FFFFFF" strokeWidth={radius * 0.22} strokeLinecap="round" />
                  </>
                )}
                {selected?.id === l.id && <circle r={radius * 1.7} fill="none" stroke={palette.layout.selected} strokeWidth={radius * 0.2} />}
                {detail && (
                  <text y={radius * 2.1} textAnchor="middle" fontSize={radius * 0.95} fill={palette.text.primary} style={{ pointerEvents: 'none' }}>
                    {l.code}
                  </text>
                )}
              </g>
            )
          })}
        </svg>

        <Stack sx={{ position: 'absolute', top: 8, left: 8, bgcolor: 'background.paper', borderRadius: 1, border: 1, borderColor: 'divider' }}>
          <Tooltip title={t('md.layout.zoomIn')} placement="right"><IconButton size="small" aria-label={t('md.layout.zoomIn')} onClick={() => zoomCentre(0.7)}><AddIcon /></IconButton></Tooltip>
          <Tooltip title={t('md.layout.zoomOut')} placement="right"><IconButton size="small" aria-label={t('md.layout.zoomOut')} onClick={() => zoomCentre(1.4)}><RemoveIcon /></IconButton></Tooltip>
          <Tooltip title={t('md.layout.fit')} placement="right"><IconButton size="small" aria-label={t('md.layout.fit')} onClick={() => setView(fit(plan.locations))}><FitScreenIcon /></IconButton></Tooltip>
        </Stack>

        {selected && (
          <Paper elevation={3} sx={{ position: 'absolute', top: 8, right: 8, p: 2, width: 260, maxWidth: '60%' }}>
            <Box sx={{ display: 'flex', alignItems: 'flex-start' }}>
              <Typography variant="h6" sx={{ flexGrow: 1, wordBreak: 'break-all' }}>{selected.code}</Typography>
              <IconButton size="small" aria-label={t('md.close')} onClick={() => setSelected(null)}><CloseIcon fontSize="small" /></IconButton>
            </Box>
            <Typography variant="body2">{t('md.fields.areaCode')}: {areaOf(selected.areaId)}</Typography>
            <Typography variant="body2">{t('md.layout.stateLabel')}: {t(`md.layout.state.${selected.state}`)}</Typography>
            <Typography variant="body2" sx={{ mb: 1.5 }}>{t('md.layout.position', { x: selected.x, y: selected.y })}</Typography>
            <Button size="small" variant="contained" onClick={() => navigate(`/masterdata/locations?open=${selected.id}`)}>{t('md.layout.openRecord')}</Button>
          </Paper>
        )}
      </Paper>

      <Stack spacing={1.5} sx={{ mt: 2 }}>
        <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', rowGap: 1, alignItems: 'center' }}>
          <Typography variant="caption" color="text.secondary">{t('md.layout.areasLegend')}</Typography>
          {usedAreas.map((a) => {
            const off = hidden.has(a.id)
            return (
              <Chip key={a.id} size="small" label={a.code} clickable aria-pressed={!off}
                onClick={() => setHidden((prev) => { const n = new Set(prev); if (off) n.delete(a.id); else n.add(a.id); return n })}
                sx={{ bgcolor: off ? 'transparent' : colourOf.get(a.id), color: off ? 'text.secondary' : '#FFFFFF', border: 1, borderColor: colourOf.get(a.id), textDecoration: off ? 'line-through' : 'none',
                  '&:hover': { bgcolor: off ? 'action.hover' : colourOf.get(a.id) } }} />
            )
          })}
        </Stack>
        <StateLegend />
        <Typography variant="caption" color="text.secondary">{t('md.layout.planHelp')}</Typography>
      </Stack>
    </>
  )
}
