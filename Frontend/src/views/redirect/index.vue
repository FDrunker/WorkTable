<template>
  <div></div>
</template>

<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
const { params, query } = route
const { path } = params

const targetPath = String(path || '')
router.replace({
  path: targetPath.startsWith('admin/') ? `/${targetPath}` : `/admin/${targetPath}`,
  query
})
</script>
