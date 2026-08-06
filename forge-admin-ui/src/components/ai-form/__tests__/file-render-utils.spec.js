import { describe, expect, it } from 'vitest'
import { isImageFileName, resolveFileRenderItems, splitFileValues } from '../file-render-utils'

describe('file render utils', () => {
  it('识别常见图片扩展名并忽略查询参数', () => {
    expect(isImageFileName('1784902600090_download.png')).toBe(true)
    expect(isImageFileName('photo.JPEG?download=1')).toBe(true)
    expect(isImageFileName('report.pdf')).toBe(false)
    expect(isImageFileName('image.png.tmp')).toBe(false)
  })

  it('拆分数组和逗号分隔的附件值', () => {
    expect(splitFileValues(['a,b', ' c '])).toEqual(['a', 'b', 'c'])
  })

  it('按值顺序配对显示名称并在名称缺失时回退原值', () => {
    expect(resolveFileRenderItems('id-1,id-2', 'photo.png,report.pdf')).toEqual([
      { value: 'id-1', name: 'photo.png' },
      { value: 'id-2', name: 'report.pdf' },
    ])
    expect(resolveFileRenderItems('id-1', '')).toEqual([
      { value: 'id-1', name: 'id-1' },
    ])
  })
})
