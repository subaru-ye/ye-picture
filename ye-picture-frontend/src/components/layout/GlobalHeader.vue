<template>
  <div id="globalHeader">
    <!-- 头部布局：使用Ant Design Row/Col实现三列布局 -->
    <a-row :wrap="false">
      <!-- 左侧：Logo与系统名称区域 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="title-bar">
            <img class="logo" :src="logoUrl" alt="logo" />
            <div class="title">Tonight图库</div>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单区域 -->
      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="current"
          mode="horizontal"
          :items="items"
          @click="doMenuClick"
        />
      </a-col>
      <!-- 右侧：用户登录状态区域 -->
      <a-col flex="120px">
        <div class="user-login-status">
          <!-- 已登录状态：显示用户头像、名称及下拉菜单 -->
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown>
              <ASpace>
                <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                {{ loginUserStore.loginUser.userName ?? '无名' }}
              </ASpace>
              <template #overlay>
                <a-menu>
                  <!-- 个人中心选项 -->
                  <a-menu-item>
                    <router-link to="/user/center">
                      <UserOutlined />
                      个人中心
                    </router-link>
                  </a-menu-item>
                  <!-- 我的消息选项 -->
                  <a-menu-item>
                    <router-link to="/user/messages">
                      <MailOutlined />
                      我的消息
                    </router-link>
                  </a-menu-item>
                  <!-- 我的空间选项 -->
                  <a-menu-item>
                    <router-link to="/my_space">
                      <UserOutlined />
                      我的空间
                    </router-link>
                  </a-menu-item>
                  <!-- 退出登录选项 -->
                  <a-menu-item @click="doLogout">
                    <LogoutOutlined />
                    退出登录
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <!-- 未登录状态：显示登录按钮 -->
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
  </div>
</template>

<script lang="ts" setup>
import { computed, h, ref } from 'vue'
import { HomeOutlined, LogoutOutlined, UserOutlined ,MailOutlined} from '@ant-design/icons-vue'
import { MenuProps, message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'
import logoUrl from '@/assets/logo.png'

// 登录用户状态管理（Pinia Store）
const loginUserStore = useLoginUserStore()

/**
 * 菜单配置与动态过滤逻辑
 */
// 原始菜单列表（包含所有可能的导航项）
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/add_picture',
    label: '创建图片',
    title: '创建图片',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/pictureManage',
    label: '图片管理',
    title: '图片管理',
  },
  {
    key: '/admin/spaceManage',
    label: '空间管理',
    title: '空间管理',
  },
]

/**
 * 菜单过滤逻辑：根据用户角色（管理员/普通用户）动态显示菜单
 * @param menus 原始菜单列表
 * @returns 过滤后的菜单列表
 */
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    // 若菜单是管理员路由，仅管理员可见
    if (menu.key.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 最终渲染的菜单列表（响应式计算属性）
const items = computed<MenuProps['items']>(() => filterMenus(originItems))

/**
 * 菜单高亮与路由跳转逻辑
 */
const router = useRouter()
// 当前选中的菜单key
const current = ref<string[]>([])

// 监听路由变化，自动更新菜单高亮
router.afterEach((to) => {
  current.value = [to.path]
})

/**
 * 菜单点击事件：触发路由跳转
 * @param {Object} param - 包含点击的菜单key
 */
const doMenuClick = ({ key }: { key: string }) => {
  router.push({
    path: key,
  })
}

/**
 * 用户退出登录逻辑
 */
const doLogout = async () => {
  const res = await userLogoutUsingPost()
  if (res.data.code === 0) {
    // 清空登录状态
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    await router.push('/user/login')
  } else {
    message.error('退出登录失败，' + res.data.message)
  }
}
</script>

<style scoped>
/* Logo与系统名称区域样式 */
.title-bar {
  display: flex;
  align-items: center;
}

.title {
  color: black;
  font-size: 18px;
  margin-left: 16px;
}

.logo {
  height: 48px;
}
</style>
