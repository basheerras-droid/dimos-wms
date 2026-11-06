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

export const locationsConfig: EntityConfig = {
  key: 'locations',
  path: `${MD}/locations`,
  exportType: 'locations',
  defaultSort: 'code,asc',
  columns: [
    { field: 'code', width: 170, sortable: true },
    { field: 'name', flex: 1, sortable: true },
    { field: 'areaCode', width: 130 },
    { field: 'locationTypeCode', width: 150 },
    { field: 'levelNo', type: 'number', width: 100, sortable: true },
    { field: 'aisle', width: 90, sortable: true },
    { field: 'columnNo', type: 'number', width: 100, sortable: true },
    { field: 'rowNo', type: 'number', width: 90, sortable: true },
    { field: 'enabled', type: 'boolean', width: 100, sortable: true },
    { field: 'blockedIn', type: 'boolean', width: 120 },
    { field: 'blockedOut', type: 'boolean', width: 120 },
    { field: 'blockReason', width: 200, hiddenByDefault: true },
    { field: 'xMm', type: 'number', width: 100, hiddenByDefault: true },
    { field: 'yMm', type: 'number', width: 100, hiddenByDefault: true },
    { field: 'zMm', type: 'number', width: 100, hiddenByDefault: true },
    { field: 'maxWeightKg', type: 'number', width: 140, hiddenByDefault: true },
    { field: 'externalRef', width: 160, hiddenByDefault: true },
  ],
  fields: [
    { name: 'code', type: 'text', required: true, maxLength: 50, helpKey: 'md.help.code' },
    { name: 'name', type: 'text', maxLength: 100 },
    { name: 'areaId', type: 'ref', required: true, refPath: `${MD}/areas`, displayField: 'areaCode' },
    { name: 'locationTypeId', type: 'ref', required: true, refPath: `${MD}/location-types`, displayField: 'locationTypeCode' },
    { name: 'levelNo', type: 'integer', helpKey: 'md.help.levelNo' },
    { name: 'aisle', type: 'text', maxLength: 20 },
    { name: 'side', type: 'text', maxLength: 10 },
    { name: 'columnNo', type: 'integer' },
    { name: 'rowNo', type: 'integer' },
    { name: 'xMm', type: 'integer', helpKey: 'md.help.position' },
    { name: 'yMm', type: 'integer' },
    { name: 'zMm', type: 'integer' },
    { name: 'maxWeightKg', type: 'decimal', min: 0 },
    { name: 'enabled', type: 'boolean', helpKey: 'md.help.enabled' },
    { name: 'blockedIn', type: 'boolean', helpKey: 'md.help.blockedIn' },
    { name: 'blockedOut', type: 'boolean', helpKey: 'md.help.blockedOut' },
    { name: 'blockReason', type: 'text', maxLength: 255, helpKey: 'md.help.blockReason' },
    { name: 'externalRef', type: 'text', maxLength: 100, helpKey: 'md.help.externalRef' },
    { name: 'extAttributes', type: 'multiline', maxLength: 4000, helpKey: 'md.help.extAttributes' },
  ],
  filters: [
    { name: 'areaId', type: 'ref', refPath: `${MD}/areas` },
    { name: 'locationTypeId', type: 'ref', refPath: `${MD}/location-types` },
    { name: 'levelNo', type: 'integer' },
    { name: 'enabled', type: 'boolean' },
    { name: 'blocked', type: 'boolean' },
  ],
  defaults: {
    code: '', name: '', areaId: '', locationTypeId: '', levelNo: '0', aisle: '', side: '', columnNo: '', rowNo: '',
    xMm: '', yMm: '', zMm: '', maxWeightKg: '', enabled: true, blockedIn: false, blockedOut: false, blockReason: '',
    externalRef: '', extAttributes: '',
  },
  bulk: {
    path: `${MD}/locations/bulk`,
    max: 1000,
    actions: [
      { action: 'BLOCK_IN', labelKey: 'md.bulk.blockIn', needsReason: true },
      { action: 'UNBLOCK_IN', labelKey: 'md.bulk.unblockIn' },
      { action: 'BLOCK_OUT', labelKey: 'md.bulk.blockOut', needsReason: true },
      { action: 'UNBLOCK_OUT', labelKey: 'md.bulk.unblockOut' },
      { action: 'ENABLE', labelKey: 'md.bulk.enable' },
      { action: 'DISABLE', labelKey: 'md.bulk.disable' },
    ],
  },
}

export const locationLinksConfig: EntityConfig = {
  key: 'locationLinks',
  path: `${MD}/location-links`,
  exportType: 'location-links',
  defaultSort: 'id,asc',
  searchKey: 'md.locationLinks.search',
  rowLabel: (row) => `${String(row.fromLocationCode)} -> ${String(row.toLocationCode)}`,
  columns: [
    { field: 'fromLocationCode', flex: 1 },
    { field: 'toLocationCode', flex: 1 },
    { field: 'huTypeCode', width: 150, emptyKey: 'md.allTypes' },
    { field: 'equipmentCode', width: 150 },
    { field: 'cost', type: 'number', width: 100, sortable: true },
    { field: 'enabled', type: 'boolean', width: 100, sortable: true },
  ],
  fields: [
    { name: 'fromLocationId', type: 'ref', required: true, refPath: `${MD}/locations`, displayField: 'fromLocationCode' },
    { name: 'toLocationId', type: 'ref', required: true, refPath: `${MD}/locations`, displayField: 'toLocationCode' },
    { name: 'huTypeId', type: 'ref', refPath: `${MD}/hu-types`, displayField: 'huTypeCode', helpKey: 'md.help.huTypeEmpty' },
    { name: 'equipmentId', type: 'ref', refPath: `${MD}/equipment`, displayField: 'equipmentCode', helpKey: 'md.help.linkEquipment' },
    { name: 'cost', type: 'integer', min: 0, helpKey: 'md.help.cost' },
    { name: 'enabled', type: 'boolean' },
  ],
  filters: [
    { name: 'huTypeId', type: 'ref', refPath: `${MD}/hu-types` },
    { name: 'enabled', type: 'boolean' },
  ],
  defaults: { fromLocationId: '', toLocationId: '', huTypeId: '', equipmentId: '', cost: '1', enabled: true },
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
