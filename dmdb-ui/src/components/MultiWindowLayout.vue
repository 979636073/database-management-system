<template>
  <el-container class="layout-container">
    <el-aside width="280px" class="sidebar">
      <div class="sidebar-header-wrapper">
        <div class="sidebar-header-top">
          <span class="app-title"><i class="el-icon-coin"></i> 数据库列表</span>
          <div class="header-actions">
            <el-button type="text" icon="el-icon-refresh" @click="handleRefresh" title="刷新列表" :loading="isRefreshing"
              style="margin-right: 8px; font-size: 16px">
            </el-button>
            <el-button type="text" icon="el-icon-circle-plus-outline" @click="openAddDialog" title="新建连接"
              style="font-size: 16px">
            </el-button>
          </div>
        </div>
        <div class="sidebar-search">
          <el-input placeholder="过滤库/表/角色..." v-model="filterText" size="mini" clearable prefix-icon="el-icon-search">
          </el-input>
        </div>
      </div>

      <el-tree ref="tree" :data="treeData" :props="defaultProps" :load="loadNode" lazy node-key="id" highlight-current
        :filter-node-method="filterNode" @node-click="handleNodeClick" class="db-tree" :expand-on-click-node="false">
        <span class="custom-tree-node" slot-scope="{ node, data }">
          <span class="node-label">
            <i :class="getIcon(data.type)" :style="{ color: data.connStatus === 'fail' ? '#F56C6C' : '' }"></i>

            {{ node.label }}

            <i v-if="data.loading" class="el-icon-loading" style="margin-left: 5px"></i>

            <el-tooltip v-if="data.type === 'root' && data.connStatus === 'fail'" class="item" effect="dark"
              :content="data.errorMsg || '连接失败'" placement="top">
              <span style="
                  color: #f56c6c;
                  font-size: 12px;
                  margin-left: 5px;
                  cursor: pointer;
                  font-weight: bold;
                ">
                <i class="el-icon-warning-outline"></i> (离线)
              </span>
            </el-tooltip>

            <span v-if="data.comment" style="color: #999; font-size: 12px; margin-left: 5px">{{ data.comment }}</span>

            <span v-if="data.type === 'trigger'" :style="{
              color: data.status === 'ENABLED' ? '#67C23A' : '#909399',
              fontSize: '12px',
              marginLeft: '5px',
              fontWeight: 'bold',
            }">
              {{ data.status === "ENABLED" ? "●" : "○" }}
            </span>
          </span>

          <span class="node-actions">
            <template v-if="data.type === 'root'">
              <el-button type="text" icon="el-icon-edit" size="mini" @click.stop="openEditDialog(data)"></el-button>
              <el-button type="text" icon="el-icon-delete" size="mini"
                @click.stop="handleDeleteConn(node, data)"></el-button>
            </template>

            <template v-if="data.type && data.type.startsWith('folder_')">
              <el-button type="text" icon="el-icon-plus" size="mini" title="新建"
                @click.stop="openCreateDialog(node, data)"></el-button>
            </template>

            <template v-if="['table', 'view', 'trigger', 'role'].includes(data.type)">
              <el-button type="text" icon="el-icon-delete" size="mini" style="color: #f56c6c" title="删除"
                @click.stop="handleDeleteObject(node, data)"></el-button>
            </template>
          </span>
        </span>
      </el-tree>
    </el-aside>

    <el-main class="main-content">
      <div v-if="tabs.length === 0" class="empty-state">
        <i class="el-icon-monitor"></i>
        <p>请新建连接并选择数据表、视图或角色进行管理</p>
      </div>

      <el-tabs v-else v-model="activeTab" type="card" closable @tab-remove="removeTab" class="content-tabs">
        <el-tab-pane v-for="item in tabs" :key="item.key" :label="item.title" :name="item.key">
          <TableDetail v-if="item.type === 'table' || item.type === 'view'" :conn-id="item.connId"
            :conn-name="item.connName" :schema="item.schema" :table-name="item.tableName" :table-type="item.tableType"
            :initial-filter="item.filter" :init-view-mode="item.initViewMode" @view-mode-change="handleViewModeChange"
            @open-table="handleOpenTable" />

          <RoleDetail v-else-if="item.type === 'role'" :conn-id="item.connId" :role-name="item.roleName" />
        </el-tab-pane>
      </el-tabs>
    </el-main>

    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="400px" :close-on-click-modal="false">
      <el-form :model="connForm" label-width="80px" size="small">
        <el-form-item label="名称" required>
          <el-input v-model="connForm.name" placeholder="连接名称 (唯一)"></el-input>
        </el-form-item>
        <el-form-item label="主机" required>
          <el-input v-model="connForm.host"></el-input>
        </el-form-item>
        <el-form-item label="端口" required>
          <el-input v-model="connForm.port"></el-input>
        </el-form-item>
        <el-form-item label="用户" required>
          <el-input v-model="connForm.user"></el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="connForm.password" type="password"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="success" plain @click="testConnection" :loading="testing">测试连接</el-button>
        <el-button type="primary" @click="submitConnection" :loading="connecting">保存并连接</el-button>
      </div>
    </el-dialog>

    <el-dialog title="新建数据表" :visible.sync="createTableVisible" width="700px" :close-on-click-modal="false">
      <el-form :model="tableForm" size="small" label-width="80px">
        <el-form-item label="表名" required>
          <el-input v-model="tableForm.tableName" placeholder="例如: ORDER_ITEMS" style="width: 100%"></el-input>
        </el-form-item>

        <div style="
            margin-bottom: 10px;
            font-weight: bold;
            border-bottom: 1px solid #eee;
            padding-bottom: 5px;
          ">
          列定义
          <el-button type="text" icon="el-icon-plus" style="float: right" @click="addTableColumn">添加列</el-button>
        </div>

        <el-table :data="tableForm.columns" border size="mini" max-height="300">
          <el-table-column label="列名" width="150">
            <template slot-scope="scope"><el-input v-model="scope.row.name" placeholder="列名"></el-input></template>
          </el-table-column>
          <el-table-column label="类型" width="120">
            <template slot-scope="scope">
              <el-select v-model="scope.row.type" placeholder="类型">
                <el-option value="VARCHAR"></el-option>
                <el-option value="INTEGER"></el-option>
                <el-option value="DATE"></el-option>
                <el-option value="TIMESTAMP"></el-option>
                <el-option value="DECIMAL"></el-option>
                <el-option value="TEXT"></el-option>
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="长度" width="80">
            <template slot-scope="scope"><el-input v-model="scope.row.length" :disabled="['INTEGER', 'DATE', 'TIMESTAMP', 'TEXT'].includes(
              scope.row.type
            )
              "></el-input></template>
          </el-table-column>
          <el-table-column label="主键" width="60" align="center">
            <template slot-scope="scope"><el-checkbox v-model="scope.row.pk"></el-checkbox></template>
          </el-table-column>
          <el-table-column label="非空" width="60" align="center">
            <template slot-scope="scope"><el-checkbox v-model="scope.row.notNull"></el-checkbox></template>
          </el-table-column>
          <el-table-column label="操作" width="60" align="center">
            <template slot-scope="scope"><el-button type="text" icon="el-icon-delete" style="color: #f56c6c"
                @click="removeTableColumn(scope.$index)"></el-button></template>
          </el-table-column>
        </el-table>
        <div style="margin-top: 5px; font-size: 12px; color: #909399;">
          <i class="el-icon-info"></i> 支持勾选多个列作为复合主键。
        </div>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="createTableVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitCreateTable" :loading="submitting">创建</el-button>
      </div>
    </el-dialog>

    <el-dialog title="新建视图" :visible.sync="createViewVisible" width="600px" :close-on-click-modal="false">
      <el-form :model="viewForm" size="small" label-width="80px">
        <el-form-item label="视图名" required>
          <el-input v-model="viewForm.name" placeholder="例如: V_USER_STATS"></el-input>
        </el-form-item>
        <el-form-item label="定义SQL" required>
          <el-input type="textarea" :rows="8" v-model="viewForm.sql" placeholder="SELECT * FROM ..."></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button size="small" @click="createViewVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitCreateView" :loading="submitting">创建</el-button>
      </div>
    </el-dialog>

    <el-dialog title="新建触发器" :visible.sync="createTriggerVisible" width="600px" :close-on-click-modal="false">
      <el-alert title="触发器定义较复杂，请直接输入完整 Create SQL" type="info" :closable="false"
        style="margin-bottom: 10px"></el-alert>
      <el-input type="textarea" :rows="12" v-model="triggerSql" placeholder="CREATE OR REPLACE TRIGGER ..."></el-input>
      <div slot="footer">
        <el-button size="small" @click="createTriggerVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="submitCreateTrigger" :loading="submitting">创建</el-button>
      </div>
    </el-dialog>

    <el-dialog title="新建角色" :visible.sync="createRoleVisible" width="400px" :close-on-click-modal="false">
      <el-form :model="roleForm" size="small" label-width="80px">
        <el-form-item label="角色名称" required>
          <el-input v-model="roleForm.name" placeholder="例如: ROLE_HR"></el-input>
        </el-form-item>
      </el-form>
      <div slot="footer">
        <el-button @click="createRoleVisible = false" size="small">取消</el-button>
        <el-button type="primary" @click="submitCreateRole" size="small">创建</el-button>
      </div>
    </el-dialog>

  </el-container>
