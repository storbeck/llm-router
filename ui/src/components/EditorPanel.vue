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

  <div
    v-else
    ref="editorRoot"
    class="editor-panel"
  />
</template>

<script lang="ts" setup>
import CodeMirror from 'codemirror'
import 'codemirror/lib/codemirror.css'
import 'codemirror/mode/cypher/cypher.js'
import { onBeforeUnmount, onMounted, ref, shallowRef } from 'vue'

const editorRoot = ref<HTMLElement | null>(null)
const editor = shallowRef<CodeMirror.EditorFromTextArea | null>(null)
const loadError = ref('')

const initialValue = `MATCH p=()-[]-()
RETURN p
LIMIT 25`

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
      mode: 'application/x-cypher-query',
      tabSize: 2,
    })

    editor.value.setSize('100%', '100%')
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : 'Unable to load CodeMirror.'
  }
})

onBeforeUnmount(() => {
  editor.value?.toTextArea()
})
</script>

<style scoped>
.editor-panel {
  flex: 1 1 42%;
  min-height: 0;
  min-width: 320px;
  height: 100%;
  overflow: hidden;
}

.editor-panel :deep(.CodeMirror) {
  height: 100%;
  font-family: 'Roboto Mono', monospace;
  font-size: 13px;
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
