<template>
  <image
    class="sl-avatar"
    :style="{ width: size + 'px', height: size + 'px', borderRadius: size / 2 + 'px' }"
    :src="src"
    mode="aspectFill"
    @click="emit('click')"
  />
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    src: string;
    size?: number;
  }>(),
  {
    size: 36,
  },
);

const emit = defineEmits<{
  (e: 'click'): void;
}>();
</script>

<style scoped>
.sl-avatar {
  background: var(--surface-3, #f5f5f4);
  display: block;
}
</style>
