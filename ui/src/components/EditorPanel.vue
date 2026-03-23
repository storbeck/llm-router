<template>
  <div
    v-if="loadError"
    class="editor-panel editor-panel--error"
  >
    <v-alert
      class="ma-4"
      density="comfortable"
      type="error"
      variant="tonal"
    >
      {{ loadError }}
    </v-alert>
  </div>

  <section
    v-else
    ref="panelRoot"
    class="editor-panel"
  >
    <div
      ref="editorRoot"
      class="editor-panel__editor"
    />

    <div
      v-if="resultsVisible"
      class="editor-panel__results-divider"
      :class="{ 'editor-panel__results-divider--dragging': isDraggingResults }"
      aria-label="Resize query editor and results panel"
      role="separator"
      tabindex="0"
      @keydown.up.prevent="resizeResultsByKeyboard(5)"
      @keydown.down.prevent="resizeResultsByKeyboard(-5)"
      @pointerdown="startResultsDrag"
    />

    <v-sheet
      v-if="resultsVisible"
      class="editor-panel__results"
      :style="{ flexBasis: `${resultsHeightPercent}%` }"
      border="t"
      rounded="0"
      tile
    >
      <v-toolbar
        density="compact"
        flat
      >
        <v-toolbar-title class="text-body-2 font-weight-medium">
          Response
        </v-toolbar-title>
        <v-spacer />
        <v-btn
          icon="mdi-close"
          size="x-small"
          variant="text"
          @click="closeResults"
        />
      </v-toolbar>

      <v-table
        class="editor-panel__results-table"
        density="compact"
        fixed-header
        height="100%"
      >
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Status</th>
              <th>Notes</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in mockRows"
              :key="row.id"
            >
              <td>{{ row.id }}</td>
              <td>{{ row.name }}</td>
              <td>{{ row.status }}</td>
              <td>{{ row.notes }}</td>
            </tr>
          </tbody>
      </v-table>
    </v-sheet>
  </section>
</template>

<script lang="ts" setup>
import CodeMirror from 'codemirror'
import 'codemirror/lib/codemirror.css'
import 'codemirror/mode/sql/sql.js'
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'

const props = defineProps<{
  applyQuery: string
  applyRunAfter: boolean
  applyRequestId: number
  runRequestId: number
}>()

const panelRoot = ref<HTMLElement | null>(null)
const editorRoot = ref<HTMLElement | null>(null)
const editor = shallowRef<CodeMirror.EditorFromTextArea | null>(null)
const loadError = ref('')
const isRunning = ref(false)
const resultsVisible = ref(false)
const resultsHeightPercent = ref(34)
const isDraggingResults = ref(false)
const runTimeoutId = ref<number | null>(null)

const initialValue = `SELECT 1 AS result;`

const mockRows = [
  { id: 'n-001', name: 'Lorem Node', status: 'active', notes: 'Lorem ipsum dolor sit amet' },
  { id: 'n-002', name: 'Ipsum Edge', status: 'pending', notes: 'Consectetur adipiscing elit' },
  { id: 'n-003', name: 'Dolor Path', status: 'archived', notes: 'Sed do eiusmod tempor' },
  { id: 'n-004', name: 'Sit Query', status: 'active', notes: 'Incididunt ut labore et dolore' },
]

function refreshEditor() {
  requestAnimationFrame(() => {
    editor.value?.refresh()
  })
}

function clearRunTimer() {
  if (runTimeoutId.value !== null) {
    window.clearTimeout(runTimeoutId.value)
    runTimeoutId.value = null
  }
}

function clampResultsHeight(value: number) {
  return Math.min(55, Math.max(20, value))
}

function updateResultsHeight(clientY: number) {
  if (!panelRoot.value) {
    return
  }

  const bounds = panelRoot.value.getBoundingClientRect()
  if (!bounds.height) {
    return
  }

  const nextHeight = ((bounds.bottom - clientY) / bounds.height) * 100
  resultsHeightPercent.value = clampResultsHeight(nextHeight)
  refreshEditor()
}

