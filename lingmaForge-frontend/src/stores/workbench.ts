import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 工作台状态管理
 * ------------------------------------------------------------------
 * 业务逻辑：工作台包含两种交互模式
 *   - simple（简洁模式）：对应 lingma-workbench-standalone.html，初始状态，
 *     仅一个输入框；用户输入需求并按回车后触发切换。
 *   - generation（生成模式）：对应 lingma-workbench.html，回车事件后无缝切换。
 *
 * 通过 Pinia 集中管理模式与用户输入，使 SimpleMode / GenerationMode 两个子组件
 * 能无缝共享状态并完成切换，避免组件间直接耦合。
 */
export type WorkbenchMode = 'simple' | 'generation'

export const useWorkbenchStore = defineStore('workbench', () => {
  /** 当前交互模式，初始为简洁模式 */
  const mode = ref<WorkbenchMode>('simple')

  /** 用户在简洁模式输入的需求文本，切换到生成模式后由对话区消费 */
  const prompt = ref('')

  /** 当前选中的 AI 模型名称 */
  const model = ref('灵码 UI Pro')

  /** 可选模型列表 */
  const models = ['灵码 UI Pro', '灵码 UI Standard', '灵码 Speed Fast']

  /**
   * 触发模式切换：simple -> generation
   * 仅当用户输入了非空需求时才切换，并把输入带入生成模式。
   */
  function submit(value: string) {
    const trimmed = value.trim()
    if (!trimmed) return false
    prompt.value = trimmed
    mode.value = 'generation'
    return true
  }

  /** 回退回简洁模式（清空生成状态） */
  function reset() {
    mode.value = 'simple'
    prompt.value = ''
  }

  /** 循环切换模型 */
  function cycleModel() {
    const idx = models.indexOf(model.value)
    const next = models[(idx + 1) % models.length]
    if (next) model.value = next
  }

  return { mode, prompt, model, models, submit, reset, cycleModel }
})
