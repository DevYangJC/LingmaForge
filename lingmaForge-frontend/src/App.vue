<script setup lang="ts">
import { NConfigProvider } from 'naive-ui'
import { themeOverrides } from '@/config/naive-theme'
import IconSprite from '@/components/IconSprite.vue'
import hljs from 'highlight.js/lib/core'
import typescript from 'highlight.js/lib/languages/typescript'
import javascript from 'highlight.js/lib/languages/javascript'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import json from 'highlight.js/lib/languages/json'
import 'highlight.js/styles/atom-one-light.css'

hljs.registerLanguage('typescript', typescript)
hljs.registerLanguage('javascript', javascript)
hljs.registerLanguage('xml', xml) // Vue template/HTML
hljs.registerLanguage('vue', xml) // Map vue to xml
hljs.registerLanguage('html', xml) // Map html to xml
hljs.registerLanguage('css', css)
hljs.registerLanguage('json', json)
</script>
<template>
  <NConfigProvider :theme-overrides="themeOverrides" :hljs="hljs">
    <IconSprite />
    <router-view />
  </NConfigProvider>
</template>
