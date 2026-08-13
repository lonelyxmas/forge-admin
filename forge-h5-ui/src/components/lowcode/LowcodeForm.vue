<template>
  <view class="lowcode-form">
    <LowcodeField
      v-for="field in renderedFields"
      :key="field.field"
      :field="field"
      :model-value="data[field.field]"
      :options="fieldOptions(field)"
      :readonly="readonly"
      :error="errors[field.field]"
      @update:model-value="updateField(field, $event)"
      @blur="emit('field-event', { trigger: 'BLUR', field, data })"
      @change="emit('field-event', { trigger: 'CHANGE', field, data })"
      @scan="emit('field-event', { trigger: 'SCAN_COMPLETE', field, data, scan: $event })"
    />
  </view>
</template>

<script setup>
import { computed, reactive } from 'vue'
import LowcodeField from './LowcodeField.vue'
import { resolveFieldControl } from '@/utils/lowcode-runtime'

const props = defineProps({
  fields: { type: Array, default: () => [] },
  data: { type: Object, default: () => ({}) },
  dictOptions: { type: Object, default: () => ({}) },
  currentChildren: { type: Object, default: () => ({}) },
  readonly: { type: Boolean, default: false },
  context: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['update:data', 'field-event'])
const errors = reactive({})
const renderedFields = computed(() => props.fields.map(field => ({
  ...field,
  __runtimeControl: resolveFieldControl(field, {
    record: props.data,
    formData: props.data,
    row: props.data,
    route: { query: props.context.routeQuery || {} },
    user: props.context.user || {},
  }),
})).filter(field => field.__runtimeControl.visible))

function fieldOptions(field) {
  if (field.type === 'dictSelect') return props.dictOptions[field.dictType || field.props?.dictType] || []
  const source = field.props?.options || field.options
  if (Array.isArray(source)) return source.map(item => typeof item === 'object' ? item : ({ label: String(item), value: item }))
  if (field.props?.optionSource?.type === 'CURRENT_CHILDREN') {
    const sourceRows = props.currentChildren[field.props.optionSource.relationKey] || []
    return sourceRows.filter(row => props.currentChildren[field.props.optionSource.relationKey]).map(row => ({
      label: row[field.props.optionSource.labelField] ?? row.id,
      value: row[field.props.optionSource.valueField] ?? row.id,
    }))
  }
  return []
}

function updateField(field, value) {
  props.data[field.field] = value
  delete errors[field.field]
  emit('update:data', props.data)
}

function validate() {
  Object.keys(errors).forEach(key => delete errors[key])
  for (const field of props.fields) {
    const control = resolveFieldControl(field, { record: props.data, formData: props.data, row: props.data })
    if (!control.visible || !control.required || props.readonly || control.readonly) continue
    const value = props.data[field.field]
    if (value === undefined || value === null || value === '' || (Array.isArray(value) && !value.length)) {
      errors[field.field] = field.requiredMessage || `请输入${field.label}`
    }
  }
  return Object.keys(errors).length === 0
}

defineExpose({ validate })
</script>

<style lang="scss" scoped>
.lowcode-form { display: flex; flex-direction: column; }
</style>
