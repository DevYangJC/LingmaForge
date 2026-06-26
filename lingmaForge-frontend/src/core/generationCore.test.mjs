import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createInitialWorkbenchState,
  reduceGenerationMessage,
  reduceGenerationComplete,
  reduceGenerationError,
} from './generationCore.mjs'

test('routes pipeline messages into chat, checklist, files, logs, and preview state', () => {
  let state = createInitialWorkbenchState('task-1', 'Build a pricing app')

  state = reduceGenerationMessage(state, {
    threadId: 'task-1',
    nodeName: 'execution_planning',
    text: JSON.stringify({
      files: [
        { path: 'src/App.vue', content: '<template>App</template>' },
        { path: 'src/styles.css', content: '.app { color: teal; }' },
      ],
    }),
    textType: 'JSON',
    error: false,
  })

  assert.equal(state.checklist.execution_planning, 'done')
  assert.equal(state.chatMessages.at(-1).contentType, 'JSON')
  assert.deepEqual(
    state.files.map((file) => file.path),
    ['src/App.vue', 'src/styles.css'],
  )
  assert.equal(state.activeFilePath, 'src/App.vue')

  state = reduceGenerationMessage(state, {
    threadId: 'task-1',
    nodeName: 'build_verification',
    text: 'Build passed in 1.2s',
    textType: 'TEXT',
    error: false,
  })

  assert.equal(state.logs.at(-1).source, 'build')
  assert.equal(state.logs.at(-1).message, 'Build passed in 1.2s')

  state = reduceGenerationMessage(state, {
    threadId: 'task-1',
    nodeName: 'preview_deploy',
    text: 'https://preview.example.test',
    textType: 'TEXT',
    error: false,
  })

  assert.equal(state.previewUrl, 'https://preview.example.test')
})

test('complete events move the workbench into complete mode with a running preview', () => {
  const state = reduceGenerationComplete(createInitialWorkbenchState('task-2', 'Build dashboard'), {
    threadId: 'task-2',
    url: 'https://sandbox.example.test',
    port: 5173,
    buildTime: 2.4,
  })

  assert.equal(state.mode, 'complete')
  assert.equal(state.isGenerating, false)
  assert.equal(state.sandboxStatus, 'running')
  assert.equal(state.previewUrl, 'https://sandbox.example.test')
  assert.equal(state.checklist.preview_deploy, 'done')
})

test('error events stop generation and keep a system message visible', () => {
  const state = reduceGenerationError(createInitialWorkbenchState('task-3', 'Build CRM'), 'SSE connection failed')

  assert.equal(state.isGenerating, false)
  assert.equal(state.sandboxStatus, 'error')
  assert.equal(state.chatMessages.at(-1).role, 'system')
  assert.equal(state.chatMessages.at(-1).level, 'error')
})
