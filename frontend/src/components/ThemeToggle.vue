<template>
  <button
    class="theme-toggle"
    :class="{ dark: theme.dark }"
    type="button"
    :title="theme.dark ? '切换到亮色主题' : '切换到暗色主题'"
    :aria-label="theme.dark ? '切换到亮色主题' : '切换到暗色主题'"
    @click="theme.toggle()"
  >
    <!-- 太阳（亮色） -->
    <svg v-if="!theme.dark" class="tt-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
      <circle cx="12" cy="12" r="4.2" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
    <!-- 月亮（暗色） -->
    <svg v-else class="tt-icon" viewBox="0 0 24 24" fill="currentColor">
      <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z" />
    </svg>
  </button>
</template>

<script setup>
import { useThemeStore } from '../stores/theme'
const theme = useThemeStore()
</script>

<style scoped>
.theme-toggle {
  width: 36px; height: 36px; padding: 0;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: 50%;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink-2);
  cursor: pointer;
  transition: all .25s ease;
  box-shadow: var(--shadow-xs);
}
.theme-toggle:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary-light-5);
  transform: rotate(-12deg) scale(1.06);
}
.theme-toggle.dark { background: var(--surface-2); }
.tt-icon { width: 19px; height: 19px; display: block; }
</style>
