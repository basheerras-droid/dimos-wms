import { Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'

export default function PlaceholderPage({ navKey }: { navKey: string }) {
  const { t } = useTranslation()
  return (
    <>
      <Typography variant="h4" component="h1" gutterBottom>
        {t(`nav.${navKey}`)}
      </Typography>
      <Typography color="text.secondary">{t('placeholder.body')}</Typography>
    </>
  )
}
