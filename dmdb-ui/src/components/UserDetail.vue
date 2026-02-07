<template>
    <div class="user-detail-container" v-loading="loading">
        <div class="user-banner">
            <div class="banner-left">
                <div class="avatar-box"><i class="el-icon-user-solid"></i></div>
                <div class="info-box">
                    <h1 class="username">{{ userInfo.USERNAME }}</h1>
                    <div class="meta">
                        <el-tag size="mini" :type="userInfo.ACCOUNT_STATUS === 'OPEN' ? 'success' : 'danger'"
                            effect="dark">{{ userInfo.ACCOUNT_STATUS }}</el-tag>
                        <span class="meta-item"><i class="el-icon-folder"></i> {{ userInfo.DEFAULT_TABLESPACE }}</span>
                        <span class="meta-item"><i class="el-icon-time"></i> {{ userInfo.CREATED }}</span>
                    </div>
                </div>
            </div>
            <div class="banner-actions">
                <el-button size="small" :type="userInfo.ACCOUNT_STATUS === 'OPEN' ? 'warning' : 'success'"
                    :icon="userInfo.ACCOUNT_STATUS === 'OPEN' ? 'el-icon-lock' : 'el-icon-unlock'" plain
                    @click="toggleLock">
                    {{ userInfo.ACCOUNT_STATUS === 'OPEN' ? '锁定' : '解锁' }}
                </el-button>
                <el-button size="small" type="primary" icon="el-icon-key" plain
                    @click="pwdVisible = true">改密</el-button>
                <el-button size="small" type="danger" icon="el-icon-delete" plain @click="handleDelete">删除</el-button>
            </div>
        </div>

        <div class="tabs-area">
            <el-tabs v-model="activeTab" type="border-card" class="stylish-tabs">

                <el-tab-pane label="基本信息" name="general">
                    <div class="tab-content-pad">
                        <el-descriptions title="账户属性" :column="2" border>
                            <el-descriptions-item label="用户名">{{ userInfo.USERNAME }}</el-descriptions-item>
                            <el-descriptions-item label="账户状态">{{ userInfo.ACCOUNT_STATUS }}</el-descriptions-item>
                            <el-descriptions-item label="默认表空间"><el-tag size="small">{{ userInfo.DEFAULT_TABLESPACE
                                    }}</el-tag></el-descriptions-item>
                            <el-descriptions-item label="概要文件">{{ userInfo.PROFILE || 'DEFAULT'
                                }}</el-descriptions-item>
                        </el-descriptions>
                        <div style="margin-top: 30px;">
                            <h4 style="margin-bottom: 15px; color: #606266;">配额管理</h4>
                            <el-button size="small" icon="el-icon-coin" @click="openQuota">设置表空间配额</el-button>
                        </div>
                    </div>
                </el-tab-pane>

                <el-tab-pane label="所属角色" name="role">
                    <div class="toolbar" style="justify-content: flex-end;">
                        <el-button type="primary" size="small" icon="el-icon-check" :loading="savingRoles"
                            @click="saveRoles" :disabled="!roleChanged">保存变更</el-button>
                        <el-button size="small" icon="el-icon-refresh" @click="loadPrivileges"
                            style="margin-left: 10px;">重置</el-button>
                    </div>
                    <el-table :data="filteredRoleList" border size="small" height="calc(100% - 60px)" stripe
                        highlight-current-row :row-style="rowStyleHelper">
                        <el-table-column prop="name" label="角色名称" sortable min-width="200">
                            <template slot-scope="scope">
                                <div
                                    :style="{ color: scope.row.granted ? '#409EFF' : '#606266', fontWeight: scope.row.granted ? 'bold' : 'normal' }">
                                    <i class="el-icon-user-solid" style="margin-right: 5px;"></i>
                                    {{ scope.row.name }}
                                </div>
                            </template>
                        </el-table-column>
                        <el-table-column label="授予" width="100" align="center">
                            <template slot-scope="scope">
                                <el-checkbox v-model="scope.row.granted"
                                    @change="onRoleGrantedChange(scope.row)"></el-checkbox>
                            </template>
                        </el-table-column>
                        <el-table-column label="转授" width="150" align="center">
                            <template slot-scope="scope">
                                <el-checkbox v-model="scope.row.admin"
                                    @change="onRoleAdminChange(scope.row)"></el-checkbox>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-tab-pane>

                <el-tab-pane label="系统权限" name="sys">
                    <div class="toolbar">
                        <div class="left">
                            <el-input v-model="sysPrivFilter" placeholder="搜索系统权限..." size="small"
                                prefix-icon="el-icon-search" clearable style="width: 240px;"></el-input>
                        </div>
                        <div class="right">
                            <el-button type="primary" size="small" icon="el-icon-check" :loading="savingSys"
                                @click="saveSysPrivs" :disabled="!sysChanged">保存变更</el-button>
                            <el-button size="small" icon="el-icon-refresh" @click="loadPrivileges">重置</el-button>
                        </div>
                    </div>
                    <el-table :data="filteredSysList" border size="small" height="calc(100% - 60px)"
                        highlight-current-row @sort-change="handleSysSort" :row-style="sysRowStyleHelper">
                        <el-table-column prop="name" label="权限名称" sortable="custom" min-width="200">
                            <template slot-scope="scope">
                                <div v-if="scope.row._isAllRow" style="font-weight: bold; color: #303133;">All</div>
                                <div v-else
                                    :style="{ color: scope.row.granted ? '#409EFF' : '#606266', fontWeight: scope.row.granted ? 'bold' : 'normal' }">
                                    <i class="el-icon-key" style="margin-right: 5px;"></i>
                                    {{ scope.row.name }}
                                </div>
                            </template>
                        </el-table-column>
                        <el-table-column label="授予" width="100" align="center">
                            <template slot-scope="scope">
                                <el-checkbox v-if="scope.row._isAllRow" v-model="sysHeader.allGranted"
                                    :indeterminate="sysHeader.indeterminateGranted"
                                    @change="handleCheckAllSysGranted"></el-checkbox>
                                <el-checkbox v-else v-model="scope.row.granted"
                                    @change="onSysGrantedChange(scope.row)"></el-checkbox>
                            </template>
                        </el-table-column>
                        <el-table-column label="转授" width="100" align="center">
                            <template slot-scope="scope">
                                <el-checkbox v-if="scope.row._isAllRow" v-model="sysHeader.allAdmin"
                                    :indeterminate="sysHeader.indeterminateAdmin"
                                    @change="handleCheckAllSysAdmin"></el-checkbox>
                                <el-checkbox v-else v-model="scope.row.admin"
                                    @change="onSysAdminChange(scope.row)"></el-checkbox>
                            </template>
                        </el-table-column>
                    </el-table>
                </el-tab-pane>

                <el-tab-pane label="对象权限" name="obj">
                    <div class="obj-layout">
                        <div class="obj-sidebar">
                            <div class="sidebar-search">
                                <el-select v-model="selectedSchema" placeholder="选择模式" size="small" @change="loadTables"
                                    class="schema-select">
                                    <el-option v-for="s in schemaList" :key="s" :label="s" :value="s">
                                        <span style="float: left; font-weight: bold">{{ s }}</span>
                                    </el-option>
                                </el-select>
                                <el-input placeholder="过滤表名..." v-model="tableFilter" size="small" clearable
                                    prefix-icon="el-icon-search"></el-input>
                            </div>
                            <div class="sidebar-list custom-scroll" v-loading="loadingTables">
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
                                        <el-tag type="success" effect="dark" size="small">{{ selectedSchema }}.{{
                                            currentTable
                                            }}</el-tag>
                                    </div>
                                    <el-button type="primary" icon="el-icon-check" size="small" :loading="savingObj"
                                        @click="saveObjPrivs">应用权限</el-button>
                                </div>

                                <el-table :data="currentObjPerms" border stripe height="100%" size="medium"
                                    :header-cell-style="headerStyle">
                                    <el-table-column prop="name" label="操作类型">
                                        <template slot-scope="scope">
                                            <el-tag size="medium" effect="light" class="priv-name-tag">{{ scope.row.name
                                                }}</el-tag>
                                        </template>
                                    </el-table-column>

                                    <el-table-column align="center" width="180">
                                        <template slot="header" slot-scope="scope">
                                            <div class="custom-header-cell center">
                                                <div class="header-top">授予</div>
                                                <div class="header-bottom">
                                                    <el-checkbox v-model="objGrantAll"
                                                        @change="handleObjGrantAllChange"></el-checkbox>
                                                </div>
                                            </div>
                                        </template>
                                        <template slot-scope="scope">
                                            <el-checkbox v-model="scope.row.granted"
                                                @change="handleObjRowChange(scope.row, 'grant')"
                                                class="big-checkbox"></el-checkbox>
                                        </template>
                                    </el-table-column>

                                    <el-table-column align="center" width="180">
                                        <template slot="header" slot-scope="scope">
                                            <div class="custom-header-cell center">
                                                <div class="header-top">转授</div>
                                                <div class="header-bottom">
                                                    <el-checkbox v-model="objAdminAll"
                                                        @change="handleObjAdminAllChange"></el-checkbox>
                                                </div>
                                            </div>
                                        </template>
                                        <template slot-scope="scope">
                                            <el-checkbox v-model="scope.row.admin"
                                                @change="handleObjRowChange(scope.row, 'admin')"
                                                class="big-checkbox"></el-checkbox>
                                        </template>
                                    </el-table-column>
                                </el-table>
                            </div>
                            <div v-else class="empty-state">
                                <i class="el-icon-mouse"></i>
                                <p>请在左侧列表选择一个数据表</p>
                            </div>
                        </div>
                    </div>
                </el-tab-pane>
            </el-tabs>
        </div>

        <el-dialog title="修改密码" :visible.sync="pwdVisible" width="400px" append-to-body>
            <el-form label-width="80px" size="small">
                <el-form-item label="用户"><b>{{ username }}</b></el-form-item>
                <el-form-item label="新密码" required><el-input v-model="newPassword"
                        show-password></el-input></el-form-item>
            </el-form>
            <div slot="footer"><el-button type="primary" @click="submitPwd" size="small"
                    :loading="submitting">保存</el-button>
            </div>
        </el-dialog>

        <el-dialog title="设置配额" :visible.sync="quotaVisible" width="450px" append-to-body>
            <el-form label-width="100px" size="small">
                <el-form-item label="表空间"><el-select v-model="quotaForm.tablespace" style="width: 100%"><el-option
                            v-for="ts in tablespaces" :key="ts" :label="ts"
                            :value="ts"></el-option></el-select></el-form-item>
                <el-form-item label="配额大小"><el-input v-model="quotaForm.quota"
                        placeholder="如 100M 或 UNLIMITED"></el-input></el-form-item>
            </el-form>
            <div slot="footer"><el-button type="primary" @click="submitQuota" size="small"
                    :loading="submitting">保存</el-button>
            </div>
        </el-dialog>
    </div>
