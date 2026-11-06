import { Alert } from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'
import { useCan } from '../api/auth'

/** Shows the page only to people with the permission (the server refuses the data anyway). */
export default function RequirePermission({ permission, children }: { permission?: string; children: ReactNode }) {
  const { t } = useTranslation()
  const allowed = useCan(permission ?? '')
  if (!permission || allowed) return <>{children}</>
  return <Alert severity="warning">{t('md.error.forbidden')}</Alert>
}
