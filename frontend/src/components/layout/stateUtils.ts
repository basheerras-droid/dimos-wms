import type { LocState } from '../../api/layout'

export const STATES: LocState[] = ['OK', 'DISABLED', 'BLOCKED_IN', 'BLOCKED_OUT', 'BLOCKED_BOTH']

/** The fill of a rack-face cell for a state (patterns come from {@link StatePatterns}). */
export function cellFill(state: LocState, prefix: string, ok: string, blockedIn: string): string {
  switch (state) {
    case 'DISABLED': return `url(#${prefix}-disabled)`
    case 'BLOCKED_IN': return blockedIn
    case 'BLOCKED_OUT': return `url(#${prefix}-out)`
    case 'BLOCKED_BOTH': return `url(#${prefix}-both)`
    default: return ok
  }
}
