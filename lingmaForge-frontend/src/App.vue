<script setup lang="ts">
import { NConfigProvider } from 'naive-ui'
import { themeOverrides } from '@/config/naive-theme'
import IconSprite from '@/components/IconSprite.vue'
</script>

<template>
  <NConfigProvider :theme-overrides="themeOverrides">
    <IconSprite />
    <router-view />
  </NConfigProvider>
</template>
