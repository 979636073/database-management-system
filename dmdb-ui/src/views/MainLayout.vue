<template>
  <el-container class="layout-container">
    <el-aside width="280px" class="sidebar">
      <div class="sidebar-header">
        <span class="app-title"><i class="el-icon-coin"></i> 数据库列表</span>
        <div class="header-actions">
          <el-button type="text" icon="el-icon-refresh" @click="handleRefresh" title="刷新列表" :loading="isRefreshing"
            style="margin-right: 8px; font-size: 16px;"></el-button>
          <el-button type="text" icon="el-icon-circle-plus-outline" @click="openAddDialog" title="新建连接"
            style="font-size: 16px;"></el-button>
        </div>
      </div>

      <el-tree ref="tree" :data="treeData" :props="defaultProps" :load="loadNode" lazy node-key="id" highlight-current
        @node-click="handleNodeClick" class="db-tree" :expand-on-click-node="false">
        <span class="custom-tree-node" slot-scope="{ node, data }">
          <span class="node-label">
            <i :class="getIcon(data.type)"></i> {{ node.label }}
            <i v-if="data.loading" class="el-icon-loading" style="margin-left:5px"></i>
            <span v-if="data.comment" style="color:#999;font-size:12px;margin-left:5px;">{{ data.comment }}</span>
          </span>
          <span v-if="data.type === 'root'" class="node-actions">
            <el-button type="text" icon="el-icon-edit" size="mini" @click.stop="openEditDialog(data)"></el-button>
            <el-button type="text" icon="el-icon-delete" size="mini"
              @click.stop="handleDeleteConn(node, data)"></el-button>
          </span>
        </span>
      </el-tree>
    </el-aside>

    <el-main class="main-content">
      <div v-if="tabs.length === 0" class="empty-state">
        <i class="el-icon-monitor"></i>
        <p>请新建连接并选择数据表</p>
      </div>

      <el-tabs v-else v-model="activeTab" type="card" closable @tab-remove="removeTab" class="content-tabs">
        <el-tab-pane v-for="item in tabs" :key="item.key" :label="item.title" :name="item.key">
          <TableDetail :conn-id="item.connId" :conn-name="item.connName" :schema="item.schema"
            :table-name="item.tableName" :initial-filter="item.filter" :init-view-mode="item.initViewMode"
            @view-mode-change="handleViewModeChange" @open-table="handleOpenTable" />
        </el-tab-pane>
      </el-tabs>
    </el-main>

    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="400px">
      <el-form :model="connForm" label-width="80px" size="small">
        <el-form-item label="名称"><el-input v-model="connForm.name"></el-input></el-form-item>
        <el-form-item label="主机"><el-input v-model="connForm.host"></el-input></el-form-item>
        <el-form-item label="端口"><el-input v-model="connForm.port"></el-input></el-form-item>
        <el-form-item label="用户"><el-input v-model="connForm.user"></el-input></el-form-item>
        <el-form-item label="密码"><el-input v-model="connForm.password" type="password"></el-input></el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConnection" :loading="connecting">确定</el-button>
      </div>
    </el-dialog>
  </el-container>
</template>

<script>
import request from '@/utils/request';
import TableDetail from './TableDetail.vue';

const STORAGE_KEY = 'DMDB_CONNECTIONS';

