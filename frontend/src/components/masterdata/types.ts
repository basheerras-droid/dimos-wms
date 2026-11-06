import type { ComponentType, ReactElement } from 'react'
import type { Versioned } from '../../api/masterdata'

export type FieldType = 'text' | 'multiline' | 'integer' | 'decimal' | 'boolean' | 'select' | 'ref'

/** One input in the edit drawer. The label comes from i18n key `md.fields.<name>`. */
export interface FieldDef {
  name: string
  type: FieldType
  required?: boolean
  maxLength?: number
  min?: number
  /** For type "select": the allowed values; labels come from `<optionsLabelKey>.<value>`. */
  options?: string[]
  optionsLabelKey?: string
  /** For type "ref": a link to a record of another table, chosen by searching it on the server. */
  refPath?: string
  /** For type "ref": the field of the loaded row that shows the chosen record (e.g. "areaCode"). */
  displayField?: string
  /** Optional hint under the field, i18n key. */
  helpKey?: string
  /** Show the field only when editing an existing record (never when creating). */
  editOnly?: boolean
}

/** One column of the grid. The header comes from i18n key `md.fields.<field>`. */
export interface ColumnDef {
  field: string
  type?: 'text' | 'boolean' | 'number'
  width?: number
  flex?: number
  /** The server can sort by this column. */
  sortable?: boolean
  /** Show values through `<optionsLabelKey>.<value>`. */
  optionsLabelKey?: string
  /** Text shown when the value is empty (i18n key), e.g. "all types". */
  emptyKey?: string
  hiddenByDefault?: boolean
}

export interface FilterDef {
  name: string
  type: 'select' | 'boolean' | 'integer' | 'ref'
  options?: string[]
  optionsLabelKey?: string
  /** For type "ref": the table whose records are offered. */
  refPath?: string
}

/** One bulk action on the selected rows, sent to `bulk.path` as { ids, action, reason }. */
export interface BulkAction {
  action: string
  /** i18n key of the button text. */
  labelKey: string
  /** Ask for a reason first (e.g. when blocking). */
  needsReason?: boolean
}

/** An extra button on every row that opens a dialog for that row (e.g. "assign locations"). */
export interface RowAction {
  key: string
  /** i18n key of the tooltip. */
  labelKey: string
  icon: ReactElement
  dialog: ComponentType<{ row: Row; canEdit: boolean; onClose: () => void }>
}

/** Everything the generic list + drawer needs to manage one master-data table. */
export interface EntityConfig {
  /** i18n namespace under `md.`, e.g. "areas" -> md.areas.title */
  key: string
  path: string
  /** Name used by the export endpoint (/api/masterdata/export/{exportType}). */
  exportType: string
  columns: ColumnDef[]
  fields: FieldDef[]
  filters?: FilterDef[]
  defaults: Record<string, string | boolean>
  defaultSort: string
  /** Name of a row in messages such as "X will be deleted" (default: its code). */
  rowLabel?: (row: Row) => string
  /** Select several rows and apply one action to all of them. */
  bulk?: { path: string; actions: BulkAction[]; max: number }
  rowActions?: RowAction[]
  /** Placeholder of the search box, i18n key (default md.search). */
  searchKey?: string
}

export type Row = Versioned & Record<string, unknown>
