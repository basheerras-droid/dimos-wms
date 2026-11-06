import {
  Alert, Button, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle, Typography,
} from '@mui/material'
import type { ReactNode } from 'react'
import { useTranslation } from 'react-i18next'

/** A record in an assignment list (what the server sends: id, code, name). */
export interface Item {
  id: number
  code: string
  name: string | null
}

/** Answer of the assignment endpoints: the assigned records and the version of the owner. */
export interface AssignedIds {
  ids: number[]
  version: number
  items: Item[]
}

interface ShellProps {
  title: string
  subtitle?: string
  loading?: boolean
  loadError?: string
  error?: ReactNode
  /** Show Save (false = view only). */
  canEdit: boolean
  saving?: boolean
  /** Save is shown but cannot be pressed yet (e.g. an input is invalid). */
  saveDisabled?: boolean
  onSave: () => void
  onClose: () => void
  maxWidth?: 'sm' | 'md' | 'lg'
  children: ReactNode
}

/** The frame of every assignment dialog: title, error area, Cancel / Save. */
export function AssignmentShell({
  title, subtitle, loading, loadError, error, canEdit, saving, saveDisabled, onSave, onClose, maxWidth = 'sm', children,
}: ShellProps) {
  const { t } = useTranslation()
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth={maxWidth}>
      <DialogTitle>
        {title}
        {subtitle && <Typography variant="body2" color="text.secondary">{subtitle}</Typography>}
      </DialogTitle>
      <DialogContent dividers>
        {loading && <CircularProgress size={24} />}
        {loadError && <Alert severity="error">{loadError}</Alert>}
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        {!loading && !loadError && children}
      </DialogContent>
      <DialogActions>
        <Button color="secondary" onClick={onClose}>{canEdit ? t('md.cancel') : t('md.close')}</Button>
        {canEdit && (
          <Button variant="contained" onClick={onSave} disabled={saving || saveDisabled || loading || Boolean(loadError)}>
            {saving ? t('md.saving') : t('md.save')}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  )
}
