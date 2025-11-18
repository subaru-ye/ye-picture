<template>
  <div id="pictureManagePage">
    <a-flex justify="space-between">
      <h2>图片管理</h2>
      <a-space>
        <a-button type="primary" @click="goToAddPicture">+ 创建图片</a-button>
        <a-button type="primary" @click="goToBatchAddPicture" ghost>+ 批量创建图片</a-button>
      </a-space>
    </a-flex>
    <div style="margin-bottom: 16px" />
    <!-- 搜索表单：支持关键词、类型、标签、审核状态筛选 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="关键词" name="searchText">
        <a-input
          v-model:value="searchParams.searchText"
          placeholder="从名称和简介搜索"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="类型" name="category">
        <a-input v-model:value="searchParams.category" placeholder="请输入类型" allow-clear />
      </a-form-item>
      <a-form-item label="标签" name="tags">
        <a-select
          v-model:value="searchParams.tags"
          mode="tags"
          placeholder="请输入标签"
          style="min-width: 180px"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="审核状态" name="reviewStatus">
        <a-select
          v-model:value="searchParams.reviewStatus"
          :options="PIC_REVIEW_STATUS_OPTIONS"
          placeholder="请输入审核状态"
          style="min-width: 180px"
          allow-clear
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>

    <div style="margin-bottom: 16px"></div>
    <a-table
      :columns="columns"
      :data-source="dataList"
      :pagination="pagination"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'url'">
          <a-image :src="record.url" :width="120" />
        </template>
        <!-- 标签列：解析 JSON 字符串并展示标签列表 -->
        <template v-if="column.dataIndex === 'tags'">
          <a-space wrap>
            <a-tag v-for="tag in JSON.parse(record.tags || '[]')" :key="tag">{{ tag }}</a-tag>
          </a-space>
        </template>
        <!-- 图片信息列：展示图片的格式、尺寸、宽高比、文件大小等信息 -->
        <template v-if="column.dataIndex === 'picInfo'">
          <a-space direction="vertical" size="small" style="max-width: 280px">
            <div>格式：{{ record.picFormat || '-' }}</div>
            <div>尺寸：{{ record.picWidth || '-' }} × {{ record.picHeight || '-' }}</div>
            <div>宽高比：{{ record.picScale || '-' }}</div>
            <div>大小：{{ record.picSize ? (record.picSize / 1024).toFixed(2) + ' KB' : '-' }}</div>
          </a-space>
        </template>
        <!-- 审核信息列：展示审核状态、审核信息、审核人、审核时间 -->
        <template v-if="column.dataIndex === 'reviewMessage'">
          <a-space direction="vertical" size="small" style="max-width: 360px">
            <div>
              审核状态：
              <a-tag
                :color="
                  record.reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS
                    ? 'green'
                    : record.reviewStatus === PIC_REVIEW_STATUS_ENUM.REJECT
                      ? 'red'
                      : 'blue'
                "
              >
                {{ PIC_REVIEW_STATUS_MAP[record.reviewStatus as keyof typeof PIC_REVIEW_STATUS_MAP] || '-' }}
              </a-tag>
            </div>
            <div>
              审核信息：
              <span style="white-space: pre-wrap; word-break: break-word">{{
                record.reviewMessage || '-'
              }}</span>
            </div>
            <div>审核人：{{ record.reviewerId || '-' }}</div>
            <div v-if="record.reviewTime">
              审核时间：{{ dayjs(record.reviewTime).format('YYYY-MM-DD HH:mm:ss') }}
            </div>
          </a-space>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.dataIndex === 'editTime'">
          {{ dayjs(record.editTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space wrap>
            <a-button
              v-if="record.reviewStatus !== PIC_REVIEW_STATUS_ENUM.PASS"
              type="link"
              @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.PASS)"
            >
              通过
            </a-button>
            <a-button
              v-if="record.reviewStatus !== PIC_REVIEW_STATUS_ENUM.REJECT"
              type="link"
              danger
              @click="handleReview(record, PIC_REVIEW_STATUS_ENUM.REJECT)"
            >
              拒绝
            </a-button>
            <a-button type="link" @click="goToEditPicture(record.id!)">编辑</a-button>
            <a-button type="link" danger @click="doDelete(record.id!)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 审核弹窗 -->
    <a-modal
      v-model:visible="reviewModalVisible"
      :title="reviewModalTitle"
      @ok="handleReviewSubmit"
      @cancel="handleReviewCancel"
      :ok-button-props="{ danger: currentReviewStatus === PIC_REVIEW_STATUS_ENUM.REJECT }"
    >
      <a-form ref="reviewFormRef" :model="reviewForm" layout="vertical">
        <a-form-item label="审核状态">
          <a-radio-group v-model:value="reviewForm.reviewStatus" :disabled="true">
            <a-radio :value="PIC_REVIEW_STATUS_ENUM.PASS">通过</a-radio>
            <a-radio :value="PIC_REVIEW_STATUS_ENUM.REJECT">拒绝</a-radio>
          </a-radio-group>
        </a-form-item>
        <a-form-item
          label="审核原因"
          name="reviewMessage"
          :rules="[
            {
              required: currentReviewStatus === PIC_REVIEW_STATUS_ENUM.REJECT,
              message: '拒绝审核时必须填写拒绝原因',
              trigger: 'blur',
            },
          ]"
        >
          <a-textarea
            v-model:value="reviewForm.reviewMessage"
            :placeholder="
              currentReviewStatus === PIC_REVIEW_STATUS_ENUM.PASS
                ? '请输入审核通过原因（可选）'
                : '请输入拒绝原因（必填）'
            "
            :rows="4"
            :maxlength="500"
            show-count
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
<script lang="ts" setup>
/**
 * 图片管理页面
 * 提供图片的查询、审核、编辑、删除等功能
 * 支持多条件搜索和分页展示
 */
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  deletePictureUsingPost,
  doPictureReviewUsingPost,
  listPictureByPageUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  PIC_REVIEW_STATUS_ENUM,
  PIC_REVIEW_STATUS_MAP,
  PIC_REVIEW_STATUS_OPTIONS,
} from '@/constants/picture.ts'

