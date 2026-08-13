import assert from 'node:assert/strict'
import test from 'node:test'
import {
  applyEventMappings,
  buildEventClearPatch,
  buildEventParams,
  buildDefaultData,
  ensureChildRows,
  normalizeScanContext,
  normalizeDesignerField,
  normalizeField,
  normalizeMainFields,
  resolveFieldControl,
  resolveChildRows,
  resolveChildTitle,
  syncChildRowAliases,
  shouldSkipFieldEvent,
} from '../lowcode-runtime.js'
import { scanBarcode } from '../barcode-scanner.js'

test('field value controls another field visibility and required state', () => {
  const field = normalizeField({
    field: 'cashAmount',
    type: 'money',
    required: true,
    props: {
      runtimeRules: [{
        enabled: true,
        mode: 'all',
        conditions: [{ source: 'formData', field: 'payMethod', operator: 'eq', value: 'CASH' }],
        effect: { visible: true, whenUnmatched: 'hidden' },
      }],
    },
  })

  assert.deepEqual(resolveFieldControl(field, { formData: { payMethod: 'STATIC_CODE' } }), {
    visible: false,
    readonly: false,
    required: true,
  })
  assert.equal(resolveFieldControl(field, { formData: { payMethod: 'CASH' } }).visible, true)
})

test('field event maps governed query parameters and response fields', () => {
  const rule = {
    resultMode: 'FIRST_ROW',
    paramMappings: [{ source: 'FORM_FIELD', field: 'barcode', param: 'barcode' }],
    resultMappings: [
      { from: 'productName', to: 'productName' },
      { from: 'price', to: 'price' },
    ],
  }

  assert.deepEqual(buildEventParams(rule, { barcode: '6901234567890' }), { barcode: '6901234567890' })
  assert.deepEqual(applyEventMappings(rule, { records: [{ productName: '测试商品', price: 1200 }] }).patch, {
    productName: '测试商品',
    price: 1200,
  })
})

test('field event skips blank sources and clears only governed targets', () => {
  const rule = {
    sourceField: 'mobile',
    skipWhenEmpty: true,
    resultMappings: [
      { from: 'memberId', to: 'memberId', whenMissing: 'CLEAR' },
      { from: 'memberLevel', to: 'memberLevel', whenMissing: 'KEEP' },
    ],
  }

  assert.equal(shouldSkipFieldEvent(rule, { mobile: '  ' }), true)
  assert.equal(shouldSkipFieldEvent(rule, { mobile: '13800000000' }), false)
  assert.deepEqual(buildEventClearPatch(rule), { memberId: '' })
})

test('scan context is length-bounded and contains only declared values', () => {
  assert.deepEqual(normalizeScanContext({ value: ' SKU-001 ', type: 'barCode', platform: 'H5', token: 'ignored' }), {
    value: 'SKU-001',
    type: 'barCode',
    platform: 'H5',
  })
  assert.equal(normalizeScanContext({ value: '' }), null)
})

test('enterprise WeChat scanner result is normalized', async () => {
  const navigatorDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'navigator')
  const previousWx = globalThis.wx
  Object.defineProperty(globalThis, 'navigator', {
    configurable: true,
    value: { userAgent: 'wxwork/4.1' },
  })
  globalThis.wx = {
    scanQRCode(options) {
      options.success({ resultStr: 'CODE-128,6901234567890', scanType: 'barCode' })
    },
  }

  try {
    const result = await scanBarcode()
    assert.deepEqual(result, {
      value: 'CODE-128,6901234567890',
      type: 'barCode',
      platform: 'WECHAT_ENTERPRISE',
    })
  }
  finally {
    if (navigatorDescriptor)
      Object.defineProperty(globalThis, 'navigator', navigatorDescriptor)
    else
      delete globalThis.navigator
    if (previousWx === undefined)
      delete globalThis.wx
    else
      globalThis.wx = previousWx
  }
})

test('designer field default value initializes H5 form data', () => {
  const field = normalizeDesignerField({
    label: '收款方式',
    componentKey: 'dictSelect',
    props: { defaultValue: 'STATIC_CODE' },
    fieldBinding: { fieldCode: 'payMethod' },
  })

  assert.equal(field.defaultValue, 'STATIC_CODE')
  assert.deepEqual(buildDefaultData([field]), { payMethod: 'STATIC_CODE' })
})

test('presale payment method controls static-code and cash fields while system fields stay hidden', () => {
  const fields = normalizeMainFields({
    editSchema: [
      { field: 'salesUserId', label: '导购userid', type: 'input', formVisible: false, hidden: true },
      { field: 'payMethod', label: '收款方式', type: 'dictSelect', defaultValue: 'STATIC_CODE' },
      {
        field: 'staticPaymentNo',
        label: '静态码单号',
        type: 'input',
        runtimeRules: [{
          enabled: true,
          conditions: [{ source: 'formData', field: 'payMethod', operator: 'eq', value: 'STATIC_CODE' }],
          effect: { visible: true, whenUnmatched: 'hidden' },
        }],
      },
      {
        field: 'cashAmount',
        label: '现金金额',
        type: 'money',
        runtimeRules: [{
          enabled: true,
          conditions: [{ source: 'formData', field: 'payMethod', operator: 'eq', value: 'CASH' }],
          effect: { visible: true, whenUnmatched: 'hidden' },
        }],
      },
      { field: 'status', label: '状态', type: 'dictSelect', formVisible: false, hidden: true },
    ],
  })

  assert.deepEqual(fields.map(field => field.field), ['payMethod', 'staticPaymentNo', 'cashAmount'])
  assert.equal(resolveFieldControl(fields[1], { formData: { payMethod: 'STATIC_CODE' } }).visible, true)
  assert.equal(resolveFieldControl(fields[2], { formData: { payMethod: 'STATIC_CODE' } }).visible, false)
  assert.equal(resolveFieldControl(fields[1], { formData: { payMethod: 'CASH' } }).visible, false)
  assert.equal(resolveFieldControl(fields[2], { formData: { payMethod: 'CASH' } }).visible, true)
})

test('child rows and title support relation keys and Chinese relation labels', () => {
  const child = {
    key: 'operation_logs',
    relationKey: 'operation_logs',
    modelCode: 'ps_presale_operation_log',
    relationName: '操作日志',
  }
  const childData = {
    operation_logs: [{ id: 1 }],
  }

  assert.equal(resolveChildTitle(child), '操作日志')
  assert.deepEqual(resolveChildRows(child, childData), [{ id: 1 }])
  syncChildRowAliases(child, childData)
  assert.deepEqual(childData.ps_presale_operation_log, [{ id: 1 }])
  ensureChildRows({ modelCode: 'ps_presale_order_item', relationKey: 'presale_items' }, childData).push({ id: 2 })
  assert.deepEqual(childData.ps_presale_order_item, [{ id: 2 }])
})
