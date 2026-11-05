import { Button, Typography } from '@mui/material'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'

export default function NotFoundPage() {
  const { t } = useTranslation()
  return (
    <>
      <Typography variant="h4" component="h1" gutterBottom>
        {t('notFound.title')}
      </Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>
        {t('notFound.body')}
      </Typography>
      <Button component={Link} to="/" variant="contained">
        {t('notFound.back')}
      </Button>
    </>
  )
}