export default {
  name: 'MultiWindowLayout',
  components: { TableDetail },
  data() {
    return {
      treeData: [],
      defaultProps: { label: 'label', isLeaf: 'leaf' },
      tabs: [],
      activeTab: '',

      // 全局视图模式记忆 (默认为数据视图)
      globalViewMode: 'data',

      isRefreshing: false,
      dialogVisible: false,
      isEditMode: false,
      currentEditNode: null,
      dialogTitle: '新建连接',
      connecting: false,
      connForm: { name: '本地达梦', host: 'localhost', port: '5236', user: 'SYSDBA', password: '' }
    };
  },

  created() {
    this.restoreConnections();
  },

  methods: {
    getIcon(type) {
      if (type === 'root') return 'el-icon-connection';
      if (type === 'schema') return 'el-icon-folder';
      if (type === 'table') return 'el-icon-document';
      return '';
    },

    // 子组件切换视图时，更新全局偏好
    handleViewModeChange(mode) {
      this.globalViewMode = mode;
    },

    handleRefresh() {
      this.isRefreshing = true;
      this.treeData = [];
      this.restoreConnections();
      setTimeout(() => {
        this.isRefreshing = false;
        this.$message.success('列表已刷新');
      }, 500);
    },

    restoreConnections() {
      const cached = localStorage.getItem(STORAGE_KEY);
      if (cached) {
        try {
          const list = JSON.parse(cached);
          this.treeData = list.map(item => ({ ...item, loading: true }));
          this.treeData.forEach(async (node) => {
            try {
              await request.post('/connection/connect', { id: node.id, ...node.config });
              node.loading = false;
            } catch (e) {
              node.label += ' (离线)';
              node.loading = false;
            }
          });
        } catch (e) { console.error(e); }
      }
    },

    saveConnections() {
      const listToSave = this.treeData.map(node => ({
        id: node.id, label: node.label, type: 'root', leaf: false, config: node.config
      }));
      localStorage.setItem(STORAGE_KEY, JSON.stringify(listToSave));
    },

    async loadNode(node, resolve) {
      if (node.level === 0) return resolve(this.treeData);
      const data = node.data;

      if (data.loading) {
        this.$message.warning('正在尝试恢复连接，请稍候...');
        return resolve([]);
      }

      if (data.type === 'root') {
        try {
          const res = await request.get('/db/schemas', { headers: { 'Conn-Id': data.id } });
          const schemas = res.data.data.map(s => ({
            label: s, type: 'schema', connId: data.id, connName: data.label, leaf: false
          }));
          resolve(schemas);
        } catch (e) { resolve([]); }
      }

      if (data.type === 'schema') {
        try {
          const res = await request.get('/db/tables', { params: { schema: data.label }, headers: { 'Conn-Id': data.connId } });
          const tables = res.data.data.map(t => ({
            label: t.TABLE_NAME,
            comment: t.COMMENTS,
            type: 'table',
            connId: data.connId,
            connName: data.connName,
            schema: data.label,
            leaf: true
          }));
          resolve(tables);
        } catch (e) { resolve([]); }
      }
    },

    openAddDialog() {
      this.isEditMode = false;
      this.dialogTitle = '新建连接';
      this.connForm = { name: '本地达梦', host: 'localhost', port: '5236', user: 'SYSDBA', password: '' };
      this.dialogVisible = true;
    },

    openEditDialog(data) {
      this.isEditMode = true;
      this.currentEditNode = data;
      this.dialogTitle = '编辑连接';
      this.connForm = JSON.parse(JSON.stringify(data.config || {}));
      if (!data.config) this.connForm.name = data.label;
      this.dialogVisible = true;
    },

    async submitConnection() {
      this.connecting = true;
      try {
        if (this.isEditMode) {
          const connId = this.currentEditNode.id;
          await request.post('/connection/connect', { id: connId, ...this.connForm });
          this.currentEditNode.label = this.connForm.name;
          this.currentEditNode.config = JSON.parse(JSON.stringify(this.connForm));
          this.saveConnections();
          this.$message.success('更新成功');
          this.dialogVisible = false;
        } else {
          const res = await request.post('/connection/connect', this.connForm);
          if (res.data.code === 200) {
            this.treeData.push({
              id: res.data.data,
              label: this.connForm.name,
              type: 'root',
              leaf: false,
              config: JSON.parse(JSON.stringify(this.connForm))
            });
            this.saveConnections();
            this.dialogVisible = false;
            this.$message.success('连接成功');
          } else { this.$message.error(res.data.msg); }
        }
      } catch (e) { this.$message.error('连接失败'); }
      finally { this.connecting = false; }
    },

    handleDeleteConn(node, data) {
      this.$confirm(`确定删除连接【${data.label}】吗?`, '提示', { type: 'warning' }).then(async () => {
        try { await request.delete('/connection/delete', { headers: { 'Conn-Id': data.id } }); } catch (e) { }
        const parent = node.parent;
        const children = parent.data.children || parent.data;
        const index = children.findIndex(d => d.id === data.id);
        children.splice(index, 1);

        this.tabs = this.tabs.filter(t => t.connId !== data.id);
        if (this.tabs.length === 0) this.activeTab = '';
        else if (!this.tabs.find(t => t.key === this.activeTab)) this.activeTab = this.tabs[this.tabs.length - 1].key;

        this.saveConnections();
        this.$message.success('删除成功');
      }).catch(() => { });
    },

    handleNodeClick(data) {
      if (data.type === 'table') {
        this.openTab(data.connId, data.connName, data.schema, data.label);
      }
    },

    handleOpenTable(payload) {
      let cName = 'Unknown';
      const sibling = this.tabs.find(t => t.connId === payload.connId);
      if (sibling) cName = sibling.connName;
      else {
        const rootNode = this.treeData.find(t => t.id === payload.connId);
        if (rootNode) cName = rootNode.label;
      }
      // 提取 initViewMode 并传递
      this.openTab(payload.connId, cName, payload.schema, payload.tableName, payload.filter, payload.initViewMode);
    },

    // 核心打开 Tab 逻辑
    openTab(connId, connName, schema, tableName, filter = null, targetViewMode = null) {
      const tabKey = `${connId}-${schema}-${tableName}`;
      const existIndex = this.tabs.findIndex(t => t.key === tabKey);

      // 决定使用哪种视图模式：显式跳转 > 全局记忆 > 默认数据
      const finalViewMode = targetViewMode || this.globalViewMode || 'data';

      if (existIndex > -1) {
        this.activeTab = tabKey;

        const tab = this.tabs[existIndex];
        // 如果有筛选条件，或者要求切换视图模式，则强制更新 Tab
        const needUpdate = filter || (targetViewMode && tab.initViewMode !== targetViewMode);

        if (needUpdate) {
          const updatedTab = {
            ...tab,
            filter: filter || tab.filter,
            // 更新 initViewMode，子组件 watch 监听到后会切换视图
            initViewMode: finalViewMode
          };
          // ⚠️ 必须使用 $set 触发响应式更新
          this.$set(this.tabs, existIndex, updatedTab);
        }
      } else {
        this.tabs.push({
          key: tabKey,
          title: `${connName} - ${tableName}`,
          connId: connId,
          connName: connName,
          schema: schema,
          tableName: tableName,
          filter: filter,
          initViewMode: finalViewMode
        });
        this.activeTab = tabKey;
      }
    },

    removeTab(targetName) {
      const tabs = this.tabs;
      let activeName = this.activeTab;
      if (activeName === targetName) {
        tabs.forEach((tab, index) => {
          if (tab.key === targetName) {
            const nextTab = tabs[index + 1] || tabs[index - 1];
            if (nextTab) activeName = nextTab.key;
          }
        });
      }
      this.activeTab = activeName;
      this.tabs = tabs.filter(tab => tab.key !== targetName);
    }
  }
};
</script>