/** 路由实例 */
const router = useRouter()

/**
 * 跳转到创建图片页面
 */
const goToAddPicture = () => {
  router.push('/add_picture')
}

/**
 * 跳转到批量创建图片页面
 */
const goToBatchAddPicture = () => {
  router.push('/add_picture/batch')
}

/**
 * 跳转到编辑图片页面
 * @param id - 图片ID
 */
const goToEditPicture = (id: number) => {
  router.push(`/add_picture?id=${id}`)
}

/** 表格列配置 */
const columns = [
  {
    title: 'id',
    dataIndex: 'id',
    width: 80,
  },
  {
    title: '图片',
    dataIndex: 'url',
  },
  {
    title: '名称',
    dataIndex: 'name',
  },
  {
    title: '简介',
    dataIndex: 'introduction',
    ellipsis: true,
  },
  {
    title: '类型',
    dataIndex: 'category',
  },
  {
    title: '标签',
    dataIndex: 'tags',
  },
  {
    title: '图片信息',
    dataIndex: 'picInfo',
    width: 300,
  },
  {
    title: '用户 id',
    dataIndex: 'userId',
    width: 80,
  },
  {
    title: '审核信息',
    dataIndex: 'reviewMessage',
    width: 380,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '编辑时间',
    dataIndex: 'editTime',
  },
  {
    title: '操作',
    key: 'action',
  },
]

/** 图片数据列表 */
const dataList = ref<API.Picture[]>([])
/** 数据总数 */
const total = ref(0)

/** 搜索条件参数 */
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'desc',
})

