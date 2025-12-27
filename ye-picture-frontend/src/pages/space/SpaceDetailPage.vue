<template>
  <div id="spaceDetailPage">
    <!-- 空间信息 -->
    <a-flex justify="space-between">
      <h2>{{ space.spaceName }}（{{ SPACE_TYPE_MAP[space.spaceType] }}）</h2>
      <a-space size="middle">
        <a-button
          v-if="canUploadPicture"
          type="primary"
          :href="`/add_picture?spaceId=${id}`"
        >
          + 创建图片
        </a-button>

        <a-button
          v-if="canManageSpaceUser && space.spaceType === SPACE_TYPE_ENUM.TEAM"
          type="primary"
          ghost
          :icon="h(TeamOutlined)"
          :href="`/spaceUserManage/${id}`"
        >
          成员管理
        </a-button>

        <a-button
          type="primary"
          ghost
          :icon="h(BarChartOutlined)"
          @click="goToAnalyze"
        >
          空间分析
        </a-button>

        <a-button :icon="h(EditOutlined)" @click="doBatchEdit"> 批量编辑</a-button>
        <a-tooltip
          :title="`占用空间 ${formatSize(space.totalSize)} / ${formatSize(space.maxSize)}`"
        >
          <a-progress
            type="circle"
            :percent="((space.totalSize * 100) / space.maxSize).toFixed(1)"
            :size="42"
          />
        </a-tooltip>
      </a-space>
    </a-flex>
    <!-- 搜索表单 -->
    <PictureSearchForm :onSearch="onSearch" />

    <!-- 按颜色搜索 -->
    <a-form-item label="按颜色搜索" style="margin-top: 16px">
      <a-space size="middle">
        <color-picker format="hex" @pureColorChange="onColorChange" />
        <a-button @click="clearColorFilter">清除颜色</a-button>
      </a-space>
    </a-form-item>

    <!-- 图片列表 -->
    <PictureList
      :dataList="dataList"
      :loading="loading"
      :onReload="fetchData"
      showOp
      :canEdit="canEditPicture"
      :canDelete="canDeletePicture"
    />
    <a-pagination
      style="text-align: right"
      v-model:current="searchParams.current"
      v-model:pageSize="searchParams.pageSize"
      :total="total"
      :show-total="() => `图片总数 ${total} / ${space.maxCount}`"
      @change="onPageChange"
    />
  </div>
  <BatchEditPictureModal
    ref="batchEditPictureModalRef"
    :spaceId="id"
    :pictureList="dataList"
    :onSuccess="onBatchEditPictureSuccess"
  />
</template>
<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import { formatSize } from '@/utils'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import {
  listPictureVoByPageUsingPost,
  searchPictureByColorUsingPost,
} from '@/api/pictureController.ts'
import { useRouter } from 'vue-router'
import PictureList from '@/components/picture/PictureList.vue'
import PictureSearchForm from '@/components/picture/PictureSearchForm.vue'
import { ColorPicker } from 'vue3-colorpicker'
import 'vue3-colorpicker/style.css'
import BatchEditPictureModal from '@/components/picture/BatchEditPictureModal.vue'
import { EditOutlined, BarChartOutlined, TeamOutlined } from '@ant-design/icons-vue'
import { SPACE_PERMISSION_ENUM, SPACE_TYPE_ENUM, SPACE_TYPE_MAP } from '@/constants/space.ts'

// 新增：初始化路由实例
const router = useRouter()

const props = defineProps<{
  id: string | number
}>()
const space = ref<API.SpaceVO>({})
// SpaceDetailPage.vue 中跳转空间分析的逻辑
const goToAnalyze = () => {
  router.push({
    path: '/space_analyze',
    query: {
      spaceId: props.id,
      from: 'personal' // 标记为从个人空间进入
    }
  })
}
// 获取空间详情
const fetchSpaceDetail = async () => {
  try {
    const res = await getSpaceVoByIdUsingGet({
      id: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      space.value = res.data.data
    } else {
      message.error('获取空间详情失败，' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取空间详情失败：' + e.message)
  }
}

onMounted(() => {
  fetchSpaceDetail()
})

// 数据
const dataList = ref([])
const total = ref(0)
const loading = ref(true)

// 通用权限检查函数
function createPermissionChecker(permission: string) {
  return computed(() => {
    return (space.value.permissionList ?? []).includes(permission)
  })
}

// 定义权限检查
const canManageSpaceUser = createPermissionChecker(SPACE_PERMISSION_ENUM.SPACE_USER_MANAGE)
const canUploadPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_UPLOAD)
const canEditPicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_EDIT)
const canDeletePicture = createPermissionChecker(SPACE_PERMISSION_ENUM.PICTURE_DELETE)

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})
// 搜索条件
const searchParams = ref<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 分页参数
const onPageChange = (page, pageSize) => {
  searchParams.value.current = page
  searchParams.value.pageSize = pageSize
  fetchData()
}

// 搜索
const onSearch = (newSearchParams: API.PictureQueryRequest) => {
  searchParams.value = {
    ...searchParams.value,
    ...newSearchParams,
    current: 1,
  }
  fetchData()
}

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const params = {
    spaceId: props.id,
    ...searchParams.value,
  }
  const res = await listPictureVoByPageUsingPost(params)
  if (res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
  loading.value = false
}
/**
 * 处理颜色选择变化
 * 将前端颜色选择器返回的 #RRGGBB 格式转换为后端需要的 0xRRGGBB 格式
 * @param color - 颜色选择器返回的颜色值（#RRGGBB 格式）
 */
const onColorChange = async (color: string) => {
  // 将 #RRGGBB 格式转换为 0xRRGGBB 格式（后端 Color.decode 需要）
  let picColor = color
  if (color.startsWith('#')) {
    picColor = '0x' + color.slice(1)
  } else if (!color.startsWith('0x') && !color.startsWith('0X')) {
    // 如果没有前缀，添加 0x 前缀
    picColor = '0x' + color
  }

  try {
    const res = await searchPictureByColorUsingPost({
      picColor: picColor,
      spaceId: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      const data = res.data.data ?? []
      dataList.value = data
      total.value = data.length
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('颜色搜索失败：' + (error.message || '未知错误'))
  }
}
// 清除颜色筛选，恢复普通分页数据
const clearColorFilter = () => {
  // 重置回普通搜索模式（使用当前 searchParams）
  fetchData()
}
// 分享弹窗引用
const batchEditPictureModalRef = ref()

// 批量编辑成功后，刷新数据
const onBatchEditPictureSuccess = () => {
  fetchData()
}
// 打开批量编辑弹窗
const doBatchEdit = () => {
  if (batchEditPictureModalRef.value) {
    batchEditPictureModalRef.value.openModal()
  }
}

// 空间id改变时,重新获取数据
watch(
  () => props.id,
  (newSpaceId) => {
    fetchSpaceDetail()
    fetchData()
  },
)
</script>

<style scoped>
#spaceDetailPage {
  margin-bottom: 16px;
}
</style>
