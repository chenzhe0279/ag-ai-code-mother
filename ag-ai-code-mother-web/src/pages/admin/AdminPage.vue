<template>
  <div class="admin-page">
    <a-page-header title="管理后台" />
    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="users" tab="用户管理">
        <div class="toolbar">
          <a-input-search v-model:value="userKeyword" placeholder="搜索账号/昵称" style="width: 260px" @search="loadUsers" />
          <a-button type="primary" @click="openUserModal">新增用户</a-button>
        </div>
        <a-table
          :loading="userLoading"
          :data-source="userRows"
          :columns="userColumns"
          row-key="id"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'userRole'">
              <a-tag :color="record.userRole === 'admin' ? 'gold' : 'blue'">
                {{ record.userRole === 'admin' ? '管理员' : '用户' }}
              </a-tag>
            </template>
            <template v-if="column.key === 'action'">
              <a-button type="link" size="small" @click="openUserEdit(record)">编辑</a-button>
              <a-popconfirm title="确认删除该用户？" @confirm="handleDeleteUser(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
        <a-pagination
          class="pager"
          v-model:current="userPage"
          :page-size="userSize"
          :total="userTotal"
          @change="loadUsers"
        />
      </a-tab-pane>

      <a-tab-pane key="apps" tab="应用管理">
        <div class="toolbar">
          <a-input-search v-model:value="appKeyword" placeholder="搜索应用名" style="width: 260px" @search="loadApps" />
        </div>
        <a-table
          :loading="appLoading"
          :data-source="appRows"
          :columns="appColumns"
          row-key="id"
          :pagination="false"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'action'">
              <a-button type="link" size="small" @click="openAppEdit(record)">编辑</a-button>
              <a-popconfirm title="确认删除该应用？" @confirm="handleDeleteApp(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </template>
          </template>
        </a-table>
        <a-pagination
          class="pager"
          v-model:current="appPage"
          :page-size="appSize"
          :total="appTotal"
          @change="loadApps"
        />
      </a-tab-pane>
    </a-tabs>

    <!-- 新增用户 -->
    <a-modal
      v-model:open="userModalOpen"
      title="新增用户"
      @ok="handleAddUser"
      :confirm-loading="userSaving"
    >
      <a-form :model="userForm" layout="vertical">
        <a-form-item label="账号" required>
          <a-input v-model:value="userForm.userAccount" placeholder="登录账号" />
        </a-form-item>
        <a-form-item label="昵称">
          <a-input v-model:value="userForm.userName" placeholder="用户昵称" />
        </a-form-item>
        <a-form-item label="简介">
          <a-input v-model:value="userForm.userProfile" placeholder="用户简介" />
        </a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="userForm.userRole">
            <a-select-option value="user">普通用户</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 编辑用户 -->
    <a-modal
      v-model:open="userEditOpen"
      title="编辑用户"
      @ok="handleUpdateUser"
      :confirm-loading="userSaving"
    >
      <a-form :model="userEditForm" layout="vertical">
        <a-form-item label="昵称">
          <a-input v-model:value="userEditForm.userName" />
        </a-form-item>
        <a-form-item label="简介">
          <a-input v-model:value="userEditForm.userProfile" />
        </a-form-item>
        <a-form-item label="角色">
          <a-select v-model:value="userEditForm.userRole">
            <a-select-option value="user">普通用户</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 编辑应用 -->
    <a-modal
      v-model:open="appEditOpen"
      title="编辑应用"
      @ok="handleUpdateApp"
      :confirm-loading="appSaving"
    >
      <a-form :model="appEditForm" layout="vertical">
        <a-form-item label="应用名称">
          <a-input v-model:value="appEditForm.appName" />
        </a-form-item>
        <a-form-item label="封面链接">
          <a-input v-model:value="appEditForm.cover" />
        </a-form-item>
        <a-form-item label="优先级">
          <a-input-number v-model:value="appEditForm.priority" :min="0" style="width: 100%" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import {
  addUser,
  deleteUser,
  listUserByPage,
  updateUser,
  type UserVO,
} from '@/api/user'
import {
  deleteAppAdmin,
  listAppByPageAdmin,
  updateAppAdmin,
  type AppVO,
} from '@/api/app'

const activeTab = ref('users')

// 用户管理
const userLoading = ref(false)
const userRows = ref<UserVO[]>([])
const userTotal = ref(0)
const userPage = ref(1)
const userSize = 10
const userKeyword = ref('')

const userColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 180 },
  { title: '账号', dataIndex: 'userAccount', key: 'userAccount' },
  { title: '昵称', dataIndex: 'userName', key: 'userName' },
  { title: '角色', key: 'userRole', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 140 },
]

const loadUsers = async () => {
  userLoading.value = true
  try {
    const res = await listUserByPage({
      pageNum: userPage.value,
      pageSize: userSize,
      userAccount: userKeyword.value || undefined,
      userName: userKeyword.value || undefined,
    })
    userRows.value = res.records || []
    userTotal.value = res.totalRow || 0
  } catch {
    // 提示已由拦截器处理
  } finally {
    userLoading.value = false
  }
}

// 新增用户
const userModalOpen = ref(false)
const userSaving = ref(false)
const userForm = reactive({
  userAccount: '',
  userName: '',
  userProfile: '',
  userRole: 'user',
})

const openUserModal = () => {
  Object.assign(userForm, { userAccount: '', userName: '', userProfile: '', userRole: 'user' })
  userModalOpen.value = true
}

const handleAddUser = async () => {
  if (!userForm.userAccount.trim()) {
    message.warning('请输入账号')
    return
  }
  userSaving.value = true
  try {
    await addUser({ ...userForm, userAccount: userForm.userAccount.trim() })
    message.success('新增成功（默认密码 12345678）')
    userModalOpen.value = false
    loadUsers()
  } catch {
    // 提示已由拦截器处理
  } finally {
    userSaving.value = false
  }
}

// 编辑用户
const userEditOpen = ref(false)
const userEditForm = reactive({ id: 0, userName: '', userProfile: '', userRole: 'user' })

const openUserEdit = (record: UserVO) => {
  Object.assign(userEditForm, {
    id: record.id,
    userName: record.userName,
    userProfile: record.userProfile,
    userRole: record.userRole,
  })
  userEditOpen.value = true
}

const handleUpdateUser = async () => {
  userSaving.value = true
  try {
    await updateUser({ ...userEditForm })
    message.success('更新成功')
    userEditOpen.value = false
    loadUsers()
  } catch {
    // 提示已由拦截器处理
  } finally {
    userSaving.value = false
  }
}

const handleDeleteUser = async (record: UserVO) => {
  if (!record.id) return
  await deleteUser(record.id)
  message.success('删除成功')
  loadUsers()
}

// 应用管理
const appLoading = ref(false)
const appRows = ref<AppVO[]>([])
const appTotal = ref(0)
const appPage = ref(1)
const appSize = 10
const appKeyword = ref('')

const appColumns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 180 },
  { title: '应用名', dataIndex: 'appName', key: 'appName' },
  { title: '类型', dataIndex: 'codeGenType', key: 'codeGenType', width: 100 },
  { title: '版本', dataIndex: 'currentVersion', key: 'currentVersion', width: 80 },
  { title: '优先级', dataIndex: 'priority', key: 'priority', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 140 },
]

const loadApps = async () => {
  appLoading.value = true
  try {
    const res = await listAppByPageAdmin({
      pageNum: appPage.value,
      pageSize: appSize,
      appName: appKeyword.value || undefined,
    })
    appRows.value = res.records || []
    appTotal.value = res.totalRow || 0
  } catch {
    // 提示已由拦截器处理
  } finally {
    appLoading.value = false
  }
}

const appEditOpen = ref(false)
const appSaving = ref(false)
const appEditForm = reactive({ id: 0, appName: '', cover: '', priority: 0 })

const openAppEdit = (record: AppVO) => {
  Object.assign(appEditForm, {
    id: record.id,
    appName: record.appName,
    cover: record.cover,
    priority: record.priority ?? 0,
  })
  appEditOpen.value = true
}

const handleUpdateApp = async () => {
  appSaving.value = true
  try {
    await updateAppAdmin({ ...appEditForm })
    message.success('更新成功')
    appEditOpen.value = false
    loadApps()
  } catch {
    // 提示已由拦截器处理
  } finally {
    appSaving.value = false
  }
}

const handleDeleteApp = async (record: AppVO) => {
  if (!record.id) return
  await deleteAppAdmin(record.id)
  message.success('删除成功')
  loadApps()
}

onMounted(() => {
  loadUsers()
  loadApps()
})
</script>

<style scoped>
.admin-page {
  background: #fff;
  border-radius: 12px;
  padding: 8px 16px 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 16px;
}

.pager {
  margin-top: 16px;
  text-align: right;
}
</style>
