<template>
  <div id="basicLayout">
    <!-- 整体布局容器，使用 Ant Design Layout 实现页面结构 -->
    <a-layout style="min-height: 100vh">
      <!-- 头部区域：渲染全局头部组件 -->
      <a-layout-header class="header">
        <GlobalHeader />
      </a-layout-header>

      <!-- 主体区域：包含侧边栏和内容区 -->
      <a-layout>
        <!-- 全局侧边栏组件 -->
        <global-sider/>
        <!-- 内容区域：渲染路由匹配的页面组件 -->
        <a-layout-content class="content">
          <router-view />
        </a-layout-content>
      </a-layout>
    </a-layout>
  </div>
</template>

<script setup lang="ts">
// 导入全局头部组件
import GlobalHeader from '@/components/layout/GlobalHeader.vue'
// 导入全局侧边栏组件
import GlobalSider from '@/components/layout/GlobalSider.vue'
</script>

<style scoped>
/* 内容区样式：线性渐变背景 + 内边距 */
#basicLayout .content {
  background: linear-gradient(to right, #fefefe, #fff);
  padding: 20px;
}

/* 头部样式：内边距 + 底部 margin + 背景色 */
#basicLayout .header {
  padding-inline: 20px;
  margin-bottom: 16px;
  color: unset;
  background: white;
}
</style>
