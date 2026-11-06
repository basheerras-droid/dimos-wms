import AccountTreeIcon from '@mui/icons-material/AccountTree'
import AssessmentIcon from '@mui/icons-material/Assessment'
import DashboardIcon from '@mui/icons-material/Dashboard'
import InventoryIcon from '@mui/icons-material/Inventory2'
import LocalShippingIcon from '@mui/icons-material/LocalShipping'
import MoveToInboxIcon from '@mui/icons-material/MoveToInbox'
import PrecisionManufacturingIcon from '@mui/icons-material/PrecisionManufacturing'
import SettingsIcon from '@mui/icons-material/Settings'
import type { ReactElement } from 'react'
import EntityPage from './masterdata/EntityPage'
import { areasConfig, huTypesConfig, locationTypesConfig } from './masterdata/configs'

export interface NavItem {
  key: string
  /** Leaf items have a path; group items have children and use the path prefix of their children. */
  path?: string
  icon?: ReactElement
  /** Needed to see this entry. The server checks again on every call. */
  permission?: string
  children?: NavItem[]
  /** The page; entries without one show the "planned for a later release" page. */
  element?: ReactElement
}

export const navItems: NavItem[] = [
  { key: 'dashboard', path: '/', icon: <DashboardIcon /> },
  { key: 'inbound', path: '/inbound', icon: <MoveToInboxIcon /> },
  { key: 'inventory', path: '/inventory', icon: <InventoryIcon /> },
  { key: 'outbound', path: '/outbound', icon: <LocalShippingIcon /> },
  { key: 'equipment', path: '/equipment', icon: <PrecisionManufacturingIcon /> },
  {
    key: 'masterdata',
    path: '/masterdata',
    icon: <AccountTreeIcon />,
    permission: 'MASTERDATA_VIEW',
    children: [
      { key: 'areas', path: '/masterdata/areas', element: <EntityPage config={areasConfig} /> },
      { key: 'locationTypes', path: '/masterdata/location-types', element: <EntityPage config={locationTypesConfig} /> },
      { key: 'huTypes', path: '/masterdata/hu-types', element: <EntityPage config={huTypesConfig} /> },
    ],
  },
  { key: 'reports', path: '/reports', icon: <AssessmentIcon /> },
  { key: 'administration', path: '/administration', icon: <SettingsIcon /> },
]

/** Every page of the menu as a flat list (for the router). */
export function leafItems(items: NavItem[] = navItems, inherited?: string): NavItem[] {
  return items.flatMap((i) =>
    i.children
      ? leafItems(i.children, i.permission ?? inherited)
      : [{ ...i, permission: i.permission ?? inherited }],
  )
}
