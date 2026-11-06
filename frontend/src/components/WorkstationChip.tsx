import ComputerIcon from '@mui/icons-material/Computer'
import DevicesOtherIcon from '@mui/icons-material/DevicesOther'
import PhoneAndroidIcon from '@mui/icons-material/PhoneAndroid'
import StorefrontIcon from '@mui/icons-material/Storefront'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import { Chip, Tooltip } from '@mui/material'
import type { ReactElement } from 'react'
import { useTranslation } from 'react-i18next'
import type { WorkstationInfo } from '../api/auth'

const TYPE_ICON: Record<string, ReactElement> = {
  PC: <ComputerIcon />, HANDHELD: <PhoneAndroidIcon />, HMI: <DevicesOtherIcon />, KIOSK: <StorefrontIcon />,
}

/** Shows in the top bar which workstation this browser is (or "office mode"), and why access may be limited. */
export default function WorkstationChip({ workstation }: { workstation: WorkstationInfo }) {
  const { t } = useTranslation()
  const w = workstation

  if (w.mode === 'REGISTERED') {
    const tip = [
      t('workstation.tipName', { name: w.name ?? w.code ?? '' }),
      w.groupCode ? t('workstation.tipGroup', { group: w.groupCode }) : null,
      w.locationCode ? t('workstation.tipLocation', { location: w.locationCode }) : null,
      w.restricted ? t('workstation.tipRestricted') : t('workstation.tipFree'),
    ].filter(Boolean).join(' - ')
    return (
      <Tooltip title={tip}>
        <Chip size="small" color={w.restricted ? 'warning' : 'default'} variant={w.restricted ? 'filled' : 'outlined'}
          icon={TYPE_ICON[w.type ?? 'PC']} label={t('workstation.chip', { code: w.code ?? '' })} sx={{ mr: 1.5 }} />
      </Tooltip>
    )
  }
  if (w.mode === 'DISABLED') {
    return (
      <Tooltip title={t('workstation.tipDisabled')}>
        <Chip size="small" color="error" icon={<WarningAmberIcon />} label={t('workstation.disabled', { code: w.code ?? '' })} sx={{ mr: 1.5 }} />
      </Tooltip>
    )
  }
  if (w.mode === 'IP_MISMATCH') {
    return (
      <Tooltip title={t('workstation.tipIp')}>
        <Chip size="small" color="warning" icon={<WarningAmberIcon />} label={t('workstation.ipMismatch', { code: w.code ?? '' })} sx={{ mr: 1.5 }} />
      </Tooltip>
    )
  }
  return (
    <Tooltip title={w.staleCookie ? t('workstation.tipStale') : t('workstation.tipOffice')}>
      <Chip size="small" variant="outlined" color={w.staleCookie ? 'warning' : 'default'}
        icon={w.staleCookie ? <WarningAmberIcon /> : <ComputerIcon />}
        label={w.staleCookie ? t('workstation.notRegistered') : t('workstation.office')} sx={{ mr: 1.5 }} />
    </Tooltip>
  )
}
