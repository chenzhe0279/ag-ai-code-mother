<template>
  <div id="userManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="账号">
        <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" />
      </a-form-item>
      <a-form-item label="用户名">
        <a-input v-model:value="searchParams.userName" placeholder="输入用户名" />
      </a-form-item>
      <a-form-item label="角色">
        <a-select
          v-model:value="searchParams.userRole"
          placeholder="全部"
          style="width: 130px"
          allow-clear
        >
          <a-select-option :value="undefined">全部</a-select-option>
          <a-select-option value="user">普通用户</a-select-option>
          <a-select-option value="admin">管理员</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <a-divider />
    <div class="table-toolbar">
      <a-button type="primary" @click="openAddModal">新增用户</a-button>
    </div>
    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-image :src="resolveAvatarUrl(record.userAvatar)" :width="120" />
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <div v-if="record.userRole === 'admin'">
            <a-tag color="green">管理员</a-tag>
          </div>
          <div v-else>
            <a-tag color="blue">普通用户</a-tag>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'isVip'">
          <a-tag v-if="record.isVip" color="gold">VIP</a-tag>
          <span v-else class="text-gray">-</span>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button type="primary" size="small" @click="openEditModal(record)">编辑</a-button>
            <a-popconfirm title="确定要删除这个用户吗？" @confirm="doDelete(record.id)">
              <a-button danger size="small">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>

    <!-- 新增 / 编辑用户弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="isEditMode ? '编辑用户' : '新增用户'"
      :confirm-loading="submitting"
      @ok="handleSubmit"
      @cancel="handleCancel"
    >
      <a-alert
        v-if="!isEditMode"
        message="新用户初始密码为 12345678，请提醒用户及时修改"
        type="info"
        show-icon
        class="modal-tip"
      />
      <a-form ref="formRef" :model="formData" :rules="formRules" layout="vertical">
        <a-form-item label="账号" name="userAccount">
          <a-input
            v-model:value="formData.userAccount"
            placeholder="请输入账号"
            :disabled="isEditMode"
          />
        </a-form-item>
        <a-form-item label="昵称" name="userName">
          <a-input v-model:value="formData.userName" placeholder="请输入昵称" />
        </a-form-item>
        <a-form-item label="头像地址" name="userAvatar">
          <a-input v-model:value="formData.userAvatar" placeholder="请输入头像图片地址" />
        </a-form-item>
        <a-form-item label="简介" name="userProfile">
          <a-textarea v-model:value="formData.userProfile" placeholder="请输入简介" :rows="3" />
        </a-form-item>
        <a-form-item label="角色" name="userRole">
          <a-select v-model:value="formData.userRole" placeholder="请选择角色">
            <a-select-option value="user">普通用户</a-select-option>
            <a-select-option value="admin">管理员</a-select-option>
          </a-select>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { addUser, deleteUser, listUserVoByPage, updateUser } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import type { FormInstance } from 'ant-design-vue'
import dayjs from 'dayjs'
import { resolveAvatarUrl } from '@/config/env'

// ==================== 新增 / 编辑用户 ====================
const modalVisible = ref(false)
const isEditMode = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
// 编辑时记录当前用户 id（账号不允许修改，所以不放进表单）
const editingId = ref<number>()
const formData = reactive<API.UserAddRequest>({
  userAccount: '',
  userName: '',
  userAvatar: '',
  userProfile: '',
  userRole: 'user',
})
const formRules = {
  userAccount: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  userName: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  userRole: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

// 重置表单为初始状态
const resetForm = () => {
  formData.userAccount = ''
  formData.userName = ''
  formData.userAvatar = ''
  formData.userProfile = ''
  formData.userRole = 'user'
  editingId.value = undefined
  formRef.value?.clearValidate()
}

// 打开新增弹窗
const openAddModal = () => {
  isEditMode.value = false
  resetForm()
  modalVisible.value = true
}

// 打开编辑弹窗（回显当前行数据）
const openEditModal = (record: API.UserVO) => {
  isEditMode.value = true
  resetForm()
  editingId.value = record.id
  formData.userAccount = record.userAccount ?? ''
  formData.userName = record.userName ?? ''
  formData.userAvatar = record.userAvatar ?? ''
  formData.userProfile = record.userProfile ?? ''
  formData.userRole = record.userRole ?? 'user'
  modalVisible.value = true
}

const handleCancel = () => {
  modalVisible.value = false
  resetForm()
}

// 提交新增 / 编辑
const handleSubmit = async () => {
  try {
    await formRef.value?.validate()
  } catch {
    // 校验未通过，表单已给出提示
    return
  }
  submitting.value = true
  try {
    const res = isEditMode.value
      ? await updateUser({
          id: editingId.value,
          userName: formData.userName,
          userAvatar: formData.userAvatar,
          userProfile: formData.userProfile,
          userRole: formData.userRole,
        })
      : await addUser({
          userAccount: formData.userAccount,
          userName: formData.userName,
          userAvatar: formData.userAvatar,
          userProfile: formData.userProfile,
          userRole: formData.userRole,
        })
    if (res.data.code === 0) {
      message.success(isEditMode.value ? '修改成功' : '新增成功')
      modalVisible.value = false
      resetForm()
      fetchData()
    } else {
      message.error((isEditMode.value ? '修改失败：' : '新增失败：') + res.data.message)
    }
  } catch (error) {
    console.error('提交失败：', error)
    message.error('提交失败，请重试')
  } finally {
    submitting.value = false
  }
}

const columns = [
  {
    title: 'id',
    dataIndex: 'id',
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
  },
  {
    title: '用户名',
    dataIndex: 'userName',
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
  },
  {
    title: '用户角色',
    dataIndex: 'userRole',
  },
  {
    title: '会员',
    dataIndex: 'isVip',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '操作',
    key: 'action',
  },
]

// 展示的数据
const data = ref<API.UserVO[]>([])
const total = ref(0)

// 搜索条件
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// 获取数据
const fetchData = async () => {
  const res = await listUserVoByPage({
    ...searchParams,
  })
  if (res.data.data) {
    data.value = res.data.data.records ?? []
    total.value = res.data.data.totalRow ?? 0
  } else {
    message.error('获取数据失败，' + res.data.message)
  }
}

// 分页参数
const pagination = computed(() => {
  return {
    current: searchParams.pageNum ?? 1,
    pageSize: searchParams.pageSize ?? 10,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 表格分页变化时的操作
const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 搜索数据
const doSearch = () => {
  // 重置页码
  searchParams.pageNum = 1
  fetchData()
}

// 删除数据
const doDelete = async (id: number | undefined) => {
  if (!id) {
    return
  }
  const res = await deleteUser({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    fetchData()
  } else {
    message.error('删除失败')
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#userManagePage {
  padding: 24px;
  margin-top: 16px;
  border: 1px solid var(--line-soft);
  border-radius: 16px;
  background: rgba(13, 20, 42, 0.55);
  backdrop-filter: blur(12px);
}

.table-toolbar {
  margin-bottom: 16px;
}

.modal-tip {
  margin-bottom: 16px;
}

.text-gray {
  color: #8c9abc;
}
</style>
