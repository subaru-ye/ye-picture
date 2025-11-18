<template>
  <div id="userManagePage">
    <!-- 搜索表单：用于筛选用户列表（账号、用户名模糊查询） -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="账号">
        <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" allow-clear />
      </a-form-item>
      <a-form-item label="用户名">
        <a-input v-model:value="searchParams.userName" placeholder="输入用户名" allow-clear />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <div style="margin-bottom: 16px"></div>
    <!-- 用户列表表格：展示用户信息，支持分页、编辑/删除操作 -->
    <a-table
      :columns="columns"
      :data-source="dataList"
      :pagination="pagination"
      @change="doTableChange"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <!-- 头像单元格：图片预览（宽度60px） -->
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-image :src="record.userAvatar" :width="60" />
        </template>
        <!-- 用户角色单元格：标签区分管理员/普通用户 -->
        <template v-else-if="column.dataIndex === 'userRole'">
          <div v-if="record.userRole === 'admin'">
            <a-tag color="green">管理员</a-tag>
          </div>
          <div v-else>
            <a-tag color="blue">普通用户</a-tag>
          </div>
        </template>
        <!-- 创建时间单元格：格式化日期时间（YYYY-MM-DD HH:mm:ss） -->
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <!-- 操作列：编辑、删除按钮 -->
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button @click="openEdit(record)">编辑</a-button>
            <a-button danger @click="doDelete(record.id)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 编辑用户弹窗：修改用户头像、用户名、简介、角色 -->
    <a-modal v-model:open="editOpen" title="编辑用户" :confirm-loading="editSubmitting" @ok="submitEdit" @cancel="closeEdit">
      <a-form :model="editForm" :label-col="{ span: 5 }" :wrapper-col="{ span: 19 }" @finish.prevent>
        <!-- 头像上传：预览已有头像 + 上传新头像 -->
        <a-form-item label="头像">
          <div style="display:flex; align-items:center; gap:8px;">
            <a-avatar :size="56" :src="editForm.userAvatar" />
            <PictureUpload :picture="avatarAsPicture" :on-success="onAvatarUploaded" />
          </div>
          <div v-if="editForm.userAvatar" style="color:#999; font-size:12px; margin-top:8px; word-break:break-all;">
            {{ editForm.userAvatar }}
          </div>
        </a-form-item>
        <!-- 用户名：必填校验 -->
        <a-form-item label="用户名" name="userName" :rules="[{ required: true, message: '请输入用户名' }]">
          <a-input v-model:value="editForm.userName" placeholder="请输入用户名" allow-clear />
        </a-form-item>
        <!-- 用户简介：可选输入（多行文本） -->
        <a-form-item label="简介" name="userProfile">
          <a-textarea v-model:value="editForm.userProfile" :rows="3" placeholder="请输入简介" allow-clear />
        </a-form-item>
        <!-- 用户角色：下拉选择（普通用户/管理员） -->
        <a-form-item label="角色" name="userRole">
          <a-select v-model:value="editForm.userRole" style="width: 160px">
            <a-select-option value="user">普通用户</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { deleteUserUsingPost, listUserVoByPageUsingPost, updateUserUsingPost } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import PictureUpload from '@/components/picture/PictureUpload.vue'

/**
 * 表格列配置：定义用户列表展示的字段和格式
 */
const columns = [
  { title: 'id', dataIndex: 'id' },
  { title: '账号', dataIndex: 'userAccount' },
  { title: '用户名', dataIndex: 'userName' },
  { title: '头像', dataIndex: 'userAvatar' },
  { title: '简介', dataIndex: 'userProfile' },
  { title: '用户角色', dataIndex: 'userRole' },
  { title: '创建时间', dataIndex: 'createTime' },
  { title: '操作', key: 'action' },
]

/**
 * 数据存储与分页配置
 */
// 用户列表数据
const dataList = ref<API.UserVO[]>([])
// 总数据条数（用于分页）
const total = ref(0)

