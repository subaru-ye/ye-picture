<template>
  <a-modal
    class="image-cropper"
    v-model:visible="visible"
    title="编辑图片"
    :footer="false"
    @cancel="closeModal"
  >
    <div>
      <vue-cropper
        ref="cropperRef"
        :img="imageUrl"
        :autoCrop="true"
        :fixedBox="false"
        :centerBox="true"
        :canMoveBox="true"
        :info="true"
        outputType="png"
      />
      <div style="margin-bottom: 16px" />
      <!-- 协同编辑操作（仅团队空间显示） -->
      <div class="image-edit-actions" v-if="isTeamSpace">
        <a-space>
          <a-button v-if="editingUser" disabled> {{ editingUser.userName }}正在编辑</a-button>
          <a-button v-if="canEnterEdit" type="primary" ghost @click="enterEdit">进入编辑</a-button>
          <a-button v-if="canExitEdit" danger ghost @click="exitEdit">退出编辑</a-button>
        </a-space>
      </div>
      <!-- 图片操作 -->
      <div class="image-cropper-actions">
        <a-space>
          <a-button @click="rotateLeft" :disabled="!canEdit">向左旋转</a-button>
          <a-button @click="rotateRight" :disabled="!canEdit">向右旋转</a-button>
          <a-button @click="changeScale(1)" :disabled="!canEdit">放大</a-button>
          <a-button @click="changeScale(-1)" :disabled="!canEdit">缩小</a-button>
          <a-button type="primary" :loading="loading" :disabled="!canEdit" @click="handleConfirm">
            确认
          </a-button>
        </a-space>
      </div>
    </div>
  </a-modal>
</template>

<script setup lang="ts">
import { computed, onUnmounted, ref, watch, watchEffect } from 'vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import PictureEditWebSocket from '@/utils/pictureEditWebSocket.ts'
import {
  PICTURE_EDIT_ACTION_ENUM,
  PICTURE_EDIT_MESSAGE_TYPE_ENUM
} from '@/constants/picture.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'

// 定义组件接收的属性类型
interface Props {
  imageUrl?: string
  picture?: API.PictureVO
  spaceId?: number
  space?: API.SpaceVO
  onSuccess?: (newPicture: API.PictureVO) => void
}
const props = defineProps<Props>()

// 编辑器组件引用
const cropperRef = ref()

// 向左旋转（并同步到协同编辑）
const rotateLeft = () => {
  cropperRef.value?.rotateLeft()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT)
}

// 向右旋转（并同步到协同编辑）
const rotateRight = () => {
  cropperRef.value?.rotateRight()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT)
}

// 缩放（并同步到协同编辑）
const changeScale = (num: number) => {
  cropperRef.value?.changeScale(num)
  num > 0
    ? editAction(PICTURE_EDIT_ACTION_ENUM.ZOOM_IN)
    : editAction(PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT)
}

// 弹窗显示状态
const visible = ref(false)

// 打开弹窗（暴露给父组件）
const openModal = () => {
  visible.value = true
}
defineExpose({ openModal })

// 上传加载状态
const loading = ref<boolean>(false)

// 确认裁剪并上传
const handleConfirm = () => {
  if (!cropperRef.value) return
  cropperRef.value.getCropBlob((blob: Blob) => {
    const fileName = (props.picture?.name || 'image') + '.png'
    const file = new File([blob], fileName, { type: blob.type })
    handleUpload({ file })
  })
}

// 图片上传逻辑
const handleUpload = async ({ file }: { file: File }) => {
  loading.value = true
  try {
    const params: API.PictureUploadRequest = props.picture ? { id: props.picture.id } : {}
    params.spaceId = props.spaceId
    const res = await uploadPictureUsingPost(params, {}, file)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      props.onSuccess?.(res.data.data)
      closeModal()
    } else {
      message.error('图片上传失败，' + res.data.message)
    }
  } catch (error: any) {
    message.error('图片上传失败：' + error.message)
  } finally {
    loading.value = false
  }
}

// ========== 实时协同编辑逻辑 ==========
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser
const editingUser = ref<API.UserVO>() // 正在编辑的用户