</template>

<script>
import request from "@/utils/request";
import TableDetail from "./TableDetail.vue";
import RoleDetail from "./RoleDetail.vue";

const STORAGE_KEY = "DMDB_CONNECTIONS";

export default {
  name: "MultiWindowLayout",
  components: { TableDetail, RoleDetail },
  data() {
    return {
      filterText: "",
      treeData: [],
      defaultProps: { label: "label", isLeaf: "leaf" },
      tabs: [],
      activeTab: "",

      globalViewMode: "data",

      isRefreshing: false,
      dialogVisible: false,
      isEditMode: false,
      currentEditNode: null,
      dialogTitle: "新建连接",
      connecting: false,
      testing: false,
      connForm: {
        name: "本地达梦",
        host: "localhost",
        port: "5236",
        user: "SYSDBA",
        password: "",
      },

      submitting: false,
      currentNodeData: null,
      currentNodeRef: null,

      // 表/视图/触发器 弹窗状态
      createTableVisible: false,
      tableForm: {
        tableName: "",
        columns: [
          { name: "ID", type: "INTEGER", length: "", pk: true, notNull: true },
        ],
      },
      createViewVisible: false,
      viewForm: { name: "", sql: "" },
      createTriggerVisible: false,
      triggerSql: "",

      // 角色弹窗状态
      createRoleVisible: false,
      roleForm: { name: "" }
    };
  },
  watch: {
    filterText(val) {
      this.$refs.tree.filter(val);
    },
  },
  created() {
    this.restoreConnections();
  },
  methods: {
    filterNode(value, data) {
      if (!value) return true;
      return data.label.toLowerCase().indexOf(value.toLowerCase()) !== -1;
    },
    // 【核心】根据节点类型返回图标
    getIcon(type) {
      if (type === "root") return "el-icon-connection";
      if (type === "folder_schema") return "el-icon-files";
      if (type === "folder_role") return "el-icon-user-solid"; // 角色文件夹图标
      if (type === "schema") return "el-icon-folder-opened";
      if (type && type.startsWith("folder_")) return "el-icon-folder";
      if (type === "table") return "el-icon-document-copy";
      if (type === "view") return "el-icon-view";
      if (type === "trigger") return "el-icon-s-operation";
      if (type === "role") return "el-icon-user"; // 具体角色图标
      return "el-icon-document";
    },
    handleViewModeChange(mode) {
      this.globalViewMode = mode;
    },
    handleRefresh() {
      this.isRefreshing = true;
      this.treeData = [];
      this.$nextTick(() => {
        this.restoreConnections();
        setTimeout(() => {
          this.isRefreshing = false;
          this.$message.success("列表已刷新");
        }, 500);
      });
    },
    restoreConnections() {
      const cached = localStorage.getItem(STORAGE_KEY);
      if (cached) {
        try {
          const list = JSON.parse(cached);
          this.treeData = list.map((item) => ({
            ...item,
            loading: true,
            connStatus: "connecting",
            errorMsg: "",
          }));
          this.treeData.forEach(async (node) => {
            try {
              const res = await request.post("/connection/connect", {
                id: node.id,
                ...node.config,
              });
              const targetNode = this.treeData.find(n => n.id === node.id);
              if (targetNode) {
                if (res.data.code === 200) {
                  this.$set(targetNode, 'loading', false);
                  this.$set(targetNode, 'connStatus', 'success');
                  this.$set(targetNode, 'errorMsg', '');
                } else {
                  this.$set(targetNode, 'loading', false);
                  this.$set(targetNode, 'connStatus', 'fail');
                  this.$set(targetNode, 'errorMsg', res.data.msg);
                }
              }
            } catch (e) {
              const msg = e.response?.data?.msg || e.message || "连接失败";
              const targetNode = this.treeData.find(n => n.id === node.id);
              if (targetNode) {
                this.$set(targetNode, 'loading', false);
                this.$set(targetNode, 'connStatus', 'fail');
                this.$set(targetNode, 'errorMsg', msg);
              }
            }
          });
        } catch (e) {
          console.error("Restore failed:", e);
        }
      }
    },
    saveConnections() {
      const listToSave = this.treeData.map((node) => ({
        id: node.id,
        label: node.label,
        type: "root",
        leaf: false,
        config: node.config,
      }));
      localStorage.setItem(STORAGE_KEY, JSON.stringify(listToSave));
    },

    // 【核心】节点加载逻辑
    async loadNode(node, resolve) {
      if (node.level === 0) return resolve(this.treeData);
      const data = node.data;

      if (data.loading) {
        this.$message.warning("正在尝试恢复连接，请稍候...");
        return resolve([]);
      }
      if (data.type === "root" && data.connStatus === "fail") {
        this.$message.error(data.errorMsg || "连接不可用");
        return resolve([]);
      }

      if (data.type === "root") {
        resolve([
          { label: '模式', type: 'folder_schema', connId: data.id, connName: data.label, leaf: false },
          // 【新增】角色管理入口节点
          { label: '角色', type: 'folder_role', connId: data.id, connName: data.label, leaf: false }
        ]);
      } else if (data.type === "folder_role") {
        // 【新增】加载角色列表
        try {
          const res = await request.get("/db/roles", { headers: { "Conn-Id": data.connId } });
          const roles = (res.data.data || []).map(r => ({
            label: r.ROLE_NAME,
            type: "role",
            connId: data.connId,
            connName: data.connName,
            leaf: true
          }));
          resolve(roles);
        } catch (e) { resolve([]); }
      } else if (data.type === "folder_schema") {
        try {
          const res = await request.get("/db/schemas", {
            headers: { "Conn-Id": data.connId },
          });
          const sortedSchemas = (res.data.data || []).sort((a, b) => a.localeCompare(b));
          const schemaNodes = sortedSchemas.map((s) => ({
            label: s,
            type: "schema",
            connId: data.connId,
            connName: data.connName,
            leaf: false,
          }));
          resolve(schemaNodes);
        } catch (e) {
          resolve([]);
        }
      } else if (data.type === "schema") {
        resolve([
          { label: "数据表", type: "folder_table", connId: data.connId, connName: data.connName, schema: data.label, leaf: false },
          { label: "视图", type: "folder_view", connId: data.connId, connName: data.connName, schema: data.label, leaf: false },
          { label: "触发器", type: "folder_trigger", connId: data.connId, connName: data.connName, schema: data.label, leaf: false },
        ]);
      } else if (data.type === "folder_table") {
        try {
          const res = await request.get("/db/tables", {
            params: { schema: data.schema },
            headers: { "Conn-Id": data.connId },
          });
          const tables = res.data.data.map((t) => ({
            label: t.TABLE_NAME,
            comment: t.COMMENTS,
            type: "table",
            connId: data.connId,
            connName: data.connName,
            schema: data.schema,
            leaf: true,
          }));
          resolve(tables);
        } catch (e) {
          resolve([]);
        }
      } else if (data.type === "folder_view") {
        try {
          const res = await request.get("/db/views", {
            params: { schema: data.schema },
            headers: { "Conn-Id": data.connId },
          });
          const views = res.data.data.map((v) => ({
            label: v.VIEW_NAME,
            type: "view",
            connId: data.connId,
            connName: data.connName,
            schema: data.schema,
            leaf: true,
          }));
          resolve(views);
        } catch (e) {
          resolve([]);
        }
      } else if (data.type === "folder_trigger") {
        try {
          const res = await request.get("/db/triggers", {
            params: { schema: data.schema },
            headers: { "Conn-Id": data.connId },
          });
          const triggers = res.data.data.map((t) => ({
            label: t.TRIGGER_NAME,
            status: t.STATUS,
            type: "trigger",
            connId: data.connId,
            connName: data.connName,
            schema: data.schema,
            leaf: true,
          }));
          resolve(triggers);
        } catch (e) {
          resolve([]);
        }
      } else {
        resolve([]);
      }
    },

    openAddDialog() {
      this.isEditMode = false;
      this.dialogTitle = "新建连接";
      this.connForm = {
        name: "本地达梦",
        host: "localhost",
        port: "5236",
        user: "SYSDBA",
        password: "",
      };
      this.dialogVisible = true;
    },
    openEditDialog(data) {
      this.isEditMode = true;
      this.currentEditNode = data;
      this.dialogTitle = "编辑连接";
      this.connForm = JSON.parse(JSON.stringify(data.config || {}));
      if (!data.config) this.connForm.name = data.label;
      this.dialogVisible = true;
    },
    validateConnName(name) {
      if (!name || !name.trim()) return false;
      const exist = this.treeData.find((node) => {
        if (this.isEditMode && this.currentEditNode) {
          return node.label === name && node.id !== this.currentEditNode.id;
        }
        return node.label === name;
      });
      return !exist;
    },
    async testConnection() {
      this.testing = true;
      try {
        const payload = { ...this.connForm };
        if (this.isEditMode && this.currentEditNode) {
          payload.id = this.currentEditNode.id;
        }
        const res = await request.post("/connection/connect", payload);
        if (res.data.code === 200) {
          this.$message.success("连接成功！");
        } else {
          this.$message.error("连接失败: " + res.data.msg);
        }
      } catch (e) {
        this.$message.error("连接异常，请检查网络");
      } finally {
        this.testing = false;
      }
    },
    async submitConnection() {
      if (!this.connForm.name) {
        this.$message.warning("请输入连接名称");
        return;
      }
      if (!this.validateConnName(this.connForm.name)) {
        this.$message.warning("连接名称已存在，请使用其他名称");
        return;
      }
      this.connecting = true;
      try {
        if (this.isEditMode) {
          const connId = this.currentEditNode.id;
          const res = await request.post("/connection/connect", {
            id: connId,
            ...this.connForm,
          });
          if (res.data.code !== 200) {
            this.$message.error(res.data.msg || "连接失败，无法保存配置");
            return;
          }
          const targetNode = this.treeData.find(n => n.id === connId);
          if (targetNode) {
            this.$set(targetNode, "label", this.connForm.name);
            this.$set(targetNode, "config", JSON.parse(JSON.stringify(this.connForm)));
            this.$set(targetNode, "connStatus", "success");
            this.$set(targetNode, "errorMsg", "");
            this.$set(targetNode, "loading", false);
          }
          this.saveConnections();
          this.$message.success("更新成功");
          this.dialogVisible = false;
        } else {
          const res = await request.post("/connection/connect", this.connForm);
          if (res.data.code === 200) {
            const newId = res.data.data;
            const exists = this.treeData.some(n => n.id === newId);
            if (!exists) {
              this.treeData.push({
                id: newId,
                label: this.connForm.name,
                type: "root",
                leaf: false,
                config: JSON.parse(JSON.stringify(this.connForm)),
                connStatus: "success",
                errorMsg: "",
                loading: false,
              });
              this.saveConnections();
            }
            this.dialogVisible = false;
            this.$message.success("创建成功");
          } else {
            this.$message.error("创建失败: " + res.data.msg);
          }
        }
      } catch (e) {
        this.$message.error("连接失败，无法保存配置");
      } finally {
        this.connecting = false;
      }
    },
    handleDeleteConn(node, data) {
      this.$confirm(`确定删除连接【${data.label}】吗?`, "提示", {
        type: "warning",
      })
        .then(async () => {
          try {
            await request.delete("/connection/delete", {
              headers: { "Conn-Id": data.id },
            });
          } catch (e) { }
          this.treeData = this.treeData.filter(d => d.id !== data.id);
          this.tabs = this.tabs.filter((t) => t.connId !== data.id);
          if (this.tabs.length === 0) this.activeTab = "";
          else if (!this.tabs.find((t) => t.key === this.activeTab))
            this.activeTab = this.tabs[this.tabs.length - 1].key;
          this.saveConnections();
          this.$message.success("删除成功");
        })
        .catch(() => { });
    },

    // 【修复】点击节点逻辑
    handleNodeClick(data) {
      if (data.type === "table" || data.type === "view") {
        // 修复关键：将 data.label 作为 tableName 传入
        this.handleOpenTable({
          connId: data.connId,
          connName: data.connName,
          schema: data.schema,
          tableName: data.label, // 必须传递 tableName！
          type: data.type
        });
      } else if (data.type === "role") {
        // 【新增】打开角色详情 Tab
        this.openTab(data.connId, data.connName, null, data.label, 'role');
      } else if (data.type === "trigger") {
        this.$message.info(
          `触发器 [${data.label}]：${data.status === "ENABLED" ? "已启用" : "已禁用"
          } (暂不支持编辑)`
        );
      }
    },

    handleOpenTable(payload) {
      let cName = "Unknown";
      const sibling = this.tabs.find((t) => t.connId === payload.connId);
      if (sibling) cName = sibling.connName;
      else {
        const rootNode = this.treeData.find((t) => t.id === payload.connId);
        if (rootNode) cName = rootNode.label;
      }
      const type = payload.type || 'table';

      this.openTab(
        payload.connId,
        cName,
        payload.schema,
        payload.tableName, // 确保这里有值
        type,
        payload.filter,
        payload.initViewMode
      );
    },

    openTab(
      connId,
      connName,
      schema,
      tableName,
      tableType = 'table',
      filter = null,
      targetViewMode = null
    ) {
      // 区分角色和表/视图的 Key
      const isRole = tableType === 'role';
      const tabKey = isRole ? `ROLE-${connId}-${tableName}` : `${connId}-${schema}-${tableName}`;
      const title = isRole ? `角色: ${tableName}` : `${connName} - ${tableName}`;

      const existIndex = this.tabs.findIndex((t) => t.key === tabKey);
      const finalViewMode = targetViewMode || this.globalViewMode || "data";

      if (existIndex > -1) {
        this.activeTab = tabKey;
        if (!isRole) {
          const tab = this.tabs[existIndex];
          const needUpdate =
            filter || (targetViewMode && tab.initViewMode !== targetViewMode);
          if (needUpdate) {
            this.$set(this.tabs, existIndex, { ...tab, filter: filter || tab.filter, initViewMode: finalViewMode });
          }
        }
      } else {
        this.tabs.push({
          key: tabKey,
          title: title,
          connId: connId,
          connName: connName,
          schema: schema,
          tableName: tableName,
          tableType: tableType,
          type: tableType, // 供 v-if 使用
          roleName: isRole ? tableName : null,
          filter: filter,
          initViewMode: finalViewMode,
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
      this.tabs = tabs.filter((tab) => tab.key !== targetName);
    },

    openCreateDialog(node, data) {
      this.currentNodeData = data;
      this.currentNodeRef = node;
      if (data.type === "folder_table") {
        this.tableForm = {
          tableName: "",
          columns: [
            {
              name: "ID",
              type: "INTEGER",
              length: "",
              pk: true,
              notNull: true,
            },
          ],
        };
        this.createTableVisible = true;
      } else if (data.type === "folder_view") {
        this.viewForm = { name: "", sql: "" };
        this.createViewVisible = true;
      } else if (data.type === "folder_trigger") {
        this.triggerSql = `CREATE OR REPLACE TRIGGER "${data.schema}"."TRG_NAME"\nBEFORE INSERT ON "${data.schema}"."TABLE_NAME"\nFOR EACH ROW\nBEGIN\n\t-- logic here\nEND;`;
        this.createTriggerVisible = true;
      } else if (data.type === "folder_role") {
        // 【新增】新建角色
        this.roleForm.name = "";
        this.createRoleVisible = true;
      }
    },

    handleDeleteObject(node, data) {
      const typeName = { table: '表', view: '视图', trigger: '触发器', role: '角色' }[data.type];
      this.$confirm(
        `确定删除${typeName}【${data.label}】吗？此操作不可恢复！`,
        "危险操作",
        {
          type: "warning",
          confirmButtonClass: "el-button--danger",
        }
      )
        .then(async () => {
          try {
            const url = data.type === 'role' ? "/db/role/delete" : "/db/execute";
            const params = data.type === 'role' ? { roleName: data.label } : { sql: `DROP ${data.type.toUpperCase()} "${data.schema}"."${data.label}"` };
            const method = data.type === 'role' ? 'delete' : 'post';

            // 使用 request 实例，动态配置 method
            const reqConfig = {
              method,
              url,
              headers: { "Conn-Id": data.connId },
              [method === 'delete' ? 'params' : 'data']: params
            };

            const res = await request(reqConfig);

            if (res.data.code === 200) {
              this.$message.success("删除成功");
              const tabKey = data.type === 'role'
                ? `ROLE-${data.connId}-${data.label}`
                : `${data.connId}-${data.schema}-${data.label}`;
              this.removeTab(tabKey);
              if (node.parent) {
                node.parent.loaded = false;
                node.parent.expand();
              }
            } else {
              this.$message.error(res.data.msg);
            }
          } catch (e) {
            this.$message.error("操作失败");
          }
        })
        .catch(() => { });
    },

    addTableColumn() {
      this.tableForm.columns.push({
        name: "",
        type: "VARCHAR",
        length: "50",
        pk: false,
        notNull: false,
      });
    },
    removeTableColumn(index) {
      this.tableForm.columns.splice(index, 1);
    },
    async executeDDL(sql) {
      this.submitting = true;
      try {
        const res = await request.post(
          "/db/execute",
          { sql: sql },
          { headers: { "Conn-Id": this.currentNodeData.connId } }
        );
        if (res.data.code === 200) {
          this.$message.success("创建成功");
          this.currentNodeRef.loaded = false;
          this.currentNodeRef.expand();
          return true;
        } else {
          this.$message.error(res.data.msg);
          return false;
        }
      } catch (e) {
        this.$message.error("请求失败");
        return false;
      } finally {
        this.submitting = false;
      }
    },
    // 【核心修改】支持复合主键的建表 SQL 生成
    async submitCreateTable() {
      if (!this.tableForm.tableName) return this.$message.warning("请输入表名");
      const schema = this.currentNodeData.schema;
      const fullTableName = `"${schema}"."${this.tableForm.tableName}"`;

      // 1. 生成普通列定义
      let colsSql = this.tableForm.columns
        .map((col) => {
          let line = `"${col.name}" ${col.type}`;
          if (
            col.length &&
            !["INTEGER", "DATE", "TIMESTAMP", "TEXT"].includes(col.type)
          )
            line += `(${col.length})`;
          if (col.notNull) line += " NOT NULL";
          // 注意：不再这里加 PRIMARY KEY
          return line;
        })
        .join(",\n  ");

      // 2. 收集所有勾选了 PK 的列
      const pkCols = this.tableForm.columns
        .filter(c => c.pk)
        .map(c => `"${c.name}"`);

      // 3. 如果有主键，添加表级约束
      let pkSql = "";
      if (pkCols.length > 0) {
        // CONSTRAINT PK_表名 PRIMARY KEY (列1, 列2)
        pkSql = `,\n  CONSTRAINT "PK_${this.tableForm.tableName}" PRIMARY KEY (${pkCols.join(', ')})`;
      }

      const sql = `CREATE TABLE ${fullTableName} (\n  ${colsSql}${pkSql}\n);`;

      if (await this.executeDDL(sql)) this.createTableVisible = false;
    },
    async submitCreateView() {
      if (!this.viewForm.name || !this.viewForm.sql)
        return this.$message.warning("请填写完整");
      const schema = this.currentNodeData.schema;
      const sql = `CREATE OR REPLACE VIEW "${schema}"."${this.viewForm.name}" AS\n${this.viewForm.sql}`;
      if (await this.executeDDL(sql)) this.createViewVisible = false;
    },
    async submitCreateTrigger() {
      if (!this.triggerSql) return;
      if (await this.executeDDL(this.triggerSql))
        this.createTriggerVisible = false;
    },

    // 【新增】提交创建角色
    async submitCreateRole() {
      if (!this.roleForm.name) return this.$message.warning("请输入角色名称");
      try {
        const res = await request.post("/db/role/create", { roleName: this.roleForm.name }, { headers: { "Conn-Id": this.currentNodeData.connId } });
        if (res.data.code === 200) {
          this.$message.success("创建成功");
          this.createRoleVisible = false;
          this.currentNodeRef.loaded = false;
          this.currentNodeRef.expand();
        } else {
          this.$message.error(res.data.msg);
        }
      } catch (e) { this.$message.error("创建失败"); }
    }
  },
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

.sidebar-header-wrapper {
  border-bottom: 1px solid #eee;
  padding-bottom: 10px;
}

.sidebar-header-top {
  padding: 0 15px;
  height: 40px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.sidebar-search {
  padding: 0 10px;
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
  color: #409eff;
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