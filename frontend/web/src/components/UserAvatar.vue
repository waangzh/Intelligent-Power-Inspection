<template>
  <el-avatar :key="avatarSrc" :size="size" :src="avatarSrc" :style="fallbackStyle">
    {{ initials }}
  </el-avatar>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { generateDefaultAvatar, getInitials } from '@/utils/avatar'

const props = withDefaults(
  defineProps<{
    displayName: string
    avatarUrl?: string
    seed?: string
    size?: number
  }>(),
  { size: 32 },
)

const initials = computed(() => getInitials(props.displayName))

const avatarSrc = computed(() => {
  if (props.avatarUrl) return props.avatarUrl
  return generateDefaultAvatar(props.displayName, props.seed ?? props.displayName)
})

const fallbackStyle = computed(() => ({
  backgroundColor: props.avatarUrl ? undefined : 'transparent',
  flexShrink: 0,
}))
</script>
