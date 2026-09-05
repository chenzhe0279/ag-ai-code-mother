<template>
  <div class="code-diff">
    <div class="diff-head">
      <div class="head-stat remove">
        <span class="dot">−</span>
        {{ stats.removals }} removal{{ stats.removals === 1 ? '' : 's' }}
      </div>
      <div class="head-lines">{{ oldLines }} lines</div>
      <a-button size="small" type="text" @click="copy('old')">Copy</a-button>

      <div class="head-arrow">→</div>

      <div class="head-stat add">
        <span class="dot">+</span>
        {{ stats.additions }} addition{{ stats.additions === 1 ? '' : 's' }}
      </div>
      <div class="head-lines">{{ newLines }} lines</div>
      <a-button size="small" type="text" @click="copy('new')">Copy</a-button>
    </div>

    <div class="diff-body">
      <div class="diff-col old">
        <div v-for="(row, idx) in pairs" :key="'o' + idx" class="diff-row">
          <div class="gutter"></div>
          <div :class="['cell', row.old?.type || 'blank']">
            <span class="line-no">{{ row.old?.no ?? '' }}</span>
            <pre class="code">{{ row.old?.text ?? '' }}</pre>
          </div>
        </div>
      </div>

      <div class="diff-col new">
        <div v-for="(row, idx) in pairs" :key="'n' + idx" class="diff-row">
          <div class="gutter"></div>
          <div :class="['cell', row.new?.type || 'blank']">
            <span class="line-no">{{ row.new?.no ?? '' }}</span>
            <pre class="code">{{ row.new?.text ?? '' }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { message } from 'ant-design-vue'
import {
  buildDiffPairs,
  computeLineDiff,
  countChanges,
  type DiffRowPair,
} from '@/utils/diff'

const props = defineProps<{ oldText: string; newText: string; oldLabel?: string; newLabel?: string }>()

const pairs = computed<DiffRowPair[]>(() =>
  buildDiffPairs(computeLineDiff(props.oldText, props.newText)),
)
const stats = computed(() => countChanges(pairs.value))
const oldLines = computed(() => (props.oldText || '').split('\n').length)
const newLines = computed(() => (props.newText || '').split('\n').length)

const copy = async (side: 'old' | 'new') => {
  const text = side === 'old' ? props.oldText : props.newText
  try {
    await navigator.clipboard.writeText(text)
    message.success(`已复制${side === 'old' ? '旧' : '新'}版本内容`)
  } catch {
    message.warning('复制失败，请手动选择复制')
  }
}
</script>

<style scoped>
.code-diff {
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  overflow: hidden;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12.5px;
}

.diff-head {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  background: #fafafa;
  border-bottom: 1px solid #eee;
}

.head-stat {
  display: flex;
  align-items: center;
  font-weight: 600;
  font-size: 13px;
}

.head-stat.remove {
  color: #c9424a;
}

.head-stat.add {
  color: #1a7f37;
}

.dot {
  display: inline-block;
  width: 16px;
  height: 16px;
  line-height: 16px;
  text-align: center;
  border-radius: 50%;
  color: #fff;
  margin-right: 6px;
  font-size: 12px;
}

.remove .dot {
  background: #c9424a;
}

.add .dot {
  background: #1a7f37;
}

.head-lines {
  color: #999;
  font-size: 12px;
}

.head-arrow {
  color: #999;
}

.diff-body {
  display: flex;
}

.diff-col {
  flex: 1;
  min-width: 0;
}

.diff-col.old {
  border-right: 1px solid #eee;
}

.cell {
  display: flex;
  line-height: 1.5;
  min-height: 19px;
}

.cell.removed {
  background: #ffeef0;
}

.cell.added {
  background: #e6ffec;
}

.cell.blank {
  background: #fafafa;
}

.line-no {
  width: 40px;
  text-align: right;
  padding-right: 10px;
  color: #bbb;
  user-select: none;
  flex-shrink: 0;
}

.code {
  margin: 0;
  padding: 0 10px 0 6px;
  white-space: pre;
  overflow-x: auto;
  color: #333;
  flex: 1;
}
</style>
