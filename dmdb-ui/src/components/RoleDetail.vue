<template>
    <div class="role-manage-container" v-loading="loading">

        <div class="main-header">
            <div class="role-identity">
                <div class="avatar-box">
                    <i class="el-icon-user-solid"></i>
                </div>
                <div class="info">
                    <div class="label">当前管理角色</div>
                    <div class="value">{{ roleName }}</div>
                </div>
            </div>
            <div class="actions">
                <el-button type="primary" plain icon="el-icon-refresh" size="small"
                    @click="refreshData">刷新数据</el-button>
            </div>
        </div>

        <div class="main-content">
            <el-tabs v-model="activeTab" type="border-card" class="stylish-tabs">

                <el-tab-pane name="general">
                    <span slot="label"><i class="el-icon-s-custom"></i> 角色授予</span>
                    <div class="pane-layout">
                        <div class="pane-toolbar">
                            <div class="toolbar-left">
                                <i class="el-icon-info text-info"></i>
                                <span class="tip-text">将其他角色授予给当前角色 (继承权限)</span>
                            </div>
                            <el-button type="primary" icon="el-icon-check" size="small"
                                @click="saveGeneral">保存配置</el-button>
                        </div>

                        <div class="table-wrapper">
                            <el-table :data="generalRoles" border stripe height="100%" size="medium"
                                :header-cell-style="headerStyle">
                                <el-table-column prop="name" label="角色名称">
                                    <template slot-scope="scope">
                                        <el-tag size="medium" effect="light" class="priv-name-tag">
                                            <i class="el-icon-user"></i> {{ scope.row.name }}
                                        </el-tag>
                                    </template>
                                </el-table-column>
                                <el-table-column label="授予 (Grant)" align="center" width="160">
                                    <template slot-scope="scope">
                                        <el-checkbox v-model="scope.row.granted" @change="handleGrantChange(scope.row)"
                                            class="big-checkbox"></el-checkbox>
                                    </template>
                                </el-table-column>
                                <el-table-column label="允许转授 (With Admin)" align="center" width="200">
                                    <template slot-scope="scope">
                                        <el-checkbox v-model="scope.row.admin" @change="handleAdminChange(scope.row)"
                                            class="big-checkbox"></el-checkbox>
                                    </template>
                                </el-table-column>
                            </el-table>
                        </div>
                    </div>
                </el-tab-pane>

                <el-tab-pane name="sysPrivs">
                    <span slot="label"><i class="el-icon-setting"></i> 系统权限</span>
                    <div class="pane-layout">
                        <div class="pane-toolbar">
                            <div class="toolbar-left">
                                <i class="el-icon-warning-outline text-warning"></i>
                                <span class="tip-text">配置全局系统操作权限 (如建表、建用户等)</span>
                            </div>
                            <el-button type="primary" icon="el-icon-check" size="small"
                                @click="saveSysPrivs">保存配置</el-button>
                        </div>

                        <div class="table-wrapper">
                            <el-table :data="sysPrivList" border stripe height="100%" size="medium"
                                :header-cell-style="headerStyle">
                                <el-table-column prop="name" label="权限名称">
                                    <template slot-scope="scope">
                                        <el-tag size="medium" effect="light" class="priv-name-tag">{{ scope.row.name
                                        }}</el-tag>
                                    </template>
                                </el-table-column>

                                <el-table-column align="center" width="180">
                                    <template slot="header" slot-scope="scope">
                                        <el-checkbox v-model="sysGrantAll" @change="handleSysGrantAllChange"
                                            class="header-checkbox">授予 (Grant)</el-checkbox>
                                    </template>
                                    <template slot-scope="scope">
                                        <el-checkbox v-model="scope.row.granted"
                                            @change="handleSysRowChange(scope.row, 'grant')"
                                            class="big-checkbox"></el-checkbox>
                                    </template>
                                </el-table-column>

                                <el-table-column align="center" width="220">
                                    <template slot="header" slot-scope="scope">
                                        <el-checkbox v-model="sysAdminAll" @change="handleSysAdminAllChange"
                                            class="header-checkbox">转授 (With Admin)</el-checkbox>
                                    </template>
                                    <template slot-scope="scope">
                                        <el-checkbox v-model="scope.row.admin"
                                            @change="handleSysRowChange(scope.row, 'admin')"
                                            class="big-checkbox"></el-checkbox>
                                    </template>
                                </el-table-column>
                            </el-table>
                        </div>
                    </div>
                </el-tab-pane>

                <el-tab-pane name="objPrivs">
                    <span slot="label"><i class="el-icon-document-copy"></i> 对象权限</span>

                    <div class="obj-layout">
                        <div class="obj-sidebar">
                            <div class="sidebar-search">
                                <el-select v-model="selectedSchema" placeholder="选择模式" size="small" @change="loadTables"
                                    class="schema-select">
                                    <el-option v-for="s in schemaList" :key="s" :label="s" :value="s">
                                        <span style="float: left; font-weight: bold">{{ s }}</span>
                                        <span style="float: right; color: #8492a6; font-size: 12px"><i
                                                class="el-icon-folder-opened"></i></span>
                                    </el-option>
                                </el-select>
                                <el-input placeholder="过滤表名..." v-model="tableFilter" size="small" clearable
                                    prefix-icon="el-icon-search"></el-input>
                            </div>

                            <div class="sidebar-list custom-scroll">
                                <div v-for="t in filteredTables" :key="t" class="nav-item"
                                    :class="{ 'is-active': currentTable === t }" @click="handleTableSelect(t)">
                                    <i class="el-icon-document"></i>
                                    <span class="nav-text">{{ t }}</span>
                                </div>
                                <div v-if="filteredTables.length === 0" class="empty-nav">暂无数据</div>
                            </div>
                        </div>

                        <div class="obj-detail">
                            <div v-if="currentTable" class="detail-inner">
                                <div class="pane-toolbar">
                                    <div class="toolbar-left">
                                        <span class="label">当前对象：</span>
                                        <el-tag type="success" effect="dark" size="medium">{{ selectedSchema }} . {{
                                            currentTable
                                        }}</el-tag>
                                    </div>
                                    <el-button type="primary" icon="el-icon-check" size="small"
                                        @click="saveObjPrivs">应用权限</el-button>
                                </div>

                                <div class="table-wrapper">
                                    <el-table :data="currentObjPerms" border stripe height="100%" size="medium"
                                        :header-cell-style="headerStyle">
                                        <el-table-column prop="name" label="操作类型">
                                            <template slot-scope="scope">
                                                <el-tag size="medium" effect="light" class="priv-name-tag">{{
                                                    scope.row.name }}</el-tag>
                                            </template>
                                        </el-table-column>

                                        <el-table-column align="center" width="180">
                                            <template slot="header" slot-scope="scope">
                                                <el-checkbox v-model="objGrantAll" @change="handleObjGrantAllChange"
                                                    class="header-checkbox">授予 (Grant)</el-checkbox>
                                            </template>
                                            <template slot-scope="scope">
                                                <el-checkbox v-model="scope.row.granted"
                                                    @change="handleObjRowChange(scope.row, 'grant')"
                                                    class="big-checkbox"></el-checkbox>
                                            </template>
                                        </el-table-column>

                                        <el-table-column align="center" width="220">
                                            <template slot="header" slot-scope="scope">
                                                <el-checkbox v-model="objAdminAll" @change="handleObjAdminAllChange"
                                                    class="header-checkbox">转授 (Grant Opt)</el-checkbox>
                                            </template>
                                            <template slot-scope="scope">
                                                <el-checkbox v-model="scope.row.admin"
                                                    @change="handleObjRowChange(scope.row, 'admin')"
                                                    class="big-checkbox"></el-checkbox>
                                            </template>
                                        </el-table-column>
                                    </el-table>
                                </div>
                            </div>

                            <div v-else class="empty-state">
                                <i class="el-icon-mouse"></i>
                                <p>请在左侧列表点击选择一个数据表</p>
                            </div>
                        </div>
                    </div>
                </el-tab-pane>
            </el-tabs>
        </div>
    </div>
