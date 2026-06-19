export function formatRelativeTime(dateStr: string): string {
  const date = new Date(dateStr)
  const now = new Date()
  const diff = Math.floor((now.getTime() - date.getTime()) / 1000)

  if (diff < 60) return '刚刚'
  if (diff < 3600) return `${Math.floor(diff / 60)} 分钟前`
  if (diff < 86400) return `${Math.floor(diff / 3600)} 小时前`
  if (diff < 2592000) return `${Math.floor(diff / 86400)} 天前`

  const month = date.getMonth() + 1
  const day = date.getDate()
  return `${month}月${day}日`
}

export function truncate(text: string, max: number): string {
  return text.length > max ? text.slice(0, max) + '…' : text
}