import { Alert } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { Navigate } from 'react-router'
import { useMe } from '../api/auth'
import { navItems } from './navItems'

/** /masterdata or /administration on their own: go to the first page of the group the user may open. */
export default function GroupRedirect({ group }: { group: string }) {
  const { t } = useTranslation()
  const me = useMe()
  const permissions = me.data?.permissions ?? []
  const roles = me.data?.roles ?? []
  const first = navItems.find((i) => i.key === group)?.children?.find(
    (c) => (!c.permission || permissions.includes(c.permission)) && (!c.role || roles.includes(c.role)),
  )
  if (!first?.path) return <Alert severity="warning">{t('md.error.forbidden')}</Alert>
  return <Navigate to={first.path} replace />
}
