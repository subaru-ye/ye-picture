import { saveAs } from 'file-saver'

/**
 * 格式化文件大小
 * @param size
 */
export const formatSize = (size?: number) => {
  if (!size) return '未知'
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(2) + ' KB'
  return (size / (1024 * 1024)).toFixed(2) + ' MB'
}
/**
 * 下载图片
 * @param url 图片下载地址
 * @param fileName 要保存为的文件名
 * @returns Promise，下载操作成功触发后 resolve
 */
export async function downloadImage(url?: string, fileName?: string): Promise<void> {
  if (!url) {
    throw new Error('图片地址不存在')
  }
  
  try {
    // 先通过 fetch 获取图片的 blob 数据，避免直接使用 URL 导致新窗口打开
    const response = await fetch(url, {
      mode: 'cors', // 尝试跨域请求
    })
    if (!response.ok) {
      throw new Error('下载失败：网络请求错误')
    }
    const blob = await response.blob()
    
    // 如果没有指定文件名，尝试从 URL 中提取文件名
    let finalFileName = fileName
    if (!finalFileName) {
      try {
        const urlPath = new URL(url).pathname
        const urlFileName = urlPath.split('/').pop() || 'image'
        finalFileName = urlFileName.includes('.') ? urlFileName : `${urlFileName}.jpg`
      } catch {
        // 如果 URL 解析失败，使用默认文件名
        finalFileName = 'image.jpg'
      }
    }
    
    // 使用 blob 下载，避免新窗口打开
    saveAs(blob, finalFileName)
    // saveAs 是同步操作，执行到这里说明下载已成功触发
    return
  } catch (error) {
    console.error('通过 fetch 下载失败，尝试使用备用方案：', error)
    // 如果 fetch 失败（可能是跨域问题），使用创建临时链接的方式下载
    try {
      const link = document.createElement('a')
      link.href = url
      link.download = fileName || 'image.jpg'
      link.style.display = 'none'
      document.body.appendChild(link)
      link.click()
      // 延迟移除，确保点击事件完成
      setTimeout(() => {
        document.body.removeChild(link)
      }, 100)
      // link.click() 执行成功，说明下载已触发
      return
    } catch (fallbackError) {
      console.error('备用下载方案也失败：', fallbackError)
      // 最后的回退方案：直接使用 file-saver（可能会打开新窗口）
      try {
        saveAs(url, fileName)
        return
      } catch (finalError) {
        throw new Error('所有下载方案都失败：' + (finalError as Error).message)
      }
    }
  }
}

/**
 * 将颜色值转换为十六进制颜色格式
 * @param input 颜色值，可以是 "0x" 开头的十六进制字符串或其他格式
 * @returns 标准 #RRGGBB 格式的颜色字符串
 */
export function toHexColor(input: string): string {
  // 去掉 0x 前缀
  const colorValue = input.startsWith('0x') ? input.slice(2) : input

  // 将剩余部分解析为十六进制数，再转成 6 位十六进制字符串
  const hexColor = parseInt(colorValue, 16).toString(16).padStart(6, '0')

  // 返回标准 #RRGGBB 格式
  return `#${hexColor}`
}