</template>

<script>
import request from '@/utils/request';

const CORE_ROLES = ["DBA", "PUBLIC", "RESOURCE", "SOI", "SVI", "VTI"];

const SYS_PRIVS = [
    "CREATE TABLE", "CREATE VIEW", "CREATE PROCEDURE", "CREATE TRIGGER",
    "CREATE SEQUENCE", "CREATE SYNONYM",
    "CREATE ROLE", "CREATE USER", "DROP USER",
    "SELECT ANY TABLE", "INSERT ANY TABLE", "UPDATE ANY TABLE", "DELETE ANY TABLE"
];

const OBJ_PRIVS = ["SELECT", "INSERT", "UPDATE", "DELETE", "ALTER", "INDEX", "REFERENCES", "SELECT FOR DUMP"];

export default {
    name: 'RoleDetail',
    props: ['connId', 'roleName'],
    data() {
        return {
            loading: false,
            activeTab: 'general',
            headerStyle: { background: '#f5f7fa', color: '#606266', fontWeight: 'bold' },

            generalRoles: CORE_ROLES.map(r => ({ name: r, granted: false, admin: false, _orig: { granted: false, admin: false } })),
            sysPrivList: SYS_PRIVS.map(p => ({ name: p, granted: false, admin: false, _orig: { granted: false, admin: false } })),

            schemaList: [], selectedSchema: '', tableList: [], tableFilter: '',
            currentTable: '',
            currentObjPerms: OBJ_PRIVS.map(p => ({ name: p, granted: false, admin: false })),
            existingObjPrivs: [],

            sysGrantAll: false, sysAdminAll: false,
            objGrantAll: false, objAdminAll: false,
        };
    },
    computed: {
        filteredTables() {
            if (!this.tableFilter) return this.tableList;
            return this.tableList.filter(t => t.toLowerCase().includes(this.tableFilter.toLowerCase()));
        },
        filteredExistingObjPrivs() {
            if (!this.selectedSchema) return [];
            return this.existingObjPrivs.filter(p => this.getMapValue(p, 'OWNER') === this.selectedSchema);
        }
    },
    mounted() { this.refreshData(); },
    methods: {
        async request(method, url, data = {}) {
            const config = { method, url: '/db' + url, headers: { 'Conn-Id': this.connId } };
            if (method === 'get') config.params = data; else config.data = data;
            return request(config);
        },
        getMapValue(obj, key) {
            if (!obj) return null;
            if (obj[key] !== undefined) return obj[key];
            if (obj[key.toLowerCase()] !== undefined) return obj[key.toLowerCase()];
            if (obj[key.toUpperCase()] !== undefined) return obj[key.toUpperCase()];
            return null;
        },
        isOptionTrue(val) {
            if (!val) return false;
            const s = String(val).toUpperCase();
            return s === 'YES' || s === 'Y' || s === 'TRUE' || s === '1';
        },

        // --- 全选逻辑 ---
        handleSysGrantAllChange(val) {
            this.sysPrivList.forEach(item => { item.granted = val; if (!val) item.admin = false; });
            this.updateSysAdminHeaderState();
        },
        handleSysAdminAllChange(val) {
            this.sysPrivList.forEach(item => { item.admin = val; if (val) item.granted = true; });
            this.updateSysGrantHeaderState();
        },
        handleObjGrantAllChange(val) {
            this.currentObjPerms.forEach(item => { item.granted = val; if (!val) item.admin = false; });
            this.updateObjAdminHeaderState();
        },
        handleObjAdminAllChange(val) {
            this.currentObjPerms.forEach(item => { item.admin = val; if (val) item.granted = true; });
            this.updateObjGrantHeaderState();
        },

        // --- 行点击逻辑 ---
        handleGrantChange(row) { if (!row.granted) row.admin = false; },
        handleAdminChange(row) { if (row.admin) row.granted = true; },

        handleSysRowChange(row, type) {
            if (type === 'grant') this.handleGrantChange(row);
            if (type === 'admin') this.handleAdminChange(row);
            this.updateSysGrantHeaderState();
            this.updateSysAdminHeaderState();
        },
        handleObjRowChange(row, type) {
            if (type === 'grant') this.handleGrantChange(row);
            if (type === 'admin') this.handleAdminChange(row);
            this.updateObjGrantHeaderState();
            this.updateObjAdminHeaderState();
        },

        updateSysGrantHeaderState() { this.sysGrantAll = this.sysPrivList.length > 0 && this.sysPrivList.every(i => i.granted); },
        updateSysAdminHeaderState() { this.sysAdminAll = this.sysPrivList.length > 0 && this.sysPrivList.every(i => i.admin); },
        updateObjGrantHeaderState() { this.objGrantAll = this.currentObjPerms.length > 0 && this.currentObjPerms.every(i => i.granted); },
        updateObjAdminHeaderState() { this.objAdminAll = this.currentObjPerms.length > 0 && this.currentObjPerms.every(i => i.admin); },

        // --- API 调用 ---
        refreshData() { this.loading = true; this.initData().finally(() => { this.loading = false; }); },
        async initData() {
            try {
                // 【核心修复】增加 /metadata 前缀，变为 /db/metadata/schemas
                const sRes = await this.request('get', '/metadata/schemas');
                this.schemaList = sRes.data.data || [];
                if (this.schemaList.includes('DMDB')) this.selectedSchema = 'DMDB';
                else if (this.schemaList.length) this.selectedSchema = this.schemaList[0];
                await Promise.all([this.loadTables(), this.loadRoleInfo()]);
            } catch (e) { this.$message.error("初始化失败: " + e.message); }
        },
        async loadRoleInfo() {
            const res = await this.request('get', '/role/detail', { roleName: this.roleName });
            if (res.data.code === 200) {
                const data = res.data.data;

                const dbRoles = data.rolePrivs || [];
                this.generalRoles.forEach(row => {
                    const found = dbRoles.find(r => this.getMapValue(r, 'GRANTED_ROLE') === row.name);
                    row.granted = !!found;
                    row.admin = !!(found && this.isOptionTrue(this.getMapValue(found, 'ADMIN_OPTION')));
                    row._orig = { granted: row.granted, admin: row.admin };
                });

                const dbSys = data.sysPrivs || [];
                this.sysPrivList.forEach(row => {
                    const found = dbSys.find(p => {
                        const pName = this.getMapValue(p, 'PRIVILEGE');
                        return pName && pName.trim().toUpperCase() === row.name.trim().toUpperCase();
                    });
                    row.granted = !!found;
                    row.admin = !!(found && this.isOptionTrue(this.getMapValue(found, 'ADMIN_OPTION')));
                    row._orig = { granted: row.granted, admin: row.admin };
                });

                this.existingObjPrivs = data.objPrivs || [];
                this.$nextTick(() => { this.updateSysGrantHeaderState(); this.updateSysAdminHeaderState(); });
            }
        },
        async loadTables() {
            if (!this.selectedSchema) return;
            // 【核心修复】增加 /metadata 前缀，变为 /db/metadata/tables
            const res = await this.request('get', '/metadata/tables', { schema: this.selectedSchema });
            this.tableList = (res.data.data || []).map(t => t.TABLE_NAME);
            this.currentTable = '';
        },
        handleTableSelect(tableName) {
            this.currentTable = tableName;
            this.currentObjPerms = OBJ_PRIVS.map(pName => {
                const found = this.existingObjPrivs.find(p =>
                    this.getMapValue(p, 'OWNER') === this.selectedSchema &&
                    this.getMapValue(p, 'TABLE_NAME') === tableName &&
                    this.getMapValue(p, 'PRIVILEGE').toUpperCase() === pName.toUpperCase()
                );
                return {
                    name: pName,
                    granted: !!found,
                    admin: !!(found && this.isOptionTrue(this.getMapValue(found, 'GRANTABLE')))
                };
            });
            this.$nextTick(() => { this.updateObjGrantHeaderState(); this.updateObjAdminHeaderState(); });
        },
        getChanges(list, keyName) {
            const changes = [];
            list.forEach(row => {
                if (!row._orig && keyName !== 'priv') return;
                const origGranted = row._orig ? row._orig.granted : false;
                const origAdmin = row._orig ? row._orig.admin : false;
                if (!origGranted && row.granted) changes.push({ [keyName]: row.name, action: 'GRANT', admin: row.admin });
                else if (origGranted && !row.granted) changes.push({ [keyName]: row.name, action: 'REVOKE', admin: false });
                else if (row.granted && row.admin !== origAdmin) changes.push({ [keyName]: row.name, action: 'GRANT', admin: row.admin });
            });
            return changes;
        },
        async saveGeneral() {
            const changes = this.getChanges(this.generalRoles, 'role');
            if (!changes.length) return this.$message.info("无变更");
            this.loading = true;
            try { await this.request('post', '/role/role-privs', { roleName: this.roleName, changes }); this.$message.success("保存成功"); await this.loadRoleInfo(); } catch (e) { this.$message.error(e.response?.data?.msg || "保存失败"); } finally { this.loading = false; }
        },
        async saveSysPrivs() {
            const changes = this.getChanges(this.sysPrivList, 'priv');
            if (!changes.length) return this.$message.info("无变更");
            this.loading = true;
            try { await this.request('post', '/role/sys-privs', { roleName: this.roleName, changes }); this.$message.success("保存成功"); await this.loadRoleInfo(); } catch (e) { this.$message.error(e.response?.data?.msg || "保存失败"); } finally { this.loading = false; }
        },
        async saveObjPrivs() {
            if (!this.currentTable) return;
            const changes = [];
            this.currentObjPerms.forEach(row => {
                const found = this.existingObjPrivs.find(p =>
                    this.getMapValue(p, 'OWNER') === this.selectedSchema &&
                    this.getMapValue(p, 'TABLE_NAME') === this.currentTable &&
                    this.getMapValue(p, 'PRIVILEGE').toUpperCase() === row.name.toUpperCase()
                );
                const origGranted = !!found;
                const origAdmin = !!(found && this.isOptionTrue(this.getMapValue(found, 'GRANTABLE')));
                if (!origGranted && row.granted) changes.push({ priv: row.name, action: 'GRANT', admin: row.admin });
                else if (origGranted && !row.granted) changes.push({ priv: row.name, action: 'REVOKE', admin: false });
                else if (row.granted && row.admin !== origAdmin) changes.push({ priv: row.name, action: 'GRANT', admin: row.admin });
            });
            if (!changes.length) return this.$message.info("无变更");
            this.loading = true;
            try { await this.request('post', '/role/obj-privs', { roleName: this.roleName, schema: this.selectedSchema, table: this.currentTable, changes }); this.$message.success("保存成功"); await this.loadRoleInfo(); this.handleTableSelect(this.currentTable); } catch (e) { this.$message.error(e.response?.data?.msg || "保存失败"); } finally { this.loading = false; }
        }
    }
}
</script>