// 计算属性：是否可进入编辑（无用户正在编辑时）
const canEnterEdit = computed(() => !editingUser.value)
// 计算属性：是否可退出编辑（当前编辑用户是自己时）
const canExitEdit = computed(() => editingUser.value?.id === loginUser.id)
// 计算属性：是否为团队空间
const isTeamSpace = computed(() => props.space?.spaceType === SPACE_TYPE_ENUM.TEAM)
// 计算属性：是否有编辑权限（非团队空间默认可编辑，团队空间需是自己编辑时）
const canEdit = computed(() => {
  if (!isTeamSpace.value) return true
  return editingUser.value?.id === loginUser.id
})

let websocket: PictureEditWebSocket | null

// 初始化 WebSocket 连接（团队空间且弹窗可见且有图片ID时触发）
const initWebsocket = () => {
  if (!isTeamSpace.value || !visible.value || !props.picture?.id) return
  if (websocket) {
    websocket.disconnect()
    websocket = null
  }
  try {
    websocket = new PictureEditWebSocket(props.picture.id)
    websocket.connect()

    // 监听通知消息
    websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.INFO, (msg) => {
      console.log('通知消息：', msg)
      message.info(msg.message)
    })

    // 监听错误消息
    websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ERROR, (msg) => {
      console.log('错误消息：', msg)
      message.error(msg.message)
    })

    // 监听进入编辑状态
    websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT, (msg) => {
      console.log('进入编辑消息：', msg)
      message.info(msg.message)
      editingUser.value = msg.user
    })

    // 监听编辑操作同步
    websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION, (msg) => {
      console.log('编辑操作消息：', msg)
      if (cropperRef.value) {
        switch (msg.editAction) {
          case PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT:
            cropperRef.value.rotateLeft()
            break
          case PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT:
            cropperRef.value.rotateRight()
            break
          case PICTURE_EDIT_ACTION_ENUM.ZOOM_IN:
            cropperRef.value.changeScale(1)
            break
          case PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT:
            cropperRef.value.changeScale(-1)
            break
        }
      }
    })

    // 监听退出编辑状态
    websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT, (msg) => {
      console.log('退出编辑消息：', msg)
      message.info(msg.message)
      editingUser.value = undefined
    })
  } catch (e: any) {
    console.error('WebSocket 初始化失败：', e)
    message.error('实时编辑连接失败，不影响基础编辑功能')
  }
}

// 监听弹窗状态和空间类型变化，初始化WebSocket
watchEffect(() => {
  if (isTeamSpace.value && visible.value && props.picture?.id) {
    initWebsocket()
  } else {
    if (websocket) {
      websocket.disconnect()
      websocket = null
    }
  }
})

// 监听图片ID变化，重新初始化WebSocket
watch(
  () => props.picture?.id,
  (newId, oldId) => {
    if (newId !== oldId && isTeamSpace.value && visible.value) {
      initWebsocket()
    }
  }
)

// 组件卸载时清理资源
onUnmounted(() => {
  if (websocket) {
    websocket.disconnect()
    websocket = null
  }
  editingUser.value = undefined
})

// 关闭弹窗并清理资源
const closeModal = () => {
  visible.value = false
  if (websocket) {
    websocket.disconnect()
    websocket = null
  }
  editingUser.value = undefined
  cropperRef.value?.reset() // 重置裁剪组件状态
}

// 进入编辑状态（发送WebSocket消息）
const enterEdit = () => {
  if (websocket) {
    try {
      websocket.sendMessage({
        type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT,
      })
    } catch (e: any) {
      console.error('进入编辑消息发送失败：', e)
      message.error('操作失败：' + e.message)
    }
  }
}

// 退出编辑状态（发送WebSocket消息）
const exitEdit = () => {
  if (websocket) {
    try {
      websocket.sendMessage({
        type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT,
      })
    } catch (e: any) {
      console.error('退出编辑消息发送失败：', e)
      message.error('操作失败：' + e.message)
    }
  }
}

// 发送编辑操作同步消息（仅自己可编辑时触发）
const editAction = (action: string) => {
  if (websocket && canEdit.value) {
    try {
      websocket.sendMessage({
        type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION,
        editAction: action,
      })
    } catch (e: any) {
      console.error('编辑操作消息发送失败：', e)
      message.error('操作失败：' + e.message)
    }
  }
}
</script>

<style scoped>
.image-cropper {
  text-align: center;
}

.image-cropper .vue-cropper {
  height: 400px;
}
</style>
