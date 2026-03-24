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
      aria-label="Resize Mermaid editor and preview panel"
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
    >
      <v-toolbar
        density="compact"
        flat
      >
        <v-spacer />
        <v-btn
          icon="mdi-close"
          size="x-small"
          variant="text"
          @click="closeResults"
        />
      </v-toolbar>

      <div class="editor-panel__preview-wrap">
        <v-alert
          v-if="renderError"
          class="ma-4 editor-panel__preview-error"
          density="comfortable"
          type="error"
          variant="tonal"
        >
          {{ renderError }}
        </v-alert>

        <div
          ref="previewRoot"
          class="editor-panel__preview"
          :class="{ 'editor-panel__preview--hidden': !!renderError }"
        />
      </div>
    </v-sheet>
  </section>
</template>

<script lang="ts" setup>
import CodeMirror from 'codemirror'
import 'codemirror/lib/codemirror.css'
import 'codemirror/mode/markdown/markdown.js'
import mermaid from 'mermaid'
import { nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'

const props = defineProps<{
  applyQuery: string
  applyRunAfter: boolean
  applyRequestId: number
  resetRequestId: number
  runRequestId: number
}>()

const panelRoot = ref<HTMLElement | null>(null)
const editorRoot = ref<HTMLElement | null>(null)
const previewRoot = ref<HTMLElement | null>(null)
const editor = shallowRef<CodeMirror.EditorFromTextArea | null>(null)
const loadError = ref('')
const renderError = ref('')
const isRunning = ref(false)
const resultsVisible = ref(false)
const resultsHeightPercent = ref(34)
const isDraggingResults = ref(false)
const runTimeoutId = ref<number | null>(null)
const renderSequence = ref(0)

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
  return Math.min(85, Math.max(20, value))
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

async function renderDiagram() {
  if (!previewRoot.value || !editor.value) {
    return
  }

  renderSequence.value += 1
  const currentRender = renderSequence.value
  const source = editor.value.getValue().trim()

  if (!source) {
    renderError.value = 'Enter Mermaid diagram source to render a preview.'
    previewRoot.value.innerHTML = ''
    return
  }

  try {
    renderError.value = ''
    const { svg } = await mermaid.render(`mermaid-preview-${currentRender}`, source)
    if (currentRender !== renderSequence.value || !previewRoot.value) {
      return
    }

    renderError.value = ''
    previewRoot.value.innerHTML = svg
  } catch (error) {
    if (currentRender !== renderSequence.value) {
      return
    }

    previewRoot.value.innerHTML = ''
    renderError.value = error instanceof Error ? error.message : 'Unable to render Mermaid diagram.'
  }
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
    await renderDiagram()
    refreshEditor()
  }, 80)
}

function closeResults() {
  resultsVisible.value = false
  stopResultsDrag()
  renderError.value = ''
  if (previewRoot.value) {
    previewRoot.value.innerHTML = ''
  }
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

function resetEditorPanel() {
  clearRunTimer()
  if (editor.value) {
    editor.value.setValue('')
  }
  resultsHeightPercent.value = 34
  closeResults()
  editor.value?.focus()
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

watch(
  () => props.resetRequestId,
  (resetRequestId) => {
    if (resetRequestId > 0) {
      resetEditorPanel()
    }
  },
)

watch(resultsVisible, async (visible) => {
  if (visible) {
    await nextTick()
    await renderDiagram()
  }
})

onMounted(() => {
  if (!editorRoot.value) {
    return
  }

  mermaid.initialize({
    startOnLoad: false,
    securityLevel: 'loose',
    theme: 'neutral',
  })

  try {
    const input = document.createElement('textarea')
    input.value = ''
    editorRoot.value.appendChild(input)

    editor.value = CodeMirror.fromTextArea(input, {
      lineNumbers: true,
      lineWrapping: true,
      mode: 'text/x-markdown',
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
  display: flex;
  flex: 0 0 auto;
  flex-direction: column;
  min-height: 160px;
  overflow: hidden;
}

.editor-panel__preview-wrap {
  flex: 1 1 auto;
  min-height: 0;
  overflow: auto;
}

.editor-panel__preview {
  min-height: 100%;
  padding: 16px;
  border: 1px solid rgba(var(--v-border-color), var(--v-border-opacity));
  background: rgb(var(--v-theme-surface));
}

.editor-panel__preview--hidden {
  display: none;
}

.editor-panel__preview-error {
  flex: 0 0 auto;
}

.editor-panel__preview :deep(svg) {
  display: block;
  max-width: 100%;
  height: auto;
  margin: 0 auto;
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