</template>

<script>
import request from '@/utils/request';

const OBJ_PRIVS = ["SELECT", "INSERT", "UPDATE", "DELETE", "ALTER", "INDEX", "REFERENCES", "SELECT FOR DUMP"];

export default {
    name: 'UserDetail',
    props: ['connId', 'username'],
    data() {
        return {
            loading: false,
            activeTab: 'general',
            userInfo: {},

            // 角色 & 系统权限
            roleList: [], roleFilter: '', roleChanged: false,
            sysList: [], sysPrivFilter: '', sysChanged: false,
            sysSort: { prop: 'name', order: 'ascending' },
            sysHeader: { allGranted: false, indeterminateGranted: false, allAdmin: false, indeterminateAdmin: false },

            // 对象权限 (新)
            schemaList: [], selectedSchema: '', tableList: [], tableFilter: '', loadingTables: false,
            currentTable: '',
            currentObjPerms: OBJ_PRIVS.map(p => ({ name: p, granted: false, admin: false })),
            existingObjPrivs: [], // 存储后端返回的所有对象权限
            objGrantAll: false, objAdminAll: false,

            // 元数据
            allRoles: [], allSysPrivs: [], tablespaces: [],
            savingRoles: false, savingSys: false, savingObj: false, granting: false, submitting: false,
            pwdVisible: false, newPassword: '',
            quotaVisible: false, quotaForm: { tablespace: '', quota: 'UNLIMITED' },

            headerStyle: { background: '#f5f7fa', color: '#606266', fontWeight: 'bold' }
        };
    },
    computed: {
        filteredRoleList() {
            if (!this.roleFilter) return this.roleList;
            const k = this.roleFilter.toUpperCase();
            return this.roleList.filter(r => r.name.toUpperCase().includes(k));
        },
        filteredSysList() {
            let list = this.sysList;
            if (this.sysPrivFilter) {
                const k = this.sysPrivFilter.toUpperCase();
                list = list.filter(s => s.name.toUpperCase().includes(k));
            }
            list.sort((a, b) => {
                const prop = this.sysSort.prop || 'name';
                const order = this.sysSort.order === 'descending' ? -1 : 1;
                let valA = a[prop], valB = b[prop];
                if (typeof valA === 'boolean') { valA = valA ? 1 : 0; valB = valB ? 1 : 0; }
                if (valA < valB) return -1 * order;
                if (valA > valB) return 1 * order;
                return 0;
            });
            const allRow = { _isAllRow: true, name: 'All', granted: false, admin: false };
            return [allRow, ...list];
        },
        // 对象权限：过滤表列表
        filteredTables() {
            if (!this.tableFilter) return this.tableList;
            return this.tableList.filter(t => t.toLowerCase().includes(this.tableFilter.toLowerCase()));
        }
    },
    watch: {
        username: { immediate: true, handler(val) { if (val) this.loadData(); } },
        filteredSysList: { handler: 'updateSysHeaderState', deep: true }
    },
    methods: {
        async request(method, url, data = {}) {
            // 如果 url 以 /db 开头，则不做前缀处理（用于调用 metadata 接口）
            const realUrl = url.startsWith('/db/') ? url : '/db/users' + url;
            return request({ method, url: realUrl, headers: { 'Conn-Id': this.connId }, [method === 'get' ? 'params' : 'data']: data });
        },

        rowStyleHelper({ row }) { return row.granted ? { 'background-color': '#fdfdfd' } : {}; },
        sysRowStyleHelper({ row }) {
            if (row._isAllRow) return { 'background-color': '#f2f6fc' };
            if (row.granted) return { 'background-color': '#fdfdfd' };
            return {};
        },
        showError(msg) { this.$alert((msg || '操作失败').replace(/^.*nested exception is .*: /, '').replace(/^\[-\d+\]:\s*/, ''), '提示', { type: 'error' }); },
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

        async loadData() {
            this.loading = true;
            try {
                // 1. 获取用户基本信息
                const res = await this.request('get', '/list');
                if (res.data.code === 200) {
                    this.tablespaces = res.data.data.tablespaces;
                    this.userInfo = res.data.data.users.find(u => u.USERNAME === this.username) || {};
                }
                // 2. 加载模式列表 (Metadata)
                const schemaRes = await this.request('get', '/db/metadata/schemas');
                if (schemaRes.data.code === 200) {
                    this.schemaList = schemaRes.data.data || [];
                    if (!this.selectedSchema) this.selectedSchema = 'DMDB'; // 默认选中 DMDB
                }
                // 3. 加载权限详情
                await this.loadPrivileges();
                // 4. 加载表列表
                await this.loadTables();
            } catch (e) { this.$message.error('加载数据失败'); } finally { this.loading = false; }
        },

        async loadPrivileges() {
            const res = await this.request('get', '/privileges', { username: this.username });
            if (res.data.code === 200) {
                const d = res.data.data;
                const isYes = this.isOptionTrue;

                this.roleList = (d.allRoles || []).map(rName => {
                    const assigned = (d.roles || []).find(ur => ur.GRANTED_ROLE === rName);
                    return { name: rName, granted: !!assigned, admin: assigned && isYes(assigned.ADMIN_OPTION), _origGranted: !!assigned };
                });
                this.roleChanged = false;

                this.sysList = (d.allSysPrivs || []).map(pName => {
                    const assigned = (d.sysPrivs || []).find(sp => sp.PRIVILEGE === pName);
                    return { name: pName, granted: !!assigned, admin: assigned && isYes(assigned.ADMIN_OPTION), _origGranted: !!assigned };
                });
                this.sysChanged = false;
                this.updateSysHeaderState();

                // 保存原始对象权限数据，供表格选择时匹配
                this.existingObjPrivs = d.objPrivs || [];
                // 如果当前已选中表，重新渲染选中状态
                if (this.currentTable) this.handleTableSelect(this.currentTable);
            }
        },

        // --- 对象权限相关逻辑 ---
        async loadTables() {
            if (!this.selectedSchema) return;
            this.loadingTables = true;
            try {
                const res = await this.request('get', '/db/metadata/tables', { schema: this.selectedSchema });
                this.tableList = (res.data.data || []).map(t => t.TABLE_NAME);
                this.currentTable = ''; // 切换 schema 后清空选中
                this.currentObjPerms.forEach(p => { p.granted = false; p.admin = false; });
            } catch (e) { /* ignore */ } finally { this.loadingTables = false; }
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
            this.updateObjGrantHeaderState();
            this.updateObjAdminHeaderState();
        },

        // 对象权限全选联动
        handleObjGrantAllChange(val) {
            this.currentObjPerms.forEach(item => { item.granted = val; if (!val) item.admin = false; });
            this.updateObjAdminHeaderState();
        },
        handleObjAdminAllChange(val) {
            this.currentObjPerms.forEach(item => { item.admin = val; if (val) item.granted = true; });
            this.updateObjGrantHeaderState();
        },
        handleObjRowChange(row, type) {
            if (type === 'grant') { if (!row.granted) row.admin = false; }
            if (type === 'admin') { if (row.admin) row.granted = true; }
            this.updateObjGrantHeaderState();
            this.updateObjAdminHeaderState();
        },
        updateObjGrantHeaderState() { this.objGrantAll = this.currentObjPerms.every(i => i.granted); },
        updateObjAdminHeaderState() { this.objAdminAll = this.currentObjPerms.every(i => i.admin); },

        // --- Sys 权限相关 ---
        handleSysSort({ prop, order }) { this.sysSort = { prop, order }; },
        updateSysHeaderState() {
            const realList = this.filteredSysList.filter(row => !row._isAllRow);
            if (realList.length === 0) {
                this.sysHeader = { allGranted: false, indeterminateGranted: false, allAdmin: false, indeterminateAdmin: false };
                return;
            }
            const grantedCount = realList.filter(i => i.granted).length;
            this.sysHeader.allGranted = grantedCount === realList.length;
            this.sysHeader.indeterminateGranted = grantedCount > 0 && grantedCount < realList.length;
            const adminCount = realList.filter(i => i.admin).length;
            this.sysHeader.allAdmin = adminCount === realList.length;
            this.sysHeader.indeterminateAdmin = adminCount > 0 && adminCount < realList.length;
        },
        handleCheckAllSysGranted(val) {
            this.sysList.forEach(row => {
                const inView = this.filteredSysList.find(r => r.name === row.name);
                if (inView && !inView._isAllRow) { row.granted = val; if (!val) row.admin = false; }
            });
            this.markSysChanged();
        },
        handleCheckAllSysAdmin(val) {
            this.sysList.forEach(row => {
                const inView = this.filteredSysList.find(r => r.name === row.name);
                if (inView && !inView._isAllRow) { row.admin = val; if (val) row.granted = true; }
            });
            this.markSysChanged();
        },
        onSysGrantedChange(row) { if (!row.granted) row.admin = false; this.markSysChanged(); },
        onSysAdminChange(row) { if (row.admin) row.granted = true; this.markSysChanged(); },
        markSysChanged() {
            this.sysChanged = this.sysList.some(s => s.granted !== s._origGranted || (s.granted && s.admin !== s._origAdmin));
            this.updateSysHeaderState();
        },

        // --- Role 相关 ---
        onRoleGrantedChange(row) { if (!row.granted) row.admin = false; this.markRoleChanged(); },
        onRoleAdminChange(row) { if (row.admin) row.granted = true; this.markRoleChanged(); },
        markRoleChanged() { this.roleChanged = this.roleList.some(r => r.granted !== r._origGranted || (r.granted && r.admin !== r._origAdmin)); },

        // --- 保存 ---
        async saveRoles() {
            const changes = this.roleList.filter(r => r.granted !== r._origGranted || (r.granted && r.admin !== r._origAdmin));
            if (changes.length === 0) return;
            this.savingRoles = true;
            try {
                for (const item of changes) {
                    if (item.granted) {
                        if (item._origGranted) await this.doGrantRevokePromise('REVOKE', 'ROLE', item.name);
                        await this.doGrantRevokePromise('GRANT', 'ROLE', item.name, null, item.admin);
                    } else { await this.doGrantRevokePromise('REVOKE', 'ROLE', item.name); }
                }
                this.$message.success("保存成功"); await this.loadPrivileges();
            } catch (e) { this.showError(e.message); await this.loadPrivileges(); } finally { this.savingRoles = false; }
        },
        async saveSysPrivs() {
            const changes = this.sysList.filter(s => s.granted !== s._origGranted || (s.granted && s.admin !== s._origAdmin));
            if (changes.length === 0) return;
            this.savingSys = true;
            try {
                for (const item of changes) {
                    if (item.granted) {
                        if (item._origGranted) await this.doGrantRevokePromise('REVOKE', 'SYS', item.name);
                        await this.doGrantRevokePromise('GRANT', 'SYS', item.name, null, item.admin);
                    } else { await this.doGrantRevokePromise('REVOKE', 'SYS', item.name); }
                }
                this.$message.success("保存成功"); await this.loadPrivileges();
            } catch (e) { this.showError(e.message); await this.loadPrivileges(); } finally { this.savingSys = false; }
        },
        async saveObjPrivs() {
            if (!this.currentTable) return;
            this.savingObj = true;
            try {
                // 计算当前表的变更
                const objectName = `"${this.selectedSchema}"."${this.currentTable}"`;
                for (const row of this.currentObjPerms) {
                    // 查找原始状态
                    const found = this.existingObjPrivs.find(p =>
                        this.getMapValue(p, 'OWNER') === this.selectedSchema &&
                        this.getMapValue(p, 'TABLE_NAME') === this.currentTable &&
                        this.getMapValue(p, 'PRIVILEGE').toUpperCase() === row.name.toUpperCase()
                    );
                    const origGranted = !!found;
                    const origAdmin = !!(found && this.isOptionTrue(this.getMapValue(found, 'GRANTABLE')));

                    // 判断是否变更
                    if (row.granted !== origGranted || (row.granted && row.admin !== origAdmin)) {
                        // 逻辑：先回收，再根据需要授予
                        if (origGranted) {
                            await this.doGrantRevokePromise('REVOKE', 'OBJ', row.name, objectName);
                        }
                        if (row.granted) {
                            await this.doGrantRevokePromise('GRANT', 'OBJ', row.name, objectName, row.admin);
                        }
                    }
                }
                this.$message.success("应用成功");
                await this.loadPrivileges(); // 刷新数据
            } catch (e) { this.showError(e.message); } finally { this.savingObj = false; }
        },

        doGrantRevokePromise(action, type, item, obj, admin) {
            return new Promise((resolve, reject) => {
                // 注意：这里调用的是 users 的 grant-revoke 接口
                this.request('post', '/grant-revoke', { username: this.username, action, type, privilege: item, objectName: obj, adminOption: admin })
                    .then(res => res.data.code === 200 ? resolve() : reject(new Error(res.data.msg))).catch(e => reject(e));
            });
        },

        // --- 杂项 ---
        async toggleLock() {
            const t = this.userInfo.ACCOUNT_STATUS === 'OPEN' ? 'LOCK' : 'UNLOCK';
            try { await this.request('post', '/alter', { username: this.username, type: t }); this.$message.success("操作成功"); this.userInfo.ACCOUNT_STATUS = (t === 'UNLOCK' ? 'OPEN' : 'LOCKED'); } catch (e) { this.showError(e.message); }
        },
        async submitPwd() {
            if (!this.newPassword) return;
            this.submitting = true;
            try { await this.request('post', '/alter', { username: this.username, type: 'PWD', password: this.newPassword }); this.$message.success("修改成功"); this.pwdVisible = false; } catch (e) { this.showError(e.message); } finally { this.submitting = false; }
        },
        openQuota() { this.quotaForm.tablespace = this.userInfo.DEFAULT_TABLESPACE || (this.tablespaces.length > 0 ? this.tablespaces[0] : ''); this.quotaForm.quota = 'UNLIMITED'; this.quotaVisible = true; },
        async submitQuota() {
            this.submitting = true;
            try { await this.request('post', '/alter', { username: this.username, type: 'QUOTA', ...this.quotaForm }); this.$message.success("设置成功"); this.quotaVisible = false; } catch (e) { this.showError(e.message); } finally { this.submitting = false; }
        },
        handleDelete() {
            this.$confirm(`确定删除用户 ${this.username} 吗？`, '警告', { type: 'warning' }).then(async () => {
                try { await this.request('delete', '/delete', { username: this.username }); this.$message.success("删除成功"); this.$emit('user-deleted', this.username); } catch (e) { this.showError(e.message); }
            }).catch(() => { });
        }
    }
}
</script>

