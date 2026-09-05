/**
 * 逐行 diff 算法（基于 LCS 最长公共子序列）
 * 返回按顺序排列的操作序列，供并排渲染视图使用。
 */

export type DiffOp =
  | { type: 'equal'; oldLine: string; newLine: string }
  | { type: 'remove'; oldLine: string }
  | { type: 'add'; newLine: string }

export function computeLineDiff(oldText: string, newText: string): DiffOp[] {
  const a = (oldText || '').split('\n')
  const b = (newText || '').split('\n')
  const n = a.length
  const m = b.length

  // dp[i][j] = a[0..i-1] 与 b[0..j-1] 的 LCS 长度
  const dp: number[][] = Array.from({ length: n + 1 }, () => new Array<number>(m + 1).fill(0))
  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      dp[i][j] =
        a[i - 1] === b[j - 1]
          ? dp[i - 1][j - 1] + 1
          : Math.max(dp[i - 1][j], dp[i][j - 1])
    }
  }

  // 回溯路径
  const ops: DiffOp[] = []
  let i = n
  let j = m
  while (i > 0 && j > 0) {
    if (a[i - 1] === b[j - 1]) {
      ops.push({ type: 'equal', oldLine: a[i - 1], newLine: b[j - 1] })
      i--
      j--
    } else if (dp[i - 1][j] >= dp[i][j - 1]) {
      ops.push({ type: 'remove', oldLine: a[i - 1] })
      i--
    } else {
      ops.push({ type: 'add', newLine: b[j - 1] })
      j--
    }
  }
  while (i > 0) {
    ops.push({ type: 'remove', oldLine: a[i - 1] })
    i--
  }
  while (j > 0) {
    ops.push({ type: 'add', newLine: b[j - 1] })
    j--
  }

  return ops.reverse()
}

/** 将 diff 操作序列转换为左右并排的行对，用于渲染 */
export interface DiffRowPair {
  old: { no: number; text: string; type: 'context' | 'removed' | 'blank' } | null
  isBlank: boolean
  new: { no: number; text: string; type: 'context' | 'added' | 'blank' } | null
}

export function buildDiffPairs(ops: DiffOp[]): DiffRowPair[] {
  const rows: DiffRowPair[] = []
  let oldNo = 1
  let newNo = 1
  let i = 0

  while (i < ops.length) {
    const op = ops[i]
    // 上下文行
    if (op.type === 'equal') {
      rows.push({
        old: { no: oldNo++, text: op.oldLine, type: 'context' },
        isBlank: false,
        new: { no: newNo++, text: op.newLine, type: 'context' },
      })
      i++
      continue
    }

    // 收集一块连续变更：先 remove 段，再 add 段（通常成对出现）
    const removedLines: string[] = []
    const addedLines: string[] = []
    while (i < ops.length && ops[i].type !== 'equal') {
      const cur = ops[i]
      if (cur.type === 'remove') {
        removedLines.push(cur.oldLine)
      } else {
        addedLines.push(cur.newLine)
      }
      i++
    }

    const maxRows = Math.max(removedLines.length, addedLines.length)
    for (let k = 0; k < maxRows; k++) {
      const hasOld = k < removedLines.length
      const hasNew = k < addedLines.length
      const wasRemoved = hasOld && !hasNew
      const wasAdded = !hasOld && hasNew
      rows.push({
        old: hasOld
          ? { no: oldNo++, text: removedLines[k], type: wasRemoved ? 'removed' : 'context' }
          : null,
        isBlank: !hasOld && !hasNew,
        new: hasNew
          ? { no: newNo++, text: addedLines[k], type: wasAdded ? 'added' : 'context' }
          : null,
      })
    }
  }

  return rows
}

/** 统计变更数量 */
export function countChanges(rows: DiffRowPair[]) {
  let removals = 0
  let additions = 0
  for (const r of rows) {
    if (r.old?.type === 'removed') removals++
    if (r.new?.type === 'added') additions++
  }
  return { removals, additions }
}
