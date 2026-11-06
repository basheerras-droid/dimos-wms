/** "CODE - Name" for a record, or just the code when there is no (different) name. */
export const itemText = (i: { code: string; name?: string | null }) =>
  i.name && i.name !== i.code ? `${i.code} - ${i.name}` : i.code
