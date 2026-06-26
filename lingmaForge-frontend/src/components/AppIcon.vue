<script setup lang="ts">
/**
 * 通用图标组件 —— 对应原型中的 <svg class="icon"><use href="#name"/></svg>
 * 用法：<AppIcon name="spark" />  或  <AppIcon name="spark" :size="15" color="#00aee9" />
 * 也可直接通过 class / style 透传，与原型内联写法一致。
 */
withDefaults(
  defineProps<{
    name: string
    size?: number | string
    color?: string
  }>(),
  {
    size: undefined,
    color: undefined,
  },
)
</script>

<template>
  <svg
    class="icon"
    :style="{
      width: size !== undefined ? (typeof size === 'number' ? size + 'px' : size) : undefined,
      height: size !== undefined ? (typeof size === 'number' ? size + 'px' : size) : undefined,
      color: color,
    }"
  >
    <use :href="`#${name}`" />
  </svg>
</template>