<style scoped>
/* 全局容器 */
.role-manage-container {
    height: 100%;
    display: flex;
    flex-direction: column;
    background-color: #fff;
    border-radius: 4px;
    overflow: hidden;
}

/* 1. Header 样式 */
.main-header {
    height: 60px;
    padding: 0 20px;
    border-bottom: 1px solid #ebeef5;
    display: flex;
    align-items: center;
    justify-content: space-between;
    background: #fff;
    box-shadow: 0 2px 4px rgba(0, 0, 0, 0.02);
    z-index: 10;
}

.role-identity {
    display: flex;
    align-items: center;
}

.avatar-box {
    width: 36px;
    height: 36px;
    border-radius: 50%;
    background: #ecf5ff;
    color: #409EFF;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 18px;
    margin-right: 12px;
    border: 1px solid #d9ecff;
}

.info .label {
    font-size: 12px;
    color: #909399;
    line-height: 1.2;
}

.info .value {
    font-size: 16px;
    font-weight: 600;
    color: #303133;
}

/* 2. Tabs 样式 */
.main-content {
    flex: 1;
    overflow: hidden;
    padding: 15px 20px;
}

.stylish-tabs {
    height: 100%;
    display: flex;
    flex-direction: column;
    box-shadow: none !important;
    border: 1px solid #ebeef5;
}

