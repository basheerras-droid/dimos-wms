import { MD } from '../../api/masterdata'
import type { EntityConfig } from './types'

const AREA_TYPES = ['INBOUND', 'STORAGE', 'OUTBOUND', 'BUFFER', 'WORKSTATION', 'TRANSPORT']

export const areasConfig: EntityConfig = {
  key: 'areas',
  path: `${MD}/areas`,
  exportType: 'areas',
  defaultSort: 'code,asc',
  columns: [
    { field: 'code', width: 160, sortable: true },
    { field: 'name', flex: 1, sortable: true },
    { field: 'areaType', width: 170, sortable: true, optionsLabelKey: 'md.areaTypes' },
  ],
  fields: [
    { name: 'code', type: 'text', required: true, maxLength: 30, helpKey: 'md.help.code' },
    { name: 'name', type: 'text', required: true, maxLength: 100 },
    { name: 'areaType', type: 'select', required: true, options: AREA_TYPES, optionsLabelKey: 'md.areaTypes' },
  ],
  filters: [{ name: 'areaType', type: 'select', options: AREA_TYPES, optionsLabelKey: 'md.areaTypes' }],
  defaults: { code: '', name: '', areaType: 'STORAGE' },
}

export const locationTypesConfig: EntityConfig = {
  key: 'locationTypes',
  path: `${MD}/location-types`,
  exportType: 'location-types',
  defaultSort: 'code,asc',
  columns: [
    { field: 'code', width: 160, sortable: true },
    { field: 'name', flex: 1, sortable: true },
    { field: 'storage', type: 'boolean', width: 110 },
    { field: 'transfer', type: 'boolean', width: 110 },
    { field: 'workstation', type: 'boolean', width: 130 },
    { field: 'capacityHu', type: 'number', width: 140, sortable: true },
  ],
  fields: [
    { name: 'code', type: 'text', required: true, maxLength: 30, helpKey: 'md.help.code' },
    { name: 'name', type: 'text', required: true, maxLength: 100 },
    { name: 'storage', type: 'boolean', helpKey: 'md.help.storage' },
    { name: 'transfer', type: 'boolean', helpKey: 'md.help.transfer' },
    { name: 'workstation', type: 'boolean' },
    { name: 'capacityHu', type: 'integer', min: 0, helpKey: 'md.help.capacityHu' },
  ],
  defaults: { code: '', name: '', storage: false, transfer: false, workstation: false, capacityHu: '1' },
}

export const huTypesConfig: EntityConfig = {
  key: 'huTypes',
  path: `${MD}/hu-types`,
  exportType: 'hu-types',
  defaultSort: 'code,asc',
  columns: [
    { field: 'code', width: 160, sortable: true },
    { field: 'name', flex: 1, sortable: true },
    { field: 'lengthMm', type: 'number', width: 120, sortable: true },
    { field: 'widthMm', type: 'number', width: 120, sortable: true },
    { field: 'heightMm', type: 'number', width: 120, sortable: true },
    { field: 'tareKg', type: 'number', width: 110 },
    { field: 'maxGrossKg', type: 'number', width: 140 },
    { field: 'container', type: 'boolean', width: 120 },
  ],
  fields: [
    { name: 'code', type: 'text', required: true, maxLength: 30, helpKey: 'md.help.code' },
    { name: 'name', type: 'text', required: true, maxLength: 100 },
    { name: 'lengthMm', type: 'integer', min: 1 },
    { name: 'widthMm', type: 'integer', min: 1 },
    { name: 'heightMm', type: 'integer', min: 1 },
    { name: 'tareKg', type: 'decimal', min: 0 },
    { name: 'maxGrossKg', type: 'decimal', min: 0 },
    { name: 'container', type: 'boolean', helpKey: 'md.help.container' },
  ],
  filters: [{ name: 'container', type: 'boolean' }],
  defaults: {
    code: '', name: '', lengthMm: '', widthMm: '', heightMm: '', tareKg: '', maxGrossKg: '', container: false,
  },
}
