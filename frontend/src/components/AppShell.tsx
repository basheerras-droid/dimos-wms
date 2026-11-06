import LogoutIcon from '@mui/icons-material/Logout'
import MenuIcon from '@mui/icons-material/Menu'
import KeyIcon from '@mui/icons-material/Key'
import LinkOffIcon from '@mui/icons-material/LinkOff'
import ExpandLess from '@mui/icons-material/ExpandLess'
import ExpandMore from '@mui/icons-material/ExpandMore'
import {
  AppBar, Avatar, Box, Collapse, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText,
  Menu, MenuItem, Toolbar, Tooltip, Typography, useMediaQuery,
} from '@mui/material'
import { useTheme } from '@mui/material/styles'
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Navigate, Outlet, useLocation, useNavigate } from 'react-router'
import { useLogout, useMe, useUnregisterBrowser } from '../api/auth'
import { brand } from '../theme'
import BuildVersion from './BuildVersion'
import WorkstationChip from './WorkstationChip'
import { navItems, type NavItem } from './navItems'

const WIDE = 240
const NARROW = 64

/** Dark panel for the side navigation only (other drawers, e.g. the edit drawer, stay white). */
const navPaper = {
  backgroundColor: 'nav.background',
  color: 'nav.text',
  borderRight: 0,
} as const