<style scoped>
.user-detail-container {
    height: 100%;
    background: #fff;
    display: flex;
    flex-direction: column;
}

.user-banner {
    padding: 15px 20px;
    background: #f8f9fa;
    border-bottom: 1px solid #ebeef5;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.banner-left {
    display: flex;
    align-items: center;
}

.avatar-box {
    width: 50px;
    height: 50px;
    background: #ecf5ff;
    border-radius: 8px;
    display: flex;
    justify-content: center;
    align-items: center;
    font-size: 24px;
    color: #409EFF;
    margin-right: 15px;
}

.username {
    margin: 0 0 5px 0;
    font-size: 20px;
    color: #303133;
    font-weight: 600;
}

.meta {
    display: flex;
    align-items: center;
    gap: 10px;
}

.meta-item {
    font-size: 12px;
    color: #909399;
    display: flex;
    align-items: center;
    gap: 4px;
}

.tabs-area {
    flex: 1;
    padding: 15px;
    overflow: hidden;
    display: flex;
    flex-direction: column;
}

.tab-content-pad {
    padding: 10px;
}

.toolbar {
    padding: 10px 0;
    border-bottom: 1px solid #ebeef5;
    margin-bottom: 10px;
    display: flex;
    align-items: center;
    justify-content: space-between;
}

