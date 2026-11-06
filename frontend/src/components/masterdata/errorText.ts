import type { TFunction } from 'i18next'
import { ApiError } from '../../api/client'

/** A message a person can act on. Known server codes go through i18n; anything else falls back to the server text. */
export function errorText(t: TFunction, e: unknown): string {
  if (e instanceof ApiError) {
    switch (e.code) {
      case 'STALE_VERSION':
        return t('md.error.stale')
      case 'IN_USE':
        return t('md.error.inUse')
      case 'NOT_FOUND':
        return t('md.error.notFound')
      case 'NOT_ALLOWED':
        return e.message
      case 'VALIDATION_ERROR':
        return t('md.error.validation')
    }
    if (e.status === 403) return t('md.error.forbidden')
    if (e.status === 401) return t('md.error.session')
    if (e.status >= 500) return t('md.error.server')
    return e.message
  }
  return t('md.error.network')
}
