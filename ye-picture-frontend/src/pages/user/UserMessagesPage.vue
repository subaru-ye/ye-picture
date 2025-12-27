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
              <!-- 可选：显示一个表示图片审核的图标 -->
              <!-- <template #avatar>
                <a-avatar icon="picture" />
              </template> -->
            </a-list-item-meta>
            <!-- 显示审核结果信息和图片 -->
            <div class="notice-content">
              <!-- 图片展示区域，使用 a-avatar 显示图片 -->
              <div v-if="item.pictureUrl" class="image-preview"> <!-- 修改：检查 pictureUrl -->
                <!-- 直接绑定 item.pictureUrl 到 a-avatar 的 src 属性 -->
                <a-avatar
                  :size="64"
                  :src="item.pictureUrl"
                :alt="item.pictureTitle || '审核图片'"
                shape="square"
                style="margin-bottom: 8px;"
                >
                <!-- 可选：如果图片加载失败，a-avatar 会显示默认图标或首字母 -->
                <!-- <template #icon><PictureOutlined /></template> -->
                </a-avatar>
              </div>
              <!-- 如果 pictureUrl 不存在但 pictureId 存在，可以尝试构造 URL (可选逻辑) -->
              <div v-else-if="item.pictureId && !item.pictureUrl" class="image-preview">
                <a-avatar
                  :size="64"
                  :src="`/api/file/get/${item.pictureId}`"
                :alt="item.pictureTitle || '审核图片'"
                shape="square"
                style="margin-bottom: 8px;"
                >
                <!-- 可选：如果图片加载失败，a-avatar 会显示默认图标或首字母 -->
                <!-- <template #icon><PictureOutlined /></template> -->
                </a-avatar>
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
const noticeList = ref<API.ReviewNoticeVO[]>([]) // 确保 ReviewNoticeVO 类型定义中包含 pictureUrl 字段
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
      // --- 修改这里 ---
      // 确保 res.data.data.total 被转换为数字类型
      total.value = Number(res.data.data.total) || 0 // 使用 Number() 或 parseInt()
      // 或者如果 res.data.data.total 可能是 undefined 或 null，可以这样写：
      // total.value = res.data.data.total !== undefined && res.data.data.total !== null ? Number(res.data.data.total) : 0;
      // ---
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
  /* word-break: break-word; */
}

.image-preview {
  margin-bottom: 8px;
}
</style>
