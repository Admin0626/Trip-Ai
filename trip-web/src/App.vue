<script setup lang="ts">
import { onBeforeUnmount, ref } from 'vue'
import router from '@/router'
import { getAuthSession, onAuthChange } from '@/utils/storage'

// A new login must discard forms and lists loaded for the previous session.
const epoch=ref(getAuthSession()?.epoch??'guest')
const unsubscribe=onAuthChange(()=>{
  const session=getAuthSession(),current=router.currentRoute.value
  epoch.value=session?.epoch??'guest'
  if(!session&&!current.meta.public&&current.path!=='/login')
    void router.replace({path:'/login',query:{redirect:current.fullPath}})
  else if(current.meta.admin&&session?.userInfo.role!=='ADMIN')void router.replace('/')
})
onBeforeUnmount(unsubscribe)
</script>

<template>
  <router-view :key="epoch" />
</template>
