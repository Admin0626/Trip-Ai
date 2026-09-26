// 格式化工具（03-前端设计.md §二 utils/format.ts）

/** 金额格式化：3000 → ¥3,000 */
export function formatMoney(value: number | string | null | undefined): string {
  if (value === null || value === undefined) return '¥0'
  const n = Number(value)
  if (Number.isNaN(n)) return '¥0'
  return '¥' + n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

/** 日期补零屏蔽 */
export function pad2(n: number): string {
  return n < 10 ? '0' + n : String(n)
}

/** 当前日期 + 偏移天数 → yyyy-MM-dd（预约至少明天、规划今天起） */
export function dateAfter(days: number): string {
  const d = new Date()
  d.setDate(d.getDate() + days)
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}`
}

/** 今天 → yyyy-MM-dd */
export function today(): string {
  return dateAfter(0)
}

/** 时间点展示：null/空 → '--' */
export function timePoint(t?: string | null): string {
  return t && t.trim() ? t : '--'
}

/** 预约状态文案映射 */
export function bookingStatusText(status: number): string {
  return { 0: '待确认', 1: '已确认', 2: '已取消', 3: '已完成' }[status] ?? '未知'
}

export type TagType = 'primary' | 'success' | 'warning' | 'info' | 'danger'

const bookingStatusTypeMap: Record<number, TagType> = { 0: 'warning', 1: 'success', 2: 'info', 3: 'primary' }

export function bookingStatusType(status: number): TagType {
  return bookingStatusTypeMap[status] ?? 'info'
}

/** 规划状态文案 */
export function planStatusText(status: number): string {
  return status === 1 ? '已保存' : '草稿'
}