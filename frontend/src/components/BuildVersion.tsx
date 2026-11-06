import { Typography, type TypographyProps } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { useBuildInfo } from '../api/buildInfo'

/** "DIMOS WMS 0.1.0 - build 2026-11-06 19:05 - commit 758ce04" (footer and login page). */
export default function BuildVersion(props: TypographyProps) {
  const { t } = useTranslation()
  const info = useBuildInfo()
  if (!info.data) return null
  const { version, time, commit, dirty } = info.data
  const parts = [`${t('app.name')} ${version}`]
  if (time) parts.push(t('app.build', { time }))
  if (commit) parts.push(t(dirty ? 'app.commitDirty' : 'app.commit', { commit }))
  return (
    <Typography variant="caption" component="div" color="text.secondary" {...props}>
      {parts.join(' - ')}
    </Typography>
  )
}
