export function resolveSelectionLabelFields(field = {}, selectionType = '') {
  const fieldName = String(field.field || '').trim()
  const candidates = [
    field.props?.referenceDisplayField,
    field.referenceDisplayField,
    field.props?.displayField,
    field.displayField,
    field.props?.labelField,
    field.labelField,
    field.props?.targetLabelField,
    field.targetLabelField,
    field.props?.labelValueField,
    field.labelValueField,
    field.props?.targetField,
    field.targetField,
  ]
  if (fieldName) {
    candidates.push(`${fieldName}Name`)
    if (fieldName.endsWith('UserId')) {
      candidates.push(fieldName.replace(/UserId$/, 'UserName'))
      candidates.push(fieldName.replace(/UserId$/, 'Name'))
    }
    if (fieldName.endsWith('DeptId')) {
      candidates.push(fieldName.replace(/DeptId$/, 'DeptName'))
      candidates.push(fieldName.replace(/DeptId$/, 'Name'))
    }
    if (fieldName.endsWith('OrgId')) {
      candidates.push(fieldName.replace(/OrgId$/, 'OrgName'))
      candidates.push(fieldName.replace(/OrgId$/, 'Name'))
    }
    if (fieldName.endsWith('Id'))
      candidates.push(fieldName.replace(/Id$/, 'Name'))
    candidates.push(`${fieldName}Label`, `${fieldName}Text`)
  }
  if (selectionType === 'user')
    candidates.push('userName', 'realName', 'nickname')
  if (selectionType === 'org')
    candidates.push('orgName', 'deptName', 'departmentName')
  return candidates
    .map(value => String(value || '').trim())
    .filter((value, index, all) => value && value !== fieldName && all.indexOf(value) === index)
}
