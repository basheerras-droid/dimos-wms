import { Alert, Chip, Paper, Stack, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { usePermissions, useRoles } from '../../api/admin'
import { errorText } from '../masterdata/errorText'

/** The permissions of the product, grouped by module, with the roles that hold each one. Fixed in code: read-only. */
export default function PermissionsPage() {
  const { t } = useTranslation()
  const permissions = usePermissions()
  const roles = useRoles()
  const list = permissions.data ?? []
  const modules = [...new Set(list.map((p) => p.module))]

  return (
    <>
      <Typography variant="h4" component="h1" gutterBottom>{t('admin.permissions.title')}</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>{t('admin.permissions.intro')}</Typography>
      {(permissions.isError || roles.isError) && <Alert severity="error" sx={{ mb: 2 }}>{errorText(t, permissions.error ?? roles.error)}</Alert>}
      <Stack spacing={3}>
        {modules.map((module) => (
          <Paper key={module} variant="outlined">
            <Typography variant="h6" sx={{ p: 2, pb: 1 }}>{t(`admin.modules.${module}`, { defaultValue: module })}</Typography>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 240 }}>{t('admin.roles.permission')}</TableCell>
                  <TableCell>{t('admin.permissions.allows')}</TableCell>
                  <TableCell sx={{ width: 320 }}>{t('admin.permissions.heldBy')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {list.filter((p) => p.module === module).map((p) => (
                  <TableRow key={p.code}>
                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 13 }}>{p.code}</TableCell>
                    <TableCell>{p.description}</TableCell>
                    <TableCell>
                      <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
                        {(roles.data ?? []).filter((r) => r.permissions.includes(p.code)).map((r) => (
                          <Chip key={r.id} size="small" label={r.code} variant={r.builtin ? 'filled' : 'outlined'} />
                        ))}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>
        ))}
      </Stack>
    </>
  )
}
