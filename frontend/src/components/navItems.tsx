import AssessmentIcon from '@mui/icons-material/Assessment'
import DashboardIcon from '@mui/icons-material/Dashboard'
import InventoryIcon from '@mui/icons-material/Inventory2'
import LocalShippingIcon from '@mui/icons-material/LocalShipping'
import MoveToInboxIcon from '@mui/icons-material/MoveToInbox'
import PrecisionManufacturingIcon from '@mui/icons-material/PrecisionManufacturing'
import SettingsIcon from '@mui/icons-material/Settings'
import type { ReactElement } from 'react'

export interface NavItem {
  key: string
  path: string
  icon: ReactElement
}

export const navItems: NavItem[] = [
  { key: 'dashboard', path: '/', icon: <DashboardIcon /> },
  { key: 'inbound', path: '/inbound', icon: <MoveToInboxIcon /> },
  { key: 'inventory', path: '/inventory', icon: <InventoryIcon /> },
  { key: 'outbound', path: '/outbound', icon: <LocalShippingIcon /> },
  { key: 'equipment', path: '/equipment', icon: <PrecisionManufacturingIcon /> },
  { key: 'reports', path: '/reports', icon: <AssessmentIcon /> },
  { key: 'administration', path: '/administration', icon: <SettingsIcon /> },
]
