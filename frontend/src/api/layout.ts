import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { api } from './client'
import { MD } from './masterdata'

export type LocState = 'OK' | 'DISABLED' | 'BLOCKED_IN' | 'BLOCKED_OUT' | 'BLOCKED_BOTH'

export interface PlanArea {
  id: number
  code: string
  areaType: string
}

export interface PlanLocation {
  id: number
  code: string
  areaId: number
  x: number
  y: number
  state: LocState
}

export interface PlanLink {
  id: number
  from: number
  to: number
  enabled: boolean
}

export interface Plan {
  levels: number[]
  level: number | null
  areas: PlanArea[]
  locations: PlanLocation[]
  links: PlanLink[]
  withoutPosition: number
  crossLevelLinks: number
}

export interface Rack {
  aisle: string
  side: string | null
  locations: number
  minColumn: number
  maxColumn: number
  minLevel: number
  maxLevel: number
}

export interface RackCell {
  id: number
  code: string
  column: number
  level: number
  state: LocState
}

export interface RackFace {
  aisle: string
  side: string | null
  minColumn: number
  maxColumn: number
  minLevel: number
  maxLevel: number
  cells: RackCell[]
  summary: {
    total: number
    usable: number
    disabled: number
    blockedIn: number
    blockedOut: number
    blockedBoth: number
    duplicates: number
    unplaced: number
  }
  /** Occupancy needs stock data; until the inventory exists {@code available} is false. */
  fill: { occupied: number | null; capacity: number; available: boolean }
}

export function usePlan(level: number | undefined) {
  return useQuery<Plan>({
    queryKey: ['md-layout-plan', level],
    queryFn: () => api<Plan>(`${MD}/layout/plan${level === undefined ? '' : `?levelNo=${level}`}`),
    placeholderData: keepPreviousData,
  })
}

export function useRacks() {
  return useQuery<Rack[]>({ queryKey: ['md-layout-racks'], queryFn: () => api<Rack[]>(`${MD}/layout/racks`) })
}

export function useRackFace(aisle: string | undefined, side: string | null | undefined) {
  return useQuery<RackFace>({
    queryKey: ['md-layout-rack-face', aisle, side],
    queryFn: () =>
      api<RackFace>(`${MD}/layout/rack-face?aisle=${encodeURIComponent(aisle ?? '')}${side ? `&side=${encodeURIComponent(side)}` : ''}`),
    enabled: aisle !== undefined,
    placeholderData: keepPreviousData,
  })
}
