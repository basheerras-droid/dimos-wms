import { Box, Stack, Typography } from '@mui/material'
import { useTheme } from '@mui/material/styles'
import { useTranslation } from 'react-i18next'
import type { LocState } from '../../api/layout'
import { cellFill, STATES } from './stateUtils'

/** SVG patterns that tell the rack-face states apart without relying on colour alone. Ids carry a prefix. */
export function StatePatterns({ prefix }: { prefix: string }) {
  const { palette } = useTheme()
  const l = palette.layout
  return (
    <defs>
      <pattern id={`${prefix}-disabled`} width="6" height="6" patternUnits="userSpaceOnUse" patternTransform="rotate(45)">
        <rect width="6" height="6" fill={l.disabled} />
        <line x1="0" y1="0" x2="0" y2="6" stroke={l.disabledLine} strokeWidth="2" />
      </pattern>
      <pattern id={`${prefix}-out`} width="6" height="6" patternUnits="userSpaceOnUse">
        <rect width="6" height="6" fill={l.blockedOut} />
        <line x1="0" y1="3" x2="6" y2="3" stroke="#FFFFFF" strokeWidth="1.5" />
      </pattern>
      <pattern id={`${prefix}-both`} width="6" height="6" patternUnits="userSpaceOnUse">
        <rect width="6" height="6" fill={l.blockedBoth} />
        <path d="M0 0 L6 6 M6 0 L0 6" stroke="#FFFFFF" strokeWidth="1" />
      </pattern>
    </defs>
  )
}

/** A small sample of a state, drawn like on the rack face. */
function Swatch({ state }: { state: LocState }) {
  const { palette } = useTheme()
  const prefix = `lg-${state}`
  return (
    <svg width="18" height="18" aria-hidden="true" style={{ flexShrink: 0 }}>
      <StatePatterns prefix={prefix} />
      <rect x="1" y="1" width="16" height="16" rx="2" fill={cellFill(state, prefix, palette.layout.ok, palette.layout.blockedIn)}
        stroke={state === 'DISABLED' ? palette.layout.disabledLine : 'rgba(0,0,0,0.35)'} strokeDasharray={state === 'DISABLED' ? '3 2' : undefined} />
    </svg>
  )
}

export function StateLegend() {
  const { t } = useTranslation()
  return (
    <Stack direction="row" spacing={2.5} sx={{ flexWrap: 'wrap', rowGap: 1, alignItems: 'center' }}>
      {STATES.map((s) => (
        <Box key={s} sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
          <Swatch state={s} />
          <Typography variant="caption">{t(`md.layout.state.${s}`)}</Typography>
        </Box>
      ))}
    </Stack>
  )
}
