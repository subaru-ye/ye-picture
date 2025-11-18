<template>
  <!-- Ant Design 全局配置提供者：用于统一设置组件语言等全局配置 -->
  <a-config-provider :locale="appLocale">
    <div id="app">
      <!-- 应用根布局组件，包含头部、侧边栏和内容区 -->
      <BasicLayout />
    </div>
  </a-config-provider>
</template>

<script setup lang="ts">
// 导入根布局组件
import BasicLayout from "@/layouts/BasicLayout.vue";
// 导入 Ant Design 中文语言包
import zhCN from 'ant-design-vue/es/locale/zh_CN';
// 导入日期处理库 dayjs 及其中文语言包
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
// 导入登录用户状态管理 Store
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

// 初始化登录用户 Store
const loginUserStore = useLoginUserStore()
// 页面加载时获取当前登录用户信息（同步到全局状态）
loginUserStore.fetchLoginUser()

// 配置 dayjs 默认语言为中文（确保日期显示符合中文习惯）
dayjs.locale('zh-cn');

// 配置 Ant Design 组件库的语言环境为中文
const appLocale = zhCN;
</script>

<style scoped>
/* 根组件样式（当前无额外样式，保留作用域） */
</style>