/** 分页配置（计算属性） */
const pagination = computed(() => {
  return {
    current: searchParams.current ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

/**
 * 处理表格变化事件（分页、排序等）
 * @param page - 分页信息对象，包含 current 和 pageSize
 */
const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.current = page.current
  searchParams.pageSize = page.pageSize
  void fetchData()
}

/**
 * 获取图片列表数据
 * 从后端接口获取分页数据并更新列表
 */
const fetchData = async () => {
  const res = await listPictureByPageUsingPost({
    ...searchParams,
    nullSpaceId: true, // 查询所有空间的图片
  })
  if (res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

/** 初始化完成标志，用于避免组件初始化时触发 watch 监听 */
const isInitialized = ref(false)

/** 审核弹窗显示状态 */
const reviewModalVisible = ref(false)
/** 当前审核的图片记录 */
const currentReviewRecord = ref<API.Picture | null>(null)
/** 当前审核状态 */
const currentReviewStatus = ref<number>(PIC_REVIEW_STATUS_ENUM.PASS)
/** 审核表单引用 */
const reviewFormRef = ref()
/** 审核表单数据 */
const reviewForm = reactive<{
  reviewStatus: number
  reviewMessage: string
}>({
  reviewStatus: PIC_REVIEW_STATUS_ENUM.PASS,
  reviewMessage: '',
})

/**
 * 审核弹窗标题
 */
const reviewModalTitle = computed(() => {
  if (currentReviewStatus.value === PIC_REVIEW_STATUS_ENUM.PASS) {
    return '审核通过'
  } else {
    return '审核拒绝'
  }
})

/**
 * 组件挂载时的初始化操作
 * 加载初始数据并启用查询条件监听
 */
onMounted(async () => {
  await fetchData()
  // 等待下一个 tick，确保组件完全挂载后再开始监听
  await nextTick()
  isInitialized.value = true
})

/**
 * 监听查询条件变化
 * 当用户清空搜索条件时，自动重新查询并展示所有数据
 */
watch(
  () => [
    searchParams.searchText,
    searchParams.category,
    searchParams.tags,
    searchParams.reviewStatus,
  ],
  (newValues, oldValues) => {
    // 初始化完成前不触发监听，避免首次加载时误触发
    if (!isInitialized.value) {
      return
    }

    // 检查是否有字段被清空（从有值变为空值）
    const oldTags = oldValues?.[2] as string[] | undefined
    const newTags = newValues[2] as string[] | undefined
    const hasCleared =
      (oldValues?.[0] && !newValues[0]) || // 关键词被清空
      (oldValues?.[1] && !newValues[1]) || // 类型被清空
      (Array.isArray(oldTags) && oldTags.length > 0 && (!Array.isArray(newTags) || newTags.length === 0)) || // 标签被清空
      (oldValues?.[3] !== undefined && oldValues?.[3] !== null && (newValues[3] === undefined || newValues[3] === null)) // 审核状态被清空

    // 如果有字段被清空，自动触发查询以展示所有数据
    if (hasCleared) {
      // 重置页码到第一页
      searchParams.current = 1
      void fetchData()
    }
  },
  { deep: true }
)

/**
 * 执行搜索操作
 * 重置页码并重新获取数据
 */
const doSearch = () => {
  // 重置页码到第一页
  searchParams.current = 1
  void fetchData()
}

/**
 * 删除图片
 * @param id - 要删除的图片ID
 */
const doDelete = async (id: number) => {
  if (!id) {
    return
  }
  const res = await deletePictureUsingPost({ id: id as number })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 删除成功后刷新列表数据
    void fetchData()
  } else {
    message.error('删除失败')
  }
}

/**
 * 打开审核弹窗
 * @param record - 图片记录对象
 * @param reviewStatus - 审核状态（通过或拒绝）
 */
const handleReview = (record: API.Picture, reviewStatus: number) => {
  currentReviewRecord.value = record
  currentReviewStatus.value = reviewStatus
  reviewForm.reviewStatus = reviewStatus
  // 根据审核状态设置默认审核原因
  if (reviewStatus === PIC_REVIEW_STATUS_ENUM.PASS) {
    reviewForm.reviewMessage = '审核通过'
  } else {
    reviewForm.reviewMessage = ''
  }
  reviewModalVisible.value = true
}

/**
 * 提交审核
 */
const handleReviewSubmit = async () => {
  if (!currentReviewRecord.value) {
    return
  }

  // 表单验证
  try {
    await reviewFormRef.value?.validate()
  } catch (error) {
    return
  }

  // 验证：拒绝审核时必须填写原因
  if (
    currentReviewStatus.value === PIC_REVIEW_STATUS_ENUM.REJECT &&
    !reviewForm.reviewMessage?.trim()
  ) {
    message.warning('拒绝审核时必须填写拒绝原因')
    return
  }

  try {
    const res = await doPictureReviewUsingPost({
      id: currentReviewRecord.value.id,
      reviewStatus: currentReviewStatus.value,
      reviewMessage: reviewForm.reviewMessage?.trim() || '审核通过',
    })

    if (res.data.code === 0) {
      message.success('审核操作成功')
      // 关闭弹窗
      reviewModalVisible.value = false
      // 重置表单
      reviewForm.reviewMessage = ''
      currentReviewRecord.value = null
      // 刷新列表数据
      void fetchData()
    } else {
      message.error('审核操作失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('审核操作失败：' + error.message)
  }
}

/**
 * 取消审核
 */
const handleReviewCancel = () => {
  reviewModalVisible.value = false
  reviewForm.reviewMessage = ''
  currentReviewRecord.value = null
  // 清除表单验证状态
  reviewFormRef.value?.resetFields()
}
</script>
