import type { TFunction } from 'i18next'
import type { ParameterView } from '../../api/admin'

/** "PT90M", "PT1H30M" or "PT2H" -> whole minutes (null if it is not one of those forms). */
export function isoToMinutes(iso: string): number | null {
  const m = /^PT(?:(\d+)H)?(?:(\d+)M)?$/.exec(iso.trim().toUpperCase())
  if (!m || (m[1] === undefined && m[2] === undefined)) return null
  return Number(m[1] ?? 0) * 60 + Number(m[2] ?? 0)
}

export const minutesToIso = (minutes: number) => `PT${minutes}M`

/** A value as a person reads it: "30 minutes", "2 hours", "12 characters", "yes". */
export function formatValue(t: TFunction, def: Pick<ParameterView, 'type' | 'unit'>, value: string): string {
  switch (def.type) {
    case 'DURATION': {
      const minutes = isoToMinutes(value)
      if (minutes === null) return value
      return minutes % 60 === 0 ? t('admin.parameters.hours', { count: minutes / 60 }) : t('admin.parameters.minutes', { count: minutes })
    }
    case 'BOOL':
      return value === 'true' ? t('md.yes') : t('md.no')
    case 'INT':
      return def.unit ? `${value} ${def.unit}` : value
    default:
      return value
  }
}

/** "8 - 128 characters" / "5 minutes - 12 hours" / "" */
export function formatRange(t: TFunction, def: ParameterView): string {
  if (def.min === null || def.max === null) return ''
  return `${formatValue(t, def, def.min)} - ${formatValue(t, def, def.max)}`
}
