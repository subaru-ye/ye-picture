<template>
  <!--
    分享图片弹窗组件：基于Ant Design Vue的Modal组件封装
    - v-model:visible：双向绑定弹窗显示/隐藏状态（控制弹窗开关）
    - title：弹窗标题，由父组件传入或使用默认值
    - :footer="false"：隐藏Modal默认的底部按钮（取消/确认），自定义关闭逻辑
    - @cancel：弹窗右上角关闭按钮/点击遮罩层时触发，调用closeModal关闭弹窗
  -->
  <a-modal
    v-model:visible="visible"
    :title="props.title"
    :footer="false"
    @cancel="closeModal"
  >
    <!-- 复制链接区域：展示可复制的分享链接 -->
    <h4>复制分享链接</h4>
    <!--
      a-typography-link：Ant Design Vue的链接组件，带copyable属性可实现一键复制
      {{ props.link }}：展示父组件传入的分享链接（图片URL或下载页地址）
    -->
    <a-typography-link copyable>
      {{ props.link }}
    </a-typography-link>

    <!-- 空白间隔：用div设置margin-bottom实现元素间垂直间距，替代br标签更灵活 -->
    <div style="margin-bottom: 16px" />

    <!-- 扫码查看区域：生成分享链接的二维码 -->
    <h4>手机扫码查看</h4>
    <!--
      a-qrcode：Ant Design Vue的二维码组件
      :value="props.link"：将分享链接作为二维码内容，手机扫码后可跳转/访问链接
    -->
    <a-qrcode :value="props.link" />
  </a-modal>
</template>

<script setup lang="ts">
import { defineProps, ref, withDefaults, defineExpose } from 'vue'

/**
 * 定义组件接收的Props类型接口（TypeScript类型约束）
 * - 明确父组件可传递的参数及类型，增强代码可读性和类型校验
 */
interface Props {
  // 弹窗标题（可选，父组件可自定义，默认值为"分享"）
  title: string
  // 分享链接（核心参数，需父组件传入有效的图片URL或下载地址，默认值为示例链接）
  link: string
}

/**
 * 配置Props默认值并注册组件属性
 * - withDefaults：为defineProps定义的属性设置默认值，避免父组件未传参时出现undefined
 * - 函数式默认值（() => 值）：确保复杂类型（如对象/字符串）每次使用时都是新实例（此处虽为字符串，保持规范）
 */
const props = withDefaults(defineProps<Props>(), {
  // 标题默认值：未传title时显示"分享"
  title: () => '分享',
  // 链接默认值：未传link时显示示例链接（实际使用需父组件传入真实链接）
  link: () => 'https://laoyujianli.com/share/yupi',
})

// 弹窗显示状态：用ref创建响应式变量，控制弹窗的显示（true）/隐藏（false）
// 初始值为false，默认弹窗隐藏
const visible = ref(false)

/**
 * 打开弹窗方法：修改visible为true，显示弹窗
 * - 暴露给父组件调用，父组件通过ref获取组件实例后触发
 */
const openModal = () => {
  visible.value = true
}

/**
 * 关闭弹窗方法：修改visible为false，隐藏弹窗
 * - 触发场景：弹窗右上角关闭按钮、点击遮罩层（@cancel事件绑定）
 */
const closeModal = () => {
  visible.value = false
}

/**
 * 暴露组件内部方法给父组件
 * - 由于<script setup>语法下组件内部方法默认私有，需通过defineExpose显式暴露
 * - 父组件可通过ref="modalRef" + modalRef.value.openModal()调用打开弹窗
 */
defineExpose({
  openModal, // 仅暴露openModal，closeModal无需暴露（通过@cancel和弹窗自身关闭逻辑触发）
});

</script>