::v-deep .el-tabs__content {
    flex: 1;
    height: 0;
    padding: 0;
    overflow: hidden;
}

::v-deep .el-tab-pane {
    height: 100%;
}

/* 通用面板布局 */
.pane-layout {
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: 15px;
}

.pane-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
    background-color: #fcfcfc;
    border: 1px solid #ebeef5;
    padding: 8px 12px;
    border-radius: 4px;
}

.toolbar-left {
    font-size: 13px;
    color: #606266;
    display: flex;
    align-items: center;
}

.text-info {
    color: #909399;
    margin-right: 6px;
    font-size: 16px;
}

.text-warning {
    color: #E6A23C;
    margin-right: 6px;
    font-size: 16px;
}

.table-wrapper {
    flex: 1;
    overflow: hidden;
    border: 1px solid #ebeef5;
    border-radius: 2px;
}

/* 3. 对象权限 双栏布局 */
.obj-layout {
    height: 100%;
    display: flex;
}

/* 左侧导航 */
.obj-sidebar {
    width: 260px;
    border-right: 1px solid #ebeef5;
    background-color: #fdfdfd;
    display: flex;
    flex-direction: column;
}

.sidebar-search {
    padding: 12px;
    border-bottom: 1px solid #ebeef5;
    background: #fff;
}

