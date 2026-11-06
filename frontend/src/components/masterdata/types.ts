import type { Versioned } from '../../api/masterdata'

export type FieldType = 'text' | 'multiline' | 'integer' | 'decimal' | 'boolean' | 'select'

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
  hiddenByDefault?: boolean
}

export interface FilterDef {
  name: string
  type: 'select' | 'boolean'
  options?: string[]
  optionsLabelKey?: string
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
}

export type Row = Versioned & Record<string, unknown>