/* 对象权限分栏布局 */
.obj-layout {
    height: 100%;
    display: flex;
}

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

.label {
    font-size: 13px;
    color: #909399;
    margin-right: 8px;
}

.priv-name-tag {
    background-color: #f0f9eb;
    border-color: #e1f3d8;
    color: #67c23a;
    font-weight: 500;
    border-radius: 4px;
}

/* 自定义表头样式 */
.custom-header-cell {
    display: flex;
    flex-direction: column;
    justify-content: center;
    line-height: 1.2;
}

.custom-header-cell.center {
    align-items: center;
}

.header-top {
    font-weight: bold;
    color: #909399;
    padding-bottom: 4px;
    border-bottom: 1px dashed #DCDFE6;
    margin-bottom: 4px;
    width: 100%;
    text-align: center;
}

.custom-header-cell:not(.center) .header-top {
    text-align: left;
}

.header-bottom {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 20px;
}

.custom-header-cell:not(.center) .header-bottom {
    justify-content: flex-start;
    color: #909399;
    font-weight: normal;
    font-size: 12px;
}

::v-deep .el-tabs__content {
    flex: 1;
    overflow-y: auto;
    padding: 15px;
}

/* 调整表头高度 */
::v-deep .el-table__header tr,
::v-deep .el-table__header th {
    height: 60px !important;
    padding: 0;
}

::v-deep .el-checkbox__inner {
    width: 16px;
    height: 16px;
}

::v-deep .el-checkbox__inner::after {
    left: 5px;
    top: 2px;
}
</style>