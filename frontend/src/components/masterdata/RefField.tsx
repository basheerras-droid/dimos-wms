import { Autocomplete, CircularProgress, TextField } from '@mui/material'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { api } from '../../api/client'
import type { Page } from '../../api/masterdata'

export interface RefOption {
  id: number
  code?: string
  name?: string
}

function optionText(o: RefOption): string {
  if (o.code && o.name && o.name !== o.code) return `${o.code} - ${o.name}`
  return o.code ?? String(o.id)
}

function useDebounced(value: string, ms: number) {
  const [v, setV] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setV(value), ms)
    return () => clearTimeout(id)
  }, [value, ms])
  return v
}

interface Props {
  label: string
  refPath: string
  /** Id of the chosen record, or '' for none. */
  value: string
  /** Code shown for the chosen record before any search has happened (from the loaded row). */
  valueLabel: string
  required?: boolean
  disabled?: boolean
  error?: boolean
  helperText?: string
  onChange: (id: string, label: string) => void
}

/**
 * Pick a record of another table. The server does the searching (20 matches at a time), so this works for lists
 * with thousands of entries such as locations.
 */
export default function RefField({
  label, refPath, value, valueLabel, required, disabled, error, helperText, onChange,
}: Props) {
  const [text, setText] = useState('')
  const q = useDebounced(text, 250)
  const found = useQuery<Page<RefOption>>({
    queryKey: ['md-ref', refPath, q],
    queryFn: () => api<Page<RefOption>>(`${refPath}?size=20&sort=code&q=${encodeURIComponent(q)}`),
    placeholderData: keepPreviousData,
    staleTime: 15_000,
  })

  const selected: RefOption | null = value ? { id: Number(value), code: valueLabel } : null
  const options = found.data?.items ?? []
  const withSelected = selected && !options.some((o) => o.id === selected.id) ? [selected, ...options] : options

  return (
    <Autocomplete<RefOption>
      options={withSelected}
      value={selected}
      disabled={disabled}
      loading={found.isFetching}
      getOptionLabel={optionText}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      filterOptions={(all) => all} // the server already filtered
      onInputChange={(_, v, reason) => {
        if (reason === 'input') setText(v)
        if (reason === 'clear' || reason === 'reset') setText('')
      }}
      onChange={(_, o) => onChange(o ? String(o.id) : '', o?.code ?? '')}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          required={required}
          error={error}
          helperText={helperText}
          slotProps={{
            ...params.slotProps,
            input: {
              ...params.slotProps.input,
              endAdornment: (
                <>
                  {found.isFetching ? <CircularProgress color="inherit" size={16} /> : null}
                  {params.slotProps.input.endAdornment}
                </>
              ),
            },
          }}
        />
      )}
    />
  )
}