<style scoped>
.layout-container {
  height: 100vh;
  background: #f0f2f5;
}

.sidebar {
  background: #fff;
  border-right: 1px solid #e6e6e6;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 0 15px;
  height: 50px;
  border-bottom: 1px solid #eee;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.app-title {
  font-weight: bold;
  color: #333;
  font-size: 14px;
}

.header-actions {
  display: flex;
  align-items: center;
}

.db-tree {
  flex: 1;
  overflow-y: auto;
  padding-top: 5px;
}

.custom-tree-node {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  padding-right: 8px;
}

.custom-tree-node i {
  margin-right: 5px;
  color: #409EFF;
}

.node-actions {
  display: none;
}

.custom-tree-node:hover .node-actions {
  display: inline-block;
}

.main-content {
  padding: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  overflow: hidden;
}

.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #ccc;
}

.empty-state i {
  font-size: 60px;
  margin-bottom: 10px;
}

::v-deep .el-tabs {
  height: 100%;
  display: flex;
  flex-direction: column;
}

::v-deep .el-tabs__header {
  margin: 0;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
}

::v-deep .el-tabs__content {
  flex: 1;
  padding: 0;
  overflow: hidden;
  height: 100%;
}

::v-deep .el-tab-pane {
  height: 100%;
}
</style>