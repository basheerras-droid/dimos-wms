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

export const theme = createTheme({
  palette: {
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
    MuiListItemButton: {
      styleOverrides: {
        root: {
          '&.Mui-selected': {
            backgroundColor: 'rgba(215, 44, 6, 0.08)',
            borderLeft: `3px solid ${brand.blutorange}`,
            '&:hover': { backgroundColor: 'rgba(215, 44, 6, 0.12)' },
          },
        },
      },
    },
    MuiCard: {
      defaultProps: { variant: 'outlined' },
    },
  },
})
