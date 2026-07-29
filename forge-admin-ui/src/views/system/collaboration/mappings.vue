<template>
  <div class="collaboration-mappings-page">
    <n-card :bordered="false" class="h-full">
      <n-space class="mb-4" align="center">
        <span class="text-14px">连接：</span>
        <n-select
          v-model:value="connectionId"
          :options="connectionOptions"
          placeholder="请选择连接"
          clearable
          style="width: 320px"
          @update:value="handleConnectionChange"
        />
        <n-button :disabled="!connectionId" :loading="loading" @click="loadCurrentTab">
          刷新
        </n-button>
      </n-space>

      <n-tabs v-model:value="activeTab" type="line" @update:value="handleTabChange">
        <n-tab-pane name="orgs" tab="部门映射">
          <n-data-table
            :columns="orgColumns"
            :data="tableData.orgs"
            :loading="loading"
            :max-height="tableMaxHeight"
            size="small"
          />
        </n-tab-pane>
        <n-tab-pane name="users" tab="用户绑定">
          <n-data-table
            :columns="userColumns"
            :data="tableData.users"
            :loading="loading"
            :max-height="tableMaxHeight"
            size="small"
          />
        </n-tab-pane>
        <n-tab-pane name="posts" tab="岗位映射">
          <n-data-table
            :columns="postColumns"
            :data="tableData.posts"
            :loading="loading"
            :max-height="tableMaxHeight"
            size="small"
          />
        </n-tab-pane>
        <n-tab-pane name="tags" tab="标签">
          <n-data-table
            :columns="tagColumns"
            :data="tableData.tags"
            :loading="loading"
            :max-height="tableMaxHeight"
            size="small"
          />
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { fetchConnectionOptions, listMappings } from '@/api/collaboration'

defineOptions({ name: 'CollaborationMappings' })

const connectionId = ref(null)
const connectionOptions = ref([])
const activeTab = ref('orgs')
const loading = ref(false)
const tableMaxHeight = 560

const tableData = ref({ orgs: [], users: [], posts: [], tags: [] })

onMounted(async () => {
  connectionOptions.value = await fetchConnectionOptions()
  // 默认选中第一个连接并加载
  if (connectionOptions.value.length > 0) {
    connectionId.value = connectionOptions.value[0].value
    await loadCurrentTab()
  }
})

const orgColumns = [
  { key: 'id', title: 'ID', width: 90 },
  { key: 'externalDeptId', title: '外部部门ID', width: 140 },
  { key: 'externalParentId', title: '外部父部门ID', width: 140 },
  { key: 'externalDeptName', title: '外部部门名称', minWidth: 160, ellipsis: { tooltip: true } },
  { key: 'orgId', title: 'Forge组织ID', width: 130 },
  { key: 'status', title: '状态', width: 90 },
  { key: 'updateTime', title: '更新时间', width: 170 },
]

const userColumns = [
  { key: 'id', title: 'ID', width: 90 },
  { key: 'userId', title: 'Forge用户ID', width: 120 },
  { key: 'uuid', title: '外部用户ID', minWidth: 160, ellipsis: { tooltip: true } },
  { key: 'username', title: '用户名', width: 130, ellipsis: { tooltip: true } },
  { key: 'nickname', title: '昵称', width: 130, ellipsis: { tooltip: true } },
  { key: 'externalStatus', title: '外部状态', width: 100 },
  {
    key: 'managedBySync',
    title: '同步托管',
    width: 90,
    render: row => (row.managedBySync === 1 ? '是' : '否'),
  },
  { key: 'lastSyncTime', title: '最近同步时间', width: 170 },
]

const postColumns = [
  { key: 'id', title: 'ID', width: 90 },
  { key: 'externalPostCode', title: '外部岗位编码', width: 160, ellipsis: { tooltip: true } },
  { key: 'externalPostName', title: '外部岗位名称', minWidth: 160, ellipsis: { tooltip: true } },
  { key: 'postId', title: 'Forge岗位ID', width: 130 },
  { key: 'status', title: '状态', width: 90 },
]

const tagColumns = [
  { key: 'id', title: 'ID', width: 90 },
  { key: 'externalTagId', title: '外部标签ID', width: 140 },
  { key: 'tagName', title: '标签名称', minWidth: 160, ellipsis: { tooltip: true } },
  { key: 'status', title: '状态', width: 90 },
]

async function loadCurrentTab() {
  if (!connectionId.value) {
    tableData.value = { orgs: [], users: [], posts: [], tags: [] }
    return
  }
  loading.value = true
  try {
    const res = await listMappings(activeTab.value, connectionId.value)
    if (res.code === 200)
      tableData.value[activeTab.value] = res.data || []
  }
  catch {
    window.$message.error('加载映射数据失败')
  }
  finally {
    loading.value = false
  }
}

function handleConnectionChange() {
  tableData.value = { orgs: [], users: [], posts: [], tags: [] }
  loadCurrentTab()
}

function handleTabChange() {
  loadCurrentTab()
}
</script>

<style scoped>
.collaboration-mappings-page {
  height: 100%;
}
</style>
