<template>
  <div id="userMessagesPage">
    <a-page-header title="我的消息" sub-title="查看图片审核通知" />

    <a-card title="审核通知">
      <!-- 刷新按钮和未读计数 -->
      <template #extra>
        <a-space>
          <a-badge :count="unreadCount" :overflow-count="99" v-if="unreadCount > 0">
            <a-button @click="loadNotices" :loading="loading">
              <ReloadOutlined v-if="!loading" />
              刷新
            </a-button>
          </a-badge>
          <a-button v-else @click="loadNotices" :loading="loading">
            <ReloadOutlined v-if="!loading" />
            刷新
          </a-button>
        </a-space>
      </template>

      <!-- 通知列表 -->
      <a-list
        item-layout="vertical"
        :data-source="noticeList"
        :loading="loading"
        :pagination="paginationProps"
      >
        <template #renderItem="{ item }">
          <a-list-item key="item.id" :class="{ 'unread-item': item.isRead === 0 }">
            <template #actions>
              <!-- 根据 reviewStatus 显示不同状态 -->
              <span v-if="item.reviewStatus === 0">
                <a-tag color="orange">审核中</a-tag>
              </span>
              <span v-else-if="item.reviewStatus === 1">
                <a-tag color="green">已通过</a-tag>
              </span>
              <span v-else-if="item.reviewStatus === 2">
                <a-tag color="red">已拒绝</a-tag>
              </span>
              <!-- 标记已读按钮 -->
              <span v-if="item.isRead === 0">
                <a-button type="link" size="small" @click="markAsRead(item.id)">标记已读</a-button>
              </span>
            </template>
            <a-list-item-meta
              :title="item.pictureTitle || '图片审核通知'"
              :description="formatDateTime(item.createTime)"
            >
            </a-list-item-meta>
            <!-- 显示审核结果信息和图片 -->
            <div class="notice-content">
              <!-- 图片展示区域，使用 a-image 显示图片 -->
              <div v-if="item.pictureUrl || item.pictureId" class="image-preview">
                <a-image
                  :width="128"
                :height="96"
                :src="item.pictureUrl || `/api/file/get/${item.pictureId}`"
                :alt="item.pictureTitle || '审核图片'"
                style="object-fit: cover; margin-bottom: 8px; border-radius: 4px;"
                >
                <!-- 可选：加载失败时的占位符或错误处理 -->
                <template #placeholder>
                  <div style="background: #f5f5f5; height: 100%; width: 100%; display: flex; align-items: center; justify-content: center;">
                    加载中...
                  </div>
                </template>
                <template #fallback>
                  <div style="background: #f0f0f0; height: 100%; width: 100%; display: flex; align-items: center; justify-content: center;">
                    <span>加载失败</span>
                  </div>
                </template>
                </a-image>
              </div>
              <!-- <p><strong>图片ID:</strong> {{ item.pictureId || 'N/A' }}</p> 移除图片ID显示 -->
              <p><strong>审核状态:</strong>
                <span v-if="item.reviewStatus === 0">审核中</span>
                <span v-else-if="item.reviewStatus === 1">已通过</span>
                <span v-else-if="item.reviewStatus === 2">已拒绝</span>
                <span v-else>未知状态</span>
              </p>
              <p v-if="item.reviewMessage"><strong>审核信息:</strong> {{ item.reviewMessage }}</p>
            </div>
          </a-list-item>
        </template>
        <!-- 当列表为空时的提示 -->
        <template #emptyText>
          暂无审核通知
        </template>
      </a-list>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { ReloadOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { getUnreadCountUsingGet, listNoticesUsingGet, markAsReadUsingPost } from '@/api/sysNoticeController.ts'

// 响应式数据
const loading = ref<boolean>(false)
const noticeList = ref<API.ReviewNoticeVO[]>([])
const total = ref<number>(0)
const unreadCount = ref<number>(0)

// 分页配置
const current = ref<number>(1)
const pageSize = ref<number>(10)

// 计算属性：分页器配置
const paginationProps = computed(() => ({
  current: current.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showQuickJumper: true,
  pageSizeOptions: ['10', '20', '50'],
  onChange: (page: number, size: number) => {
    current.value = page
    if (size !== pageSize.value) {
      pageSize.value = size
      current.value = 1
    }
    loadNotices()
  },
  onShowSizeChange: (current: number, size: number) => {
    pageSize.value = size
    current.value = 1
    loadNotices()
  },
}))

/**
 * 加载通知列表
 */
const loadNotices = async () => {
  loading.value = true
  try {
    const params: API.listNoticesUsingGETParams = {
      page: current.value,
      size: pageSize.value,
    }
    const res = await listNoticesUsingGet(params)
    if (res.data.code === 0 && res.data.data) {
      noticeList.value = res.data.data.records || []
      total.value = Number(res.data.data.total) || 0
    } else {
      message.error('加载审核通知失败: ' + (res.data.message || '未知错误'))
      noticeList.value = []
      total.value = 0 // 确保失败时也是 number
    }
  } catch (e) {
    console.error('Failed to load review notices:', e)
    message.error('加载审核通知失败，请稍后重试')
    noticeList.value = []
    total.value = 0 // 确保失败时也是 number
  } finally {
    loading.value = false
  }
}

/**
 * 获取未读消息数量
 */
const loadUnreadCount = async () => {
  try {
    const res = await getUnreadCountUsingGet()
    if (res.data.code === 0 && res.data.data !== undefined) {
      unreadCount.value = Number(res.data.data)
    } else {
      console.error('Failed to get unread count:', res.data.message)
      unreadCount.value = 0
    }
  } catch (e) {
    console.error('Failed to get unread count:', e)
    unreadCount.value = 0
  }
}

/**
 * 标记单条通知为已读
 */
const markAsRead = async (id: number) => {
  try {
    const res = await markAsReadUsingPost({ id })
    if (res.data.code === 0 && res.data.data) {
      message.success('已标记为已读')
      const notice = noticeList.value.find(item => item.id === id)
      if (notice) {
        notice.isRead = 1
      }
      loadUnreadCount()
    } else {
      message.error('标记已读失败: ' + (res.data.message || '未知错误'))
    }
  } catch (e) {
    console.error('Failed to mark as read:', e)
    message.error('标记已读失败，请稍后重试')
  }
}

/**
 * 格式化日期时间的辅助函数
 */
function formatDateTime(input?: string) {
  if (!input) return ''
  const d = new Date(input)
  if (isNaN(d.getTime())) return input
  const pad = (n: number) => (n < 10 ? `0${n}` : `${n}`)
  const y = d.getFullYear()
  const m = pad(d.getMonth() + 1)
  const day = pad(d.getDate())
  const hh = pad(d.getHours())
  const mm = pad(d.getMinutes())
  const ss = pad(d.getSeconds())
  return `${y}-${m}-${day} ${hh}:${mm}:${ss}`
}

// 组件挂载时加载第一页数据和未读数
onMounted(() => {
  loadNotices()
  loadUnreadCount()
})
</script>

<style scoped>
#userMessagesPage {
  max-width: 1000px;
  margin: 0 auto;
  padding: 16px;
}

/* 为未读项添加样式 */
.unread-item {
  background-color: #f9f9f9;
}

.notice-content {
  margin-top: 8px;
}

.image-preview {
  margin-bottom: 8px;
}
</style>