.schema-select {
    width: 100%;
    margin-bottom: 8px;
}

.sidebar-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px 0;
}

.nav-item {
    padding: 10px 15px;
    cursor: pointer;
    font-size: 13px;
    color: #606266;
    display: flex;
    align-items: center;
    border-left: 3px solid transparent;
    transition: all 0.2s;
}

.nav-item i {
    margin-right: 8px;
    color: #909399;
}

.nav-item:hover {
    background-color: #f5f7fa;
    color: #409EFF;
}

.nav-item:hover i {
    color: #409EFF;
}

.nav-item.is-active {
    background-color: #ecf5ff;
    color: #409EFF;
    border-left-color: #409EFF;
    font-weight: 500;
}

.nav-item.is-active i {
    color: #409EFF;
}

.empty-nav {
    text-align: center;
    color: #909399;
    margin-top: 20px;
    font-size: 12px;
}

/* 右侧详情 */
.obj-detail {
    flex: 1;
    background: #fff;
    overflow: hidden;
}

.detail-inner {
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: 15px;
}

.label {
    font-size: 13px;
    color: #909399;
    margin-right: 8px;
}

.empty-state {
    height: 100%;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    color: #c0c4cc;
}

.empty-state i {
    font-size: 48px;
    margin-bottom: 10px;
    color: #e4e7ed;
}

/* 统一的绿色 Tag 样式 */
.priv-name-tag {
    background-color: #f0f9eb;
    border-color: #e1f3d8;
    color: #67c23a;
    font-weight: 500;
    border-radius: 4px;
}

/* 复选框微调 */
::v-deep .el-checkbox__inner {
    width: 16px;
    height: 16px;
}

::v-deep .el-checkbox__inner::after {
    left: 5px;
    top: 2px;
}

/* 自定义滚动条 */
.custom-scroll::-webkit-scrollbar {
    width: 6px;
}

.custom-scroll::-webkit-scrollbar-thumb {
    background: #dcdfe6;
    border-radius: 3px;
}

.custom-scroll::-webkit-scrollbar-track {
    background: transparent;
}
</style>