function handleResultsDrag(event: PointerEvent) {
  updateResultsHeight(event.clientY)
}

function stopResultsDrag() {
  isDraggingResults.value = false
  window.removeEventListener('pointermove', handleResultsDrag)
  window.removeEventListener('pointerup', stopResultsDrag)
}

function startResultsDrag(event: PointerEvent) {
  isDraggingResults.value = true
  updateResultsHeight(event.clientY)
  window.addEventListener('pointermove', handleResultsDrag)
  window.addEventListener('pointerup', stopResultsDrag)
}

function resizeResultsByKeyboard(delta: number) {
  resultsHeightPercent.value = clampResultsHeight(resultsHeightPercent.value + delta)
  refreshEditor()
}

async function runQuery() {
  if (isRunning.value) {
    return
  }

  isRunning.value = true
  clearRunTimer()

  runTimeoutId.value = window.setTimeout(async () => {
    resultsVisible.value = true
    isRunning.value = false
    clearRunTimer()
    await nextTick()
    refreshEditor()
  }, 450)
}

function closeResults() {
  resultsVisible.value = false
  stopResultsDrag()
  refreshEditor()
}

async function applyQuery(query: string, runAfterApply: boolean) {
  if (!editor.value) {
    return
  }

  editor.value.setValue(query)
  editor.value.focus()
  refreshEditor()

  if (runAfterApply) {
    await runQuery()
  }
}

watch(
  () => props.runRequestId,
  (runRequestId) => {
    if (runRequestId > 0) {
      void runQuery()
    }
  },
)

watch(
  () => props.applyRequestId,
  (applyRequestId) => {
    if (applyRequestId > 0 && props.applyQuery.trim()) {
      void applyQuery(props.applyQuery, props.applyRunAfter)
    }
  },
)

onMounted(() => {
  if (!editorRoot.value) {
    return
  }

  try {
    const input = document.createElement('textarea')
    input.value = initialValue
    editorRoot.value.appendChild(input)

    editor.value = CodeMirror.fromTextArea(input, {
      lineNumbers: true,
      lineWrapping: true,
      mode: 'text/x-sql',
      tabSize: 2,
    })

    editor.value.setSize('100%', '100%')
    refreshEditor()
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : 'Unable to load CodeMirror.'
  }
})

onBeforeUnmount(() => {
  clearRunTimer()
  stopResultsDrag()
  editor.value?.toTextArea()
})
</script>

<style scoped>
.editor-panel {
  display: flex;
  flex: 1 1 42%;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  min-width: 320px;
}

.editor-panel__editor {
  flex: 1 1 auto;
  min-height: 0;
  overflow: hidden;
}

.editor-panel :deep(.CodeMirror) {
  height: 100%;
  font-family: 'Roboto Mono', monospace;
  font-size: 13px;
}

.editor-panel__results-divider {
  position: relative;
  flex: 0 0 12px;
  cursor: row-resize;
  background: transparent;
}

.editor-panel__results-divider::before {
  content: '';
  position: absolute;
  top: 50%;
  right: 0;
  left: 0;
  height: 1px;
  transform: translateY(-50%);
  background: rgba(var(--v-border-color), var(--v-border-opacity));
}

.editor-panel__results-divider::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 48px;
  height: 4px;
  border-radius: 999px;
  transform: translate(-50%, -50%);
  background: rgba(var(--v-theme-on-surface), 0.22);
}

.editor-panel__results-divider:hover::after,
.editor-panel__results-divider:focus-visible::after,
.editor-panel__results-divider--dragging::after {
  background: rgba(var(--v-theme-primary), 0.7);
}

.editor-panel__results-divider:focus-visible {
  outline: none;
}

.editor-panel__results {
  flex: 0 0 auto;
  min-height: 160px;
  overflow: hidden;
}

.editor-panel__results-table {
  height: calc(100% - 48px);
}

.editor-panel--error {
  display: flex;
  align-items: flex-start;
  background: rgb(var(--v-theme-surface));
}

@media (max-width: 960px) {
  .editor-panel {
    min-width: 0;
  }
}
</style>
