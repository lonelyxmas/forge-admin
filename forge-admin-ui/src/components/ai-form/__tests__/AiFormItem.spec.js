import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import AiFormItem from '../AiFormItem.vue'

vi.mock('vue-router', () => ({
  useRoute: () => ({
    query: {},
    params: {},
    path: '/',
    fullPath: '/',
    name: 'ai-form-item-test',
  }),
}))

const NFormItemStub = {
  name: 'NFormItem',
  template: '<div><slot name="label" /><slot /></div>',
}

const NInputNumberStub = {
  name: 'NInputNumber',
  props: ['value', 'min', 'max', 'step'],
  template: '<input class="n-input-number-stub">',
}

const naiveStubs = Object.fromEntries([
  'NInput',
  'NSelect',
  'NRadio',
  'NSpace',
  'NRadioGroup',
  'NRadioButton',
  'NCheckbox',
  'NCheckboxGroup',
  'NSwitch',
  'NDatePicker',
  'NTimePicker',
  'NButton',
  'NUpload',
  'NSlider',
  'NRate',
  'NColorPicker',
  'NCascader',
  'NTreeSelect',
  'NTransfer',
  'NInputGroup',
  'NIcon',
].map(name => [name, true]))

describe('aiFormItem number field compatibility', () => {
  it('renders input-number as NInputNumber and forwards numeric constraints', () => {
    const wrapper = mount(AiFormItem, {
      props: {
        field: {
          field: 'sort',
          label: '排序',
          type: 'input-number',
          props: {
            min: 0,
            max: 100,
            step: 5,
          },
        },
        value: 10,
      },
      global: {
        stubs: {
          ...naiveStubs,
          NFormItem: NFormItemStub,
          NInputNumber: NInputNumberStub,
          AiRecordSelectorModal: true,
        },
      },
    })

    const numberInput = wrapper.findComponent(NInputNumberStub)
    expect(numberInput.exists()).toBe(true)
    expect(numberInput.props()).toMatchObject({
      value: 10,
      min: 0,
      max: 100,
      step: 5,
    })
    expect(wrapper.find('.n-input-number-stub').exists()).toBe(true)
  })
})
