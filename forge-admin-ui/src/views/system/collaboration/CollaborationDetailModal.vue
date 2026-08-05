<script>
import { NButton, NDescriptions, NDescriptionsItem, NModal, NSpace } from 'naive-ui'
import { computed, defineComponent, h } from 'vue'
import DictTag from '@/components/DictTag.vue'

/**
 * 企业协同运维只读详情弹窗。
 * 各运维页（同步批次/问题单/投递记录/回调事件）复用同一详情范式，
 * 通过 fields 配置声明展示项，支持字典标签、自定义渲染与长文本预格式化。
 *
 * fields 项：
 *   { key, label, span?, dictType?, pre?, hidden?, render?(row) }
 *   - dictType：按字典渲染 DictTag
 *   - pre：长文本以 <pre> 预格式化展示（如推送正文/JSON）
 *   - render：自定义返回字符串或 VNode，优先级最高
 */
export default defineComponent({
  name: 'CollaborationDetailModal',
  props: {
    show: { type: Boolean, default: false },
    title: { type: String, default: '查看详情' },
    width: { type: [String, Number], default: 680 },
    column: { type: Number, default: 1 },
    fields: { type: Array, default: () => [] },
    data: { type: Object, default: null },
  },
  emits: ['update:show'],
  setup(props, { emit }) {
    const visibleFields = computed(() => props.fields.filter(field => !field.hidden))

    function renderValue(field) {
      const row = props.data || {}
      const raw = row[field.key]
      if (typeof field.render === 'function')
        return field.render(row)
      if (field.dictType)
        return h(DictTag, { dictType: field.dictType, value: raw, size: 'small' })
      const isBlank = raw === null || raw === undefined || raw === ''
      const text = isBlank ? '-' : String(raw)
      if (field.pre)
        return h('pre', { class: 'collab-detail-pre' }, text)
      return text
    }

    return () => h(NModal, {
      'show': props.show,
      'onUpdate:show': value => emit('update:show', value),
      'preset': 'card',
      'title': props.title,
      'style': { width: typeof props.width === 'number' ? `${props.width}px` : props.width },
      'maskClosable': true,
    }, {
      default: () => props.data
        ? h(NDescriptions, {
            bordered: true,
            column: props.column,
            size: 'small',
            labelPlacement: 'left',
            labelStyle: { width: '132px', whiteSpace: 'nowrap' },
          }, () => visibleFields.value.map(field => h(NDescriptionsItem, {
            key: field.key,
            label: field.label,
            span: field.span || 1,
          }, () => renderValue(field))))
        : null,
      footer: () => h(NSpace, { justify: 'end' }, () => h(NButton, {
        onClick: () => emit('update:show', false),
      }, () => '关闭')),
    })
  },
})
</script>

<style>
.collab-detail-pre {
  margin: 0;
  max-height: 320px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: var(--font-family-mono, 'SFMono-Regular', Consolas, monospace);
  font-size: 12px;
  line-height: 1.6;
}
</style>
