import { flushPromises, mount } from '@vue/test-utils'
import { NModal } from 'naive-ui'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { resolveRenderableFileUrl } from '@/utils'
import AuthImage from '../AuthImage.vue'

vi.mock('@/utils', () => ({
  removeCachedFileAccessUrl: vi.fn(),
  resolveRenderableFileUrl: vi.fn(),
}))

describe('auth image preview', () => {
  beforeEach(() => {
    vi.mocked(resolveRenderableFileUrl).mockResolvedValue('blob:authenticated-image')
  })

  it('opens the enlarged view with the resolved authenticated URL', async () => {
    const wrapper = mount(AuthImage, {
      props: { src: 'file-1001', alt: '申请附件', lazy: false, preview: true },
    })
    await flushPromises()

    expect(wrapper.findComponent(NModal).props('show')).toBe(false)
    await wrapper.find('.auth-image-host').trigger('click')

    expect(wrapper.findComponent(NModal).props('show')).toBe(true)
    expect(wrapper.find('.auth-image-host img').attributes('src')).toBe('blob:authenticated-image')
    expect(resolveRenderableFileUrl).toHaveBeenCalledWith('file-1001', 43200)
  })

  it('keeps caller attributes on the thumbnail host', async () => {
    const wrapper = mount(AuthImage, {
      attrs: { style: 'width: 32px; height: 32px;', title: '附件图片' },
      props: { src: 'file-1003', lazy: false, preview: true },
    })
    await flushPromises()

    expect(wrapper.find('.auth-image-host').attributes('style')).toContain('width: 32px')
    expect(wrapper.find('.auth-image-host').attributes('title')).toBe('附件图片')
  })

  it('does not open a preview unless explicitly enabled', async () => {
    const wrapper = mount(AuthImage, {
      props: { src: 'file-1002', lazy: false },
    })
    await flushPromises()
    await wrapper.find('.auth-image-host').trigger('click')

    expect(wrapper.findComponent(NModal).props('show')).toBe(false)
  })
})