export default function AppShell() {
  const { t } = useTranslation()
  const theme = useTheme()
  const compact = useMediaQuery(theme.breakpoints.down('md')) // tablets and handhelds: drawer slides over
  const me = useMe()
  const logout = useLogout()
  const unregister = useUnregisterBrowser()
  const navigate = useNavigate()
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(false)
  const [mobileOpen, setMobileOpen] = useState(false)
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>({})

  if (me.isLoading) return null
  if (!me.data) return <Navigate to="/login" replace />
  if (me.data.mustChangePassword) return <Navigate to="/change-password" replace />
  const user = me.data

  const width = compact ? WIDE : collapsed ? NARROW : WIDE
  const showText = compact || !collapsed

  const has = (permission?: string) => !permission || user.permissions.includes(permission)
  const allowed = (item: NavItem): boolean =>
    item.children ? has(item.permission) && item.children.some((c) => has(c.permission)) : has(item.permission)
  const isOn = (path: string) => (path === '/' ? location.pathname === '/' : location.pathname.startsWith(path))

  const renderItem = (item: NavItem, depth: number) => {
    const group = item.children !== undefined
    const selected = item.path !== undefined && isOn(item.path)
    const open = group && (openGroups[item.key] ?? selected)
    const target = group ? item.children![0]?.path : item.path
    const button = (
      <ListItemButton
        key={item.key}
        title={t(`nav.${item.key}`)}
        selected={selected && (!group || !showText)}
        onClick={() => {
          if (group && showText) {
            setOpenGroups((g) => ({ ...g, [item.key]: !open }))
            return
          }
          if (target) navigate(target)
          setMobileOpen(false)
        }}
        sx={(th) => ({
          minHeight: depth ? 38 : 46,
          px: 2,
          pl: depth ? 5.5 : 2,
          position: 'relative',
          color: depth ? th.palette.nav.subText : th.palette.nav.text,
          '&:hover': { backgroundColor: th.palette.nav.hover },
          '&.Mui-focusVisible': { outline: `2px solid ${th.palette.nav.text}`, outlineOffset: -2 },
          '&.Mui-selected': {
            backgroundColor: th.palette.nav.selectedBackground,
            color: th.palette.nav.selectedText,
            '&:hover': { backgroundColor: th.palette.nav.selectedBackground },
            // 4 px accent bar on the left; drawn on top so the text does not move when an item is selected
            '&::before': {
              content: '""', position: 'absolute', left: 0, top: 0, bottom: 0, width: 4,
              backgroundColor: th.palette.nav.accent,
            },
          },
        })}
      >
        {item.icon && (
          <ListItemIcon
            sx={(th) => ({
              minWidth: 0, mr: showText ? 1.75 : 0, color: selected ? th.palette.nav.selectedText : th.palette.nav.icon,
            })}
          >
            {item.icon}
          </ListItemIcon>
        )}
        {showText && (
          <ListItemText
            primary={t(`nav.${item.key}`)}
            // one line only: long labels get an ellipsis (the full text is the button's title)
            slotProps={{ primary: { noWrap: true, sx: { fontWeight: selected ? 600 : 500, color: 'inherit', fontSize: depth ? 13 : 14 } } }}
            sx={{ minWidth: 0 }}
          />
        )}
        {group && showText && (open ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />)}
      </ListItemButton>
    )
    const wrapped = showText ? button : (
      <Tooltip key={item.key} title={t(`nav.${item.key}`)} placement="right">{button}</Tooltip>
    )
    if (!group) return wrapped
    return (
      <Box key={item.key}>
        {wrapped}
        {showText && (
          <Collapse in={open} timeout="auto" unmountOnExit>
            <List disablePadding>{item.children!.filter(allowed).map((c) => renderItem(c, depth + 1))}</List>
          </Collapse>
        )}
      </Box>
    )
  }

  const list = (
    <Box sx={{ pt: 1 }}>
      <List>{navItems.filter(allowed).map((item) => renderItem(item, 0))}</List>
    </Box>
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100%' }}>
      <AppBar position="fixed" sx={{ zIndex: (th) => th.zIndex.drawer + 1 }}>
        <Toolbar>
          <IconButton edge="start" color="inherit" aria-label={compact ? t('nav.expand') : collapsed ? t('nav.expand') : t('nav.collapse')} onClick={() => (compact ? setMobileOpen(true) : setCollapsed((c) => !c))} sx={{ mr: 2 }}>
            <MenuIcon />
          </IconButton>
          <Box component="img" src="/brand/dimos-logo.png" alt="DIMOS" sx={{ height: 32, width: 'auto' }} />
          <Typography variant="h6" component="div" sx={{ ml: 2, display: { xs: 'none', sm: 'block' } }}>
            {t('app.name')}
          </Typography>
          <Box sx={{ flexGrow: 1 }} />
          <WorkstationChip workstation={user.workstation} />
          <Box sx={{ textAlign: 'right', mr: 1.5, display: { xs: 'none', sm: 'block' } }}>
            <Typography variant="body2" sx={{ fontWeight: 600, lineHeight: 1.2 }}>{user.displayName}</Typography>
            <Typography variant="caption" color="text.secondary">{user.roles.join(', ')}</Typography>
          </Box>
          <IconButton aria-label={t('user.menu')} onClick={(e) => setAnchor(e.currentTarget)}>
            <Avatar sx={{ width: 36, height: 36, bgcolor: brand.schwarzgrau, fontSize: 15 }}>
              {user.displayName.trim().charAt(0).toUpperCase()}
            </Avatar>
          </IconButton>
          <Menu anchorEl={anchor} open={Boolean(anchor)} onClose={() => setAnchor(null)}>
            <Box sx={{ px: 2, py: 1 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>{user.displayName}</Typography>
              <Typography variant="caption" color="text.secondary">
                {t('user.role')}: {user.roles.join(', ')}
              </Typography>
            </Box>
            <Divider />
            <MenuItem onClick={() => { setAnchor(null); navigate('/change-password') }}>
              <ListItemIcon><KeyIcon fontSize="small" /></ListItemIcon>
              {t('user.changePassword')}
            </MenuItem>
            {user.roles.includes('SUPER_ADMIN') && (user.workstation.mode !== 'OFFICE' || user.workstation.staleCookie) && (
              <MenuItem onClick={() => { setAnchor(null); unregister.mutate() }}>
                <ListItemIcon><LinkOffIcon fontSize="small" /></ListItemIcon>
                {t('workstation.unregister')}
              </MenuItem>
            )}
            <MenuItem onClick={() => { setAnchor(null); logout.mutate(undefined, { onSettled: () => navigate('/login', { replace: true }) }) }}>
              <ListItemIcon><LogoutIcon fontSize="small" /></ListItemIcon>
              {t('user.logout')}
            </MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>

      {compact ? (
        <Drawer variant="temporary" open={mobileOpen} onClose={() => setMobileOpen(false)} ModalProps={{ keepMounted: true }} sx={{ '& .MuiDrawer-paper': { ...navPaper, width: WIDE } }}>
          <Toolbar />
          {list}
        </Drawer>
      ) : (
        <Drawer variant="permanent" sx={{ width, flexShrink: 0, '& .MuiDrawer-paper': { ...navPaper, width, boxSizing: 'border-box', overflowX: 'hidden', transition: 'width 150ms' } }}>
          <Toolbar />
          {list}
        </Drawer>
      )}

      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <Toolbar />
        <Box sx={{ flex: 1, p: { xs: 2, md: 3 } }}>
          <Outlet />
        </Box>
        <BuildVersion sx={{ px: 3, pb: 1.5 }} />
      </Box>
    </Box>
  )
}
