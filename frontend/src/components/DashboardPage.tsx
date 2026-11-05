import { Box, Card, CardContent, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { useMe } from '../api/auth'
import { brand } from '../theme'

const kpis = ['openTransportOrders', 'stockLocationsUsed', 'activeAlarms', 'equipmentOnline'] as const

export default function DashboardPage() {
  const { t } = useTranslation()
  const me = useMe()

  return (
    <>
      <Typography variant="h4" component="h1" gutterBottom>
        {t('dashboard.title')}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        {t('dashboard.welcome', { name: me.data?.displayName ?? '' })}
      </Typography>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(4, 1fr)' } }}>
        {kpis.map((k) => (
          <Card key={k}>
            <CardContent>
              <Typography variant="body2" color="text.secondary">
                {t(`dashboard.kpi.${k}`)}
              </Typography>
              <Typography variant="h3" component="p" sx={{ mt: 1, color: brand.schwarzgrau }} aria-label={t('dashboard.noData')}>
                --
              </Typography>
            </CardContent>
          </Card>
        ))}
      </Box>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 3 }}>
        {t('dashboard.noData')}
      </Typography>
    </>
  )
}
