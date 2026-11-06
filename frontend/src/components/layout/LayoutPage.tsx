import { Tab, Tabs, Typography } from '@mui/material'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import PlanView from './PlanView'
import RackFaceView from './RackFaceView'

/** Layout preview: the plan of a level, and the front of a rack. Read-only; click a location to open its record. */
export default function LayoutPage() {
  const { t } = useTranslation()
  const [tab, setTab] = useState<'plan' | 'rack'>('plan')
  return (
    <>
      <Typography variant="h4" component="h1" gutterBottom>{t('md.layout.title')}</Typography>
      <Typography color="text.secondary" sx={{ mb: 1 }}>{t('md.layout.intro')}</Typography>
      <Tabs value={tab} onChange={(_, v: 'plan' | 'rack') => setTab(v)} sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Tab value="plan" label={t('md.layout.tabPlan')} />
        <Tab value="rack" label={t('md.layout.tabRack')} />
      </Tabs>
      {tab === 'plan' ? <PlanView /> : <RackFaceView />}
    </>
  )
}
