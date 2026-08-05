import path from 'node:path'
import { globSync } from 'glob'
import dynamicIcons from '../src/static/icons/dynamic-icons.js'

/**
 * 获取图标列表，用于支持动态渲染自定义图标
 */
export function getIcons() {
  const feFiles = globSync('src/static/icons/ai-icon/*.svg', { nodir: true, strict: true })
  const feIcons = feFiles.map((filePath) => {
    const fileName = path.basename(filePath)
    const fileNameWithoutExt = path.parse(fileName).name
    return `ai-icon:${fileNameWithoutExt}`
  })

  return [...dynamicIcons, ...feIcons]
}

/**
 * 生成 .vue 文件路径列表
 */
export function getPagePathes() {
  const files = globSync('src/pages/**/*.vue')
  return files.map(item => `/${path.normalize(item).replace(/\\/g, '/')}`)
}
