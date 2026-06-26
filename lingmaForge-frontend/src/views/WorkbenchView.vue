<script setup lang="ts">
/**
 * 工作台入口 —— 双模式切换控制器
 * ------------------------------------------------------------------
 * 简洁模式（SimpleMode）：初始状态，单输入框。
 * 生成模式（GenerationMode）：用户在简洁模式输入需求并回车后无缝切换进入。
 *
 * 模式状态集中在 Pinia (stores/workbench.ts)，由 SimpleMode 触发 submit()，
 * 本组件仅根据 store.mode 渲染对应子组件，并用 <Transition> 实现无缝过渡。
 */
import { storeToRefs } from 'pinia'
import { useWorkbenchStore } from '@/stores/workbench'
import SimpleMode from '@/components/workbench/SimpleMode.vue'
import GenerationMode from '@/components/workbench/GenerationMode.vue'

const store = useWorkbenchStore()
const { mode } = storeToRefs(store)
</script>

<template>
  <Transition name="wb-switch" mode="out-in">
    <SimpleMode v-if="mode === 'simple'" key="simple" />
    <GenerationMode v-else key="generation" />
  </Transition>
</template>

<style>
/* 模式切换过渡：与全局页面过渡同语汇，旧模式左滑淡出、新模式从右滑入 */
.wb-switch-enter-active {
  transition: opacity 0.4s cubic-bezier(0, 0, 0.2, 1),
    transform 0.55s cubic-bezier(0.16, 1, 0.3, 1);
  transition-delay: 0.1s;
}
.wb-switch-leave-active {
  transition: opacity 0.22s cubic-bezier(0.4, 0, 1, 1),
    transform 0.45s cubic-bezier(0.4, 0, 1, 1);
}
.wb-switch-enter-from {
  opacity: 0;
  transform: translateY(12px) scale(0.99);
}
.wb-switch-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.99);
}
</style>
