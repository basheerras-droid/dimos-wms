import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import { Accordion, AccordionDetails, AccordionSummary, Box, Chip, Stack, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import type { AdminUser, ViaRole } from '../../api/admin'

const viaText = (v: ViaRole, t: (k: string, o?: Record<string, unknown>) => string) =>
  v.group ? t('admin.effective.viaGroup', { role: v.role, group: v.group }) : t('admin.effective.viaRole', { role: v.role })

/** What a user may do in total, grouped by module, with the role or group each permission comes from. */
export default function EffectivePermissions({ user }: { user: AdminUser }) {
  const { t } = useTranslation()
  const list = user.effectivePermissions ?? []
  const modules = [...new Set(list.map((p) => p.module))]

  return (
    <Box>
      <Typography sx={{ fontWeight: 600, mb: 1 }}>{t('admin.effective.groups')}</Typography>
      <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5, mb: 0.5 }}>
        {(user.groups ?? []).length === 0 && <Typography variant="body2" color="text.secondary">{t('admin.effective.noGroups')}</Typography>}
        {(user.groups ?? []).map((g) => (
          <Chip key={g.code} size="small" label={g.enabled ? g.code : `${g.code} (${t('admin.users.disabled')})`} variant={g.enabled ? 'filled' : 'outlined'} />
        ))}
      </Stack>
      <Typography variant="caption" color="text.secondary">{t('admin.effective.groupsHelp')}</Typography>

      <Accordion variant="outlined" disableGutters sx={{ mt: 2 }}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography sx={{ fontWeight: 600 }}>{t('admin.effective.title', { count: list.length })}</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>{t('admin.effective.help')}</Typography>
          {modules.map((m) => (
            <Box key={m} sx={{ mb: 1.5 }}>
              <Typography variant="overline" color="text.secondary">{t(`admin.modules.${m}`, { defaultValue: m })}</Typography>
              {list.filter((p) => p.module === m).map((p) => (
                <Box key={p.code} sx={{ py: 0.5 }}>
                  <Typography sx={{ fontFamily: 'monospace', fontSize: 13 }}>{p.code}</Typography>
                  <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                    {p.description}
                  </Typography>
                  <Typography variant="caption" sx={{ display: 'block' }}>
                    {p.via.map((v) => viaText(v, t)).join('; ')}
                  </Typography>
                </Box>
              ))}
            </Box>
          ))}
        </AccordionDetails>
      </Accordion>
    </Box>
  )
}
