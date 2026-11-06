import AccountTreeIcon from '@mui/icons-material/AccountTree'
import AssessmentIcon from '@mui/icons-material/Assessment'
import DashboardIcon from '@mui/icons-material/Dashboard'
import InventoryIcon from '@mui/icons-material/Inventory2'
import LocalShippingIcon from '@mui/icons-material/LocalShipping'
import MoveToInboxIcon from '@mui/icons-material/MoveToInbox'
import PrecisionManufacturingIcon from '@mui/icons-material/PrecisionManufacturing'
import SettingsIcon from '@mui/icons-material/Settings'
import type { ReactElement } from 'react'
import AuditPage from './admin/AuditPage'
import PermissionsPage from './admin/PermissionsPage'
import RolesPage from './admin/RolesPage'
import UsersPage from './admin/UsersPage'
import EntityPage from './masterdata/EntityPage'
import EquipmentTypesPage from './masterdata/EquipmentTypesPage'
import LayoutPage from './layout/LayoutPage'
import ImportPage from './masterdata/ImportPage'
import ValidationPage from './masterdata/ValidationPage'
import {
  areasConfig, equipmentConfig, huTypesConfig, locationLinksConfig, locationsConfig, locationTypesConfig,
} from './masterdata/configs'

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
      { key: 'areas', path: '/masterdata/areas', element: <EntityPage key="areasConfig" config={areasConfig} /> },
      { key: 'locationTypes', path: '/masterdata/location-types', element: <EntityPage key="locationTypesConfig" config={locationTypesConfig} /> },
      { key: 'locations', path: '/masterdata/locations', element: <EntityPage key="locationsConfig" config={locationsConfig} /> },
      { key: 'locationLinks', path: '/masterdata/location-links', element: <EntityPage key="locationLinksConfig" config={locationLinksConfig} /> },
      { key: 'huTypes', path: '/masterdata/hu-types', element: <EntityPage key="huTypesConfig" config={huTypesConfig} /> },
      // static configuration of the equipment; the top-level "Equipment (WCS)" entry is for live status (later step)
      { key: 'equipmentMaster', path: '/masterdata/equipment', element: <EntityPage key="equipmentConfig" config={equipmentConfig} /> },
      { key: 'equipmentTypes', path: '/masterdata/equipment-types', element: <EquipmentTypesPage /> },
      { key: 'layout', path: '/masterdata/layout', element: <LayoutPage key="layout" /> },
      { key: 'import', path: '/masterdata/import', permission: 'MASTERDATA_EDIT', element: <ImportPage /> },
      { key: 'validation', path: '/masterdata/validation', element: <ValidationPage /> },
    ],
  },
  { key: 'reports', path: '/reports', icon: <AssessmentIcon /> },
  {
    key: 'administration',
    path: '/administration',
    icon: <SettingsIcon />,
    // each page needs its own permission; the group shows when at least one of them is allowed
    children: [
      { key: 'users', path: '/administration/users', permission: 'USER_MANAGE', element: <UsersPage key="users" /> },
      { key: 'roles', path: '/administration/roles', permission: 'ROLE_MANAGE', element: <RolesPage key="roles" /> },
      { key: 'permissions', path: '/administration/permissions', permission: 'ROLE_MANAGE', element: <PermissionsPage key="permissions" /> },
      { key: 'auditLog', path: '/administration/audit', permission: 'AUDIT_VIEW', element: <AuditPage key="audit" /> },
    ],
  },
]

/** Every page of the menu as a flat list (for the router). */
export function leafItems(items: NavItem[] = navItems, inherited?: string): NavItem[] {
  return items.flatMap((i) =>
    i.children
      ? leafItems(i.children, i.permission ?? inherited)
      : [{ ...i, permission: i.permission ?? inherited }],
  )
}
