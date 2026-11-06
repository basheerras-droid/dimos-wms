import { createTheme } from '@mui/material/styles'

// DIMOS Corporate Design (Jan 2023)
export const brand = {
  schwarzgrau: '#2F3339', // text and headings
  blutorange: '#D72C06', // accent only
  fenstergrau: '#90979B', // secondary, borders, muted
  text: '#1A1A1A',
  page: '#F4F5F6',
  white: '#FFFFFF',
}

/**
 * Colours of the dark side navigation. Contrast against the panel (#2F3339), checked against WCAG AA (4.5:1 for text):
 * text #E8EAEC 10.5:1, sub-items #B9BEC2 6.8:1, hover panel #3A4047: text 8.7:1 and sub-items 5.6:1,
 * selected row (18% Blutorange over the panel) with white text 11.6:1.
 * The selected icon is white, not orange: Blutorange on this dark panel is only 2.6:1. For the same reason the
 * orange bar is never the only sign of the selected item (background, bold white text and white icon say it too).
 */
export interface NavPalette {
  background: string
  text: string
  subText: string
  icon: string
  hover: string
  selectedBackground: string
  selectedText: string
  accent: string
}

/**
 * Colours of the layout preview. States are never told by colour alone: disabled is hatched / hollow, blocked has a
 * ring and a cross (plan) or stripes / cross-hatch (rack face), and the legend names every one of them.
 */
export interface LayoutPalette {
  /** One colour per area, handed out in order (repeats after 8). */
  areas: string[]
  ok: string
  disabled: string
  disabledLine: string
  blockedIn: string
  blockedOut: string
  blockedBoth: string
  empty: string
  link: string
  linkDisabled: string
  selected: string
  conflict: string
}

declare module '@mui/material/styles' {
  interface Palette {
    nav: NavPalette
    layout: LayoutPalette
  }
  interface PaletteOptions {
    nav?: NavPalette
    layout?: LayoutPalette
  }
}

const layout: LayoutPalette = {
  areas: ['#2F6DB5', '#2E8B8B', '#7A5CB5', '#B5732F', '#5C8A2F', '#B52F7A', '#6B7A8F', '#8F6B2F'],
  ok: '#3D8F5F',
  disabled: '#D5D9DC',
  disabledLine: '#7C858A',
  blockedIn: brand.blutorange,
  blockedOut: '#B57A0F',
  blockedBoth: '#7A1703',
  empty: '#EEF0F1',
  link: '#8A9196',
  linkDisabled: '#C5CACD',
  selected: brand.schwarzgrau,
  conflict: '#B52F7A',
}

const nav: NavPalette = {
  background: brand.schwarzgrau,
  text: '#E8EAEC',
  subText: '#B9BEC2',
  icon: '#E8EAEC',
  hover: '#3A4047',
  selectedBackground: 'rgba(215, 44, 6, 0.18)',
  selectedText: brand.white,
  accent: brand.blutorange,
}

export const theme = createTheme({
  palette: {
    nav,
    layout,
    primary: { main: brand.blutorange, contrastText: brand.white },
    secondary: { main: brand.schwarzgrau, contrastText: brand.white },
    text: { primary: brand.text, secondary: brand.fenstergrau },
    background: { default: brand.page, paper: brand.white },
    divider: '#DDE0E2',
  },
  shape: { borderRadius: 6 },
  typography: {
    fontFamily: '"Metropolis", "Segoe UI", Arial, sans-serif',
    h1: { color: brand.schwarzgrau, fontWeight: 700 },
    h2: { color: brand.schwarzgrau, fontWeight: 700 },
    h3: { color: brand.schwarzgrau, fontWeight: 700 },
    h4: { color: brand.schwarzgrau, fontWeight: 700 },
    h5: { color: brand.schwarzgrau, fontWeight: 600 },
    h6: { color: brand.schwarzgrau, fontWeight: 600 },
    button: { textTransform: 'none', fontWeight: 600 },
  },
  components: {
    MuiAppBar: {
      defaultProps: { color: 'default', elevation: 0 },
      styleOverrides: {
        root: { backgroundColor: brand.white, color: brand.schwarzgrau, borderBottom: '1px solid #DDE0E2' },
      },
    },
    MuiDrawer: {
      styleOverrides: { paper: { backgroundColor: brand.white, borderRight: '1px solid #DDE0E2' } },
    },
    MuiCard: {
      defaultProps: { variant: 'outlined' },
    },
  },
})