// 搜索参数：包含分页信息和查询条件
const searchParams = reactive<API.UserQueryRequest>({
  current: 1,
  pageSize: 10,
  userAccount: '',
  userName: '',
})

// 监听搜索参数清空事件：当账号或用户名被清空时，重新拉取全量数据
watch([() => searchParams.userAccount, () => searchParams.userName], ([newAccount, newName]) => {
  if (!newAccount && !newName) {
    searchParams.current = 1
    fetchData()
  }
})

// 分页配置（响应式计算，关联搜索参数）
const pagination = computed(() => {
  return {
    current: searchParams.current ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true, // 支持切换每页条数
    showTotal: ({ total }: { total: number }) => `共 ${total} 条`, // 显示总条数（明确total类型为number）
  }
})

/**
 * 表格交互逻辑
 */
// 分页/每页条数变化时触发：更新参数并重新请求数据
const doTableChange = (page: any) => {
  searchParams.current = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

/**
 * 数据请求逻辑
 */
// 拉取用户列表数据（支持搜索条件）
const fetchData = async () => {
  const res = await listUserVoByPageUsingPost({
    ...searchParams,
    // 若搜索参数为空，不传查询条件以获取全量数据
    userAccount: searchParams.userAccount || undefined,
    userName: searchParams.userName || undefined,
  })
  if (res.data.data) {
    dataList.value = res.data.data.records ?? [] // 列表数据
    total.value = res.data.data.total ?? 0 // 总条数
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

// 页面加载时初始化数据
onMounted(() => {
  fetchData()
})

// 搜索按钮触发：重置页码为1，重新请求数据
const doSearch = async () => {
  searchParams.current = 1
  await fetchData()
}

/**
 * 删除用户逻辑
 */
const doDelete = async (id: number) => {
  if (!id) return
  const res = await deleteUserUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    await fetchData() // 删除后刷新列表
  } else {
    message.error('删除失败')
  }
}

/**
 * 编辑用户逻辑
 */
// 编辑弹窗是否显示
const editOpen = ref(false)
// 编辑提交加载状态
const editSubmitting = ref(false)
// 编辑表单数据（关联用户更新请求类型）
const editForm = reactive<API.UserUpdateRequest & { id?: number }>({})

// 打开编辑弹窗：回显选中用户的信息
const openEdit = (record: API.UserVO) => {
  editForm.id = record.id
  editForm.userAvatar = record.userAvatar
  editForm.userName = record.userName
  editForm.userProfile = record.userProfile
  editForm.userRole = record.userRole
  editOpen.value = true
}

// 关闭编辑弹窗：重置表单状态
const closeEdit = () => {
  editOpen.value = false
}

// 头像上传关联：将头像URL转为PictureVO格式（适配PictureUpload组件）
const avatarAsPicture = computed<API.PictureVO | undefined>(() => {
  if (!editForm.userAvatar) return undefined
  return { url: editForm.userAvatar }
})

// 头像上传成功回调：更新表单中的头像URL
const onAvatarUploaded = (pic: API.PictureVO) => {
  editForm.userAvatar = pic.url || pic.thumbnailUrl
  message.success('头像已更新，保存后生效')
}

// 提交编辑：调用更新接口，保存用户信息
const submitEdit = async () => {
  if (!editForm.id) {
    message.error('缺少用户ID')
    return
  }
  editSubmitting.value = true
  try {
    const res = await updateUserUsingPost({
      id: editForm.id,
      userAvatar: editForm.userAvatar,
      userName: editForm.userName,
      userProfile: editForm.userProfile,
      userRole: editForm.userRole,
    })
    if (res.data.code === 0 && res.data.data) {
      message.success('保存成功')
      closeEdit() // 关闭弹窗
      await fetchData() // 刷新列表
    } else {
      message.error('保存失败，' + res.data.message)
    }
  } catch (e) {
    message.error('保存失败，请稍后重试')
  } finally {
    editSubmitting.value = false // 无论成功失败，关闭加载状态
  }
}
</script>
