<script setup lang="ts">
/**
 * 全局 SVG Symbol Sprite 注入器
 * 原型在每页顶部用 <svg width=0 height=0><symbol.../></svg> 定义图标，
 * 通过 <use href="#id"/> 引用。这里将所有页面的 symbol 合并去重后一次性注入，
 * 保持与原型完全一致的 <use> 引用方式，零视觉差异。
 * 仅需在 App.vue 挂载一次。
 */
import spriteRaw from './icon-sprite.svg?raw'
</script>

<template>
  <!-- eslint-disable vue/no-v-html -->
  <span class="icon-sprite-mount" v-html="spriteRaw"></span>
</template>

<style>
.icon-sprite-mount {
  position: absolute;
  width: 0;
  height: 0;
  overflow: hidden;
}
</style>
