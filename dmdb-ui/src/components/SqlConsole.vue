<template>
    <div class="sql-console">
        <div class="toolbar">
            <div class="toolbar-left">
                <el-select v-model="currentUser" placeholder="当前模式" size="small" filterable @change="handleUserChange"
                    style="width: 150px; margin-right: 10px;">
                    <el-option v-for="user in userList" :key="user" :label="user" :value="user">
                        <span style="float: left">{{ user }}</span>
                        <span style="float: right; color: #8492a6; font-size: 13px"><i class="el-icon-user"></i></span>
                    </el-option>
                </el-select>

                <el-button-group>
                    <el-button type="primary" size="small" icon="el-icon-video-play" @click="handleExecuteClick"
                        :loading="executing" title="执行 (F9)">执行</el-button>
                    <el-button size="small" icon="el-icon-magic-stick" @click="handleFormat"
                        title="格式化SQL">格式化</el-button>
                </el-button-group>

                <el-button-group style="margin-left: 10px;">
                    <el-button type="success" size="small" icon="el-icon-check" @click="handleCommit"
                        :loading="submitting" :disabled="!isDirty" title="提交事务 (Commit)">
                        提交
                    </el-button>
                    <el-button type="warning" size="small" icon="el-icon-close" @click="handleRollback"
                        :loading="submitting" :disabled="!isDirty" title="回滚事务 (Rollback)">
                        回滚
                    </el-button>
                </el-button-group>

                <el-button-group style="margin-left: 10px;">
                    <el-button size="small" icon="el-icon-document-add" @click="handleSave" title="保存">保存</el-button>
                    <el-button size="small" icon="el-icon-delete" @click="handleClear" title="清空">清空</el-button>
                </el-button-group>
            </div>

            <span class="status-info" v-if="execTime">
                耗时: {{ execTime }}ms
            </span>
        </div>

        <div class="editor-area">
            <SqlEditor ref="sqlEditor" v-model="sqlContent" @execute="handleExecuteClick" />
        </div>

        <div class="result-area">
            <el-tabs v-model="activeResultTab" type="border-card" class="result-tabs">

                <el-tab-pane label="数据结果" name="data">
                    <div class="table-wrapper" v-loading="executing">

                        <el-empty v-if="queryResults.length === 0 && !errorMsg" description="无查询结果"
                            :image-size="60"></el-empty>

                        <el-alert v-if="errorMsg" title="执行出错" type="error" :description="errorMsg" show-icon
                            :closable="false" style="margin-bottom: 5px;"></el-alert>

                        <div v-if="queryResults.length === 1" class="single-result-container">
                            <div class="table-body">
                                <el-table :data="pagedData" border height="100%" size="mini" stripe>
                                    <el-table-column type="index" width="50" label="#" align="center" fixed
                                        :index="(index) => (currentPage - 1) * pageSize + index + 1"></el-table-column>

                                    <el-table-column v-for="col in getColumns(queryResults[0].data)" :key="col"
                                        :prop="col" :label="col" min-width="120" show-overflow-tooltip>
                                        <template slot-scope="scope">
                                            <div v-if="isLobRef(scope.row[col])">
                                                <el-tag size="mini" type="info" style="margin-right:5px; cursor:help;"
                                                    title="包含ROWID可操作">LOB</el-tag>
                                                <el-button type="text" size="mini" icon="el-icon-view"
                                                    @click="previewLobRef(scope.row[col])" title="预览"></el-button>
                                                <el-button type="text" size="mini" icon="el-icon-download"
                                                    @click="downloadLobRef(scope.row[col])" title="下载"></el-button>
                                                <el-button type="text" size="mini" icon="el-icon-upload2"
                                                    @click="uploadLobRef(scope.row[col])" title="上传"></el-button>
                                            </div>
                                            <div v-else-if="isLobBase64(scope.row[col])">
                                                <el-tag size="mini" type="warning"
                                                    style="margin-right:5px; cursor:help;"
                                                    title="无ROWID，仅预览">Base64</el-tag>
                                                <el-button type="text" size="mini" icon="el-icon-view"
                                                    @click="previewBase64(scope.row[col])" title="预览"></el-button>
                                                <el-button type="text" size="mini" icon="el-icon-download"
                                                    @click="downloadBase64(scope.row[col])" title="下载"></el-button>
                                            </div>
                                            <div v-else-if="isLobTip(scope.row[col])">
                                                <el-tooltip :content="parseLobTip(scope.row[col]).hint" placement="top">
                                                    <el-tag type="danger" size="mini" style="cursor:help;">
                                                        <i class="el-icon-warning"></i> {{
                                                        parseLobTip(scope.row[col]).msg }}
                                                    </el-tag>
                                                </el-tooltip>
                                            </div>
                                            <span v-else>{{ scope.row[col] }}</span>
                                        </template>
                                    </el-table-column>
                                </el-table>
                            </div>

                            <div class="pagination-bar" v-if="isSelectQuery">
                                <span style="margin-right: 10px; font-size: 12px; color: #909399;">
                                    共 {{ totalRows }} 条 (服务端分页)
                                </span>
                                <el-pagination @size-change="handleSizeChange" @current-change="handleCurrentChange"
                                    :current-page="currentPage" :page-sizes="[50, 100, 500, 1000]" :page-size="pageSize"
                                    layout="sizes, prev, pager, next, jumper" :total="totalRows">
                                </el-pagination>
                            </div>
                            <div class="pagination-bar" v-else-if="queryResults[0].data.length > 0">
                                <span style="margin-right: 10px; font-size: 12px; color: #909399;">
                                    共 {{ queryResults[0].data.length }} 条 (已限制最大行数)
                                </span>
                                <el-pagination @size-change="handleSizeChange" @current-change="handleCurrentChange"
                                    :current-page="currentPage" :page-sizes="[20, 50, 100, 500]" :page-size="pageSize"
                                    layout="sizes, prev, pager, next, jumper" :total="queryResults[0].data.length">
                                </el-pagination>
                            </div>
                        </div>

                        <div v-if="queryResults.length > 1" class="multi-result-container">
                            <el-tabs v-model="activeSubTab" type="card" class="sub-result-tabs">
                                <el-tab-pane v-for="(res, idx) in queryResults" :key="idx"
                                    :label="`结果 ${res.index} (${res.rows}行)`" :name="String(idx)">

                                    <div style="height: 100%; display: flex; flex-direction: column;">
                                        <div class="table-body">
                                            <el-table :data="res.displayData || res.data.slice(0, res.pageSize)" border
                                                height="100%" size="mini" stripe>

                                                <el-table-column type="index" width="50" label="#" align="center" fixed
                                                    :index="(index) => (res.currentPage - 1) * res.pageSize + index + 1"></el-table-column>

                                                <el-table-column v-for="col in getColumns(res.data)" :key="col"
                                                    :prop="col" :label="col" min-width="120" show-overflow-tooltip>
                                                    <template slot-scope="scope">
                                                        <div v-if="isLobRef(scope.row[col])">
                                                            <el-tag size="mini" type="info">LOB</el-tag>
                                                            <el-button type="text" size="mini" icon="el-icon-view"
                                                                @click="previewLobRef(scope.row[col])"></el-button>
                                                            <el-button type="text" size="mini" icon="el-icon-download"
                                                                @click="downloadLobRef(scope.row[col])"></el-button>
                                                            <el-button type="text" size="mini" icon="el-icon-upload2"
                                                                @click="uploadLobRef(scope.row[col])"></el-button>
                                                        </div>
                                                        <div v-else-if="isLobBase64(scope.row[col])">
                                                            <el-tag size="mini" type="warning">B64</el-tag>
                                                            <el-button type="text" size="mini" icon="el-icon-view"
                                                                @click="previewBase64(scope.row[col])"></el-button>
                                                            <el-button type="text" size="mini" icon="el-icon-download"
                                                                @click="downloadBase64(scope.row[col])"></el-button>
                                                        </div>
                                                        <div v-else-if="isLobTip(scope.row[col])">
                                                            <el-tag type="danger" size="mini"><i
                                                                    class="el-icon-warning"></i> {{
                                                                parseLobTip(scope.row[col]).msg }}</el-tag>
                                                        </div>
                                                        <span v-else>{{ scope.row[col] }}</span>
                                                    </template>
                                                </el-table-column>
                                            </el-table>
                                        </div>

                                        <div class="pagination-bar" v-if="res.data.length > 0">
                                            <span style="margin-right: 10px; font-size: 12px; color: #909399;">
                                                <i class="el-icon-loading" v-if="res.loadingCount"></i>
                                                共 {{ res.totalRows }} 条 {{ res.totalRows > 5000 ? '(服务端分页)' : '(内存分页)'
                                                }}
                                            </span>
                                            <el-pagination @size-change="(val) => handleBatchSizeChange(val, res)"
                                                @current-change="(val) => handleBatchCurrentChange(val, res)"
                                                :current-page="res.currentPage" :page-sizes="[20, 50, 100, 500]"
                                                :page-size="res.pageSize" layout="sizes, prev, pager, next, jumper"
                                                :total="res.totalRows">
                                            </el-pagination>
                                        </div>
                                    </div>

                                </el-tab-pane>
                            </el-tabs>
                        </div>

                    </div>
                </el-tab-pane>

                <el-tab-pane label="执行日志" name="message">
                    <div class="message-log">
                        <div v-for="(log, index) in logs" :key="index" :class="['log-item', log.type]">
                            <span class="log-time">[{{ log.time }}]</span>
                            <span v-if="log.sqlIndex" style="font-weight:bold; margin-right:5px;">[语句{{ log.sqlIndex
                                }}]</span>
                            {{ log.msg }}
                        </div>
                    </div>
                </el-tab-pane>
            </el-tabs>
        </div>

        <el-dialog :title="previewTitle" :visible.sync="previewVisible" width="800px" append-to-body
            custom-class="preview-dialog">
            <div
                style="min-height: 200px; display: flex; justify-content: center; align-items: center; overflow: auto; max-height: 600px;">
                <img v-if="previewType === 'image'" :src="previewUrl"
                    style="max-width: 100%; box-shadow: 0 0 10px rgba(0,0,0,0.1);" />
                <pre v-else
                    style="width:100%; white-space: pre-wrap; font-family: Consolas; background: #f5f7fa; padding: 10px;">{{
            previewContent }}</pre>
            </div>
        </el-dialog>

        <input type="file" ref="consoleFileInput" style="display: none" @change="handleFileSelected" />
    </div>
</template>

<script>
import SqlEditor from './SqlEditor.vue';
import request from '@/utils/request';
import dayjs from 'dayjs';

export default {
    name: 'SqlConsole',
    components: { SqlEditor },
    props: {
        connId: { type: String, required: true },
        connName: { type: String, default: '' },
        initSql: { type: String, default: '' }
    },
    data() {
        return {
            sqlContent: '',
            executing: false,
            submitting: false,
            activeResultTab: 'data',
            activeSubTab: '0',
            queryResults: [],
            errorMsg: '',
            execTime: 0,
            logs: [],
            userList: [],
            currentUser: '',
            isDirty: false,
            previewVisible: false,
            previewType: 'text',
            previewUrl: '',
            previewContent: '',
            previewTitle: '预览',
            currentUploadMeta: null,

            // 单结果集分页状态
            currentPage: 1,
            pageSize: 50,
            totalRows: 0,
            currentBaseSql: '',
            isSelectQuery: false,
        };
    },
    computed: {
        // 单结果集分页数据
        pagedData() {
            if (this.isSelectQuery) return this.queryResults[0].data;
            if (this.queryResults.length === 1) {
                const allData = this.queryResults[0].data;
                const start = (this.currentPage - 1) * this.pageSize;
                const end = start + this.pageSize;
                return allData.slice(start, end);
            }
            return [];
        }
    },
    created() {
        this.initConsole();
    },
    methods: {
        // === 单结果集分页 ===
        handleSizeChange(val) {
            this.pageSize = val;
            this.currentPage = 1;
            if (this.isSelectQuery) this.doPagedQuery();
        },
        handleCurrentChange(val) {
            this.currentPage = val;
            if (this.isSelectQuery) this.doPagedQuery();
        },

        // === 【新增】多结果集独立分页逻辑 ===
        handleBatchSizeChange(val, res) {
            this.$set(res, 'pageSize', val);
            this.$set(res, 'currentPage', 1);
            this.doBatchPagedQuery(res);
        },
        handleBatchCurrentChange(val, res) {
            this.$set(res, 'currentPage', val);
            this.doBatchPagedQuery(res);
        },
        // 核心：处理批量结果的翻页
        async doBatchPagedQuery(res) {
            // 如果数据已经在内存中（<= 5000条的范围内），直接切片
            const start = (res.currentPage - 1) * res.pageSize;
            const end = start + res.pageSize;

            // 假设后端初始返回了5000条。如果请求的页在5000条内，直接使用内存数据
            if (end <= res.data.length) {
                this.$set(res, 'displayData', res.data.slice(start, end));
                return;
            }

            // 如果超出内存范围（需要服务端分页），则发起新请求
            const pagedSql = `SELECT * FROM (${res.sql}) LIMIT ${res.pageSize} OFFSET ${start}`;
            try {
                const apiRes = await request.post('/db/execute', {
                    sql: pagedSql, manualCommit: true
                }, { headers: { 'Conn-Id': this.connId } });

                if (apiRes.data.code === 200 && Array.isArray(apiRes.data.data)) {
                    // 更新当前显示的切片数据
                    this.$set(res, 'displayData', apiRes.data.data);
                }
            } catch (e) {
                this.$message.error("分页加载失败: " + e.message);
            }
        },

        async doPagedQuery() {
            if (!this.currentBaseSql) return;
            const offset = (this.currentPage - 1) * this.pageSize;
            const pagedSql = `SELECT * FROM (${this.currentBaseSql}) LIMIT ${this.pageSize} OFFSET ${offset}`;
            await this.executeInternal(pagedSql, false);
        },

        getColumns(data) {
            if (data && data.length > 0) return Object.keys(data[0]);
            return [];
        },
        async initConsole() {
            if (this.initSql) {
                this.sqlContent = this.initSql;
            } else {
                this.sqlContent = `-- 连接: ${this.connName}\n-- 说明: 支持分页查询，建议显式查询 ROWID\n\nSELECT * FROM v$instance;`;
            }
            this.fetchUserList();
            this.fetchCurrentUser();
            this.checkTransactionStatus();
        },
        async checkTransactionStatus() {
            try {
                const res = await request.get('/db/transaction/status', { headers: { 'Conn-Id': this.connId } });
                if (res.data.code === 200) {
                    this.isDirty = res.data.data;
                }
            } catch (e) { console.error("检查事务状态失败", e); }
        },
        async fetchUserList() {
            try {
                const res = await request.get("/db/users/list", { headers: { "Conn-Id": this.connId } });
                if (res.data.code === 200 && res.data.data && res.data.data.users) {
                    this.userList = res.data.data.users.map(u => u.USERNAME);
                }
            } catch (e) { console.error(e); }
        },
        async fetchCurrentUser() {
            try {
                const res = await request.post('/db/execute', {
                    sql: "SELECT USER FROM DUAL", manualCommit: true
                }, { headers: { 'Conn-Id': this.connId } });
                if (res.data.code === 200 && res.data.data && res.data.data.length > 0) {
                    const row = res.data.data[0];
                    this.currentUser = row['USER'] || row['user'] || Object.values(row)[0];
                }
            } catch (e) { console.error(e); }
        },
        async handleUserChange(val) {
            if (!val) return;
            const sql = `SET SCHEMA "${val}"`;
            this.appendLog(`切换模式: ${sql}`, 'info');
            try {
                const res = await request.post('/db/execute', { sql: sql, manualCommit: true }, { headers: { 'Conn-Id': this.connId } });
                if (res.data.code === 200) {
                    this.appendLog(`已切换到模式: ${val}`, 'success');
                    this.$message.success(`切换成功: ${val}`);
                } else {
                    this.appendLog(`切换失败: ${res.data.msg}`, 'error');
                    this.$message.error(res.data.msg);
                }
            } catch (e) { this.appendLog(`切换异常: ${e.message}`, 'error'); }
        },

        // 【入口】用户点击执行按钮
        async handleExecuteClick() {
            const sqlText = this.$refs.sqlEditor.getSelectionOrAll();
            if (!sqlText || !sqlText.trim()) return this.$message.warning('请先输入SQL语句');

            const statements = sqlText.split(/;\s*(\n|$)/).map(s => s.trim()).filter(s => s.length > 0);

            // 单条 SELECT 走专门的分页流程
            if (statements.length === 1 && statements[0].toUpperCase().startsWith('SELECT')) {
                let rawSql = statements[0];
                if (rawSql.endsWith(';')) rawSql = rawSql.substring(0, rawSql.length - 1);
                this.currentBaseSql = rawSql;

                this.isSelectQuery = true;
                this.currentPage = 1;

                this.fetchTotalCount(rawSql);
                await this.doPagedQuery();
            } else {
                // 脚本模式
                this.isSelectQuery = false;
                this.currentBaseSql = '';
                await this.executeInternal(sqlText, true);
            }
        },

        // 获取总数
        async fetchTotalCount(sql) {
            this.totalRows = 0;
            try {
                const countSql = `SELECT COUNT(*) FROM (${sql})`;
                const res = await request.post('/db/execute', {
                    sql: countSql, manualCommit: true
                }, { headers: { 'Conn-Id': this.connId } });

                if (res.data.code === 200 && res.data.data && res.data.data.length > 0) {
                    const row = res.data.data[0];
                    const val = Object.values(row)[0];
                    this.totalRows = parseInt(val);
                }
            } catch (e) {
                console.warn("无法获取总数", e);
                this.totalRows = 0;
            }
        },

        // 内部统一执行逻辑
        async executeInternal(sql, resetState = true) {
            this.executing = true;
            if (resetState) {
                this.errorMsg = '';
                this.queryResults = [];
                this.activeSubTab = '0';
            }
            const startTime = Date.now();

            try {
                const statements = sql.split(/;\s*(\n|$)/).map(s => s.trim()).filter(s => s.length > 0);

                if (statements.length > 1) {
                    // 脚本模式
                    const res = await request.post('/db/execute/script', {
                        sqls: statements, manualCommit: true
                    }, { headers: { 'Conn-Id': this.connId } });

                    this.processScriptResult(res, startTime);
                } else {
                    // 单条模式
                    const res = await request.post('/db/execute', {
                        sql: statements[0], manualCommit: true
                    }, { headers: { 'Conn-Id': this.connId } });

                    this.processSingleResult(res, startTime);
                }
            } catch (e) {
                this.errorMsg = '请求异常: ' + e.message;
                this.appendLog(`请求异常: ${e.message}`, 'error');
                this.activeResultTab = 'message';
            } finally {
                this.executing = false;
            }
        },

        processSingleResult(res, startTime) {
            const duration = Date.now() - startTime;
            this.execTime = duration;
            if (res.data.code === 200) {
                const data = res.data.data;
                if (Array.isArray(data)) {
                    this.queryResults = [{ index: 1, data: data, rows: data.length }];
                    this.activeResultTab = 'data';
                    if (!this.isSelectQuery) {
                        this.appendLog(`查询成功, 返回 ${data.length} 行`, 'success');
                    }
                } else {
                    if (data.dirty !== undefined) this.isDirty = data.dirty;
                    this.appendLog(data.msg, 'success');
                    this.activeResultTab = 'message';
                }
            } else {
                this.errorMsg = res.data.msg;
                this.appendLog(`执行失败: ${res.data.msg}`, 'error');
                this.activeResultTab = 'message';
            }
        },

        processScriptResult(res, startTime) {
            const duration = Date.now() - startTime;
            this.execTime = duration;
            if (res.data.code === 200) {
                const data = res.data.data;
                if (data.results) {
                    data.results.forEach(item => {
                        const isQuery = item.type === 'QUERY';
                        const isError = item.type === 'ERROR';
                        let msg = item.msg;
                        if (isQuery) msg = `查询成功 (${item.rows}行)`;
                        this.appendLog(msg, isError ? 'error' : 'success', item.index);

                        if (isQuery && item.success) {
                            // 【核心】初始化多结果集的分页状态
                            const resObj = {
                                ...item,
                                currentPage: 1,
                                pageSize: 50,
                                totalRows: item.rows, // 初始值（例如5000）
                                displayData: item.data.slice(0, 50), // 初始切片
                                loadingCount: true
                            };
                            this.queryResults.push(resObj);

                            // 【核心】异步加载真实总数
                            this.fetchTotalCountAsync(resObj);
                        }
                    });
                }
                if (data.dirty !== undefined) this.isDirty = data.dirty;
                this.appendLog(`脚本执行完成, 耗时 ${duration}ms`, 'info');
                if (this.queryResults.length > 0) this.activeResultTab = 'data';
                else this.activeResultTab = 'message';
            } else {
                this.errorMsg = res.data.msg;
                this.appendLog(`脚本执行失败: ${res.data.msg}`, 'error');
                this.activeResultTab = 'message';
            }
        },

        // 【新增】异步获取脚本结果的总行数
        async fetchTotalCountAsync(resObj) {
            try {
                const countSql = `SELECT COUNT(*) FROM (${resObj.sql})`;
                const res = await request.post('/db/execute', {
                    sql: countSql, manualCommit: true
                }, { headers: { 'Conn-Id': this.connId } });

                if (res.data.code === 200 && res.data.data && res.data.data.length > 0) {
                    const row = res.data.data[0];
                    const val = Object.values(row)[0];
                    this.$set(resObj, 'totalRows', parseInt(val));
                }
            } catch (e) {
                console.warn("Count failed for batch item", e);
            } finally {
                this.$set(resObj, 'loadingCount', false);
            }
        },

        async executeTransactionCommand(sql, successMsg) {
            this.submitting = true;
            this.appendLog(`执行: ${sql}`, 'info');
            try {
                const res = await request.post('/db/execute', { sql: sql, manualCommit: true }, { headers: { 'Conn-Id': this.connId } });
                if (res.data.code === 200) {
                    if (res.data.data && res.data.data.dirty !== undefined) this.isDirty = res.data.data.dirty;
                    const msg = res.data.data.msg || successMsg;
                    this.appendLog(msg, 'success');
                    this.$message.success(msg);
                    this.activeResultTab = 'message';
                } else {
                    this.appendLog(`操作失败: ${res.data.msg}`, 'error');
                    this.$message.error(res.data.msg);
                }
            } catch (e) {
                this.appendLog(`异常: ${e.message}`, 'error');
            } finally {
                this.submitting = false;
            }
        },
        async handleCommit() { await this.executeTransactionCommand('COMMIT', '事务已提交'); },
        async handleRollback() { await this.executeTransactionCommand('ROLLBACK', '事务已回滚'); },
        handleFormat() { this.$refs.sqlEditor.formatDocument(); },
        handleSave() { this.$message.info('保存功能开发中...'); },
        handleClear() {
            this.sqlContent = '';
            this.$refs.sqlEditor.editor.setValue('');
            this.queryResults = [];
            this.errorMsg = '';
            this.activeSubTab = '0';
        },
        appendLog(msg, type = 'info', sqlIndex = null) {
            const time = dayjs().format('HH:mm:ss');
            this.logs.unshift({ time, msg, type, sqlIndex });
        },
        isLobRef(val) { return typeof val === 'string' && val.startsWith('[LOB_REF:'); },
        parseLobRef(val) {
            const content = val.substring(9, val.length - 1);
            const parts = content.split(',');
            const meta = {};
            parts.forEach(p => { const [k, v] = p.split('='); meta[k] = v; });
            return meta;
        },
        async previewLobRef(val) {
            const meta = this.parseLobRef(val);
            const timestamp = new Date().getTime();
            const url = `/db/lob/preview?schema=${meta.schema}&tableName=${meta.table}&colName=${meta.col}&rowId=${meta.rowId}&download=false&connId=${this.connId}&_t=${timestamp}`;
            this.previewTitle = `预览 ${meta.col}`;
            const loading = this.$loading({ target: '.preview-dialog' });
            this.previewVisible = true;
            try {
                const res = await request({ url: url, method: 'get', responseType: 'blob', headers: { 'Conn-Id': this.connId } });
                const blob = res.data;
                if (meta.type === 'BINARY') { this.previewType = 'image'; this.previewUrl = URL.createObjectURL(blob); }
                else { this.previewType = 'text'; this.previewContent = await blob.text(); }
            } catch (e) { this.$message.error('预览加载失败'); } finally { loading.close(); }
        },
        async downloadLobRef(val) {
            const meta = this.parseLobRef(val);
            const url = `/db/lob/preview?schema=${meta.schema}&tableName=${meta.table}&colName=${meta.col}&rowId=${meta.rowId}&download=true&connId=${this.connId}`;
            try {
                const res = await request({ url: url, method: 'get', responseType: 'blob', headers: { 'Conn-Id': this.connId } });
                const blob = res.data;
                const link = document.createElement('a');
                link.href = URL.createObjectURL(blob);
                link.download = 'download.bin';
                link.click();
            } catch (e) { this.$message.error('下载失败'); }
        },
        uploadLobRef(val) {
            this.currentUploadMeta = this.parseLobRef(val);
            this.$refs.consoleFileInput.value = null;
            this.$refs.consoleFileInput.click();
        },
        async handleFileSelected(e) {
            const file = e.target.files[0];
            if (!file || !this.currentUploadMeta) return;
            const formData = new FormData();
            formData.append('file', file);
            formData.append('schema', this.currentUploadMeta.schema);
            formData.append('tableName', this.currentUploadMeta.table);
            formData.append('colName', this.currentUploadMeta.col);
            formData.append('rowId', this.currentUploadMeta.rowId);
            const loading = this.$loading({ text: '上传中...' });
            try {
                const res = await request({ url: '/db/lob/upload', method: 'post', headers: { 'Conn-Id': this.connId }, data: formData });
                if (res.data.code === 200) this.$message.success("上传成功");
                else this.$message.error(res.data.msg);
            } catch (err) { this.$message.error("上传失败"); } finally { loading.close(); }
        },
        isLobBase64(val) { return typeof val === 'string' && val.startsWith('[LOB_B64:'); },
        parseBase64(val) { return val.substring(9, val.length - 1).replace('data=', ''); },
        previewBase64(val) {
            const b64 = this.parseBase64(val);
            if (b64.startsWith('/9j/') || b64.startsWith('iVBOR')) { this.previewType = 'image'; this.previewUrl = 'data:image/jpeg;base64,' + b64; }
            else { this.previewType = 'text'; try { this.previewContent = atob(b64); } catch (e) { this.previewContent = "无法解码"; } }
            this.previewVisible = true;
        },
        downloadBase64(val) {
            const b64 = this.parseBase64(val);
            const link = document.createElement('a');
            link.href = 'data:application/octet-stream;base64,' + b64;
            link.download = 'download.bin';
            link.click();
        },
        isLobTip(val) { return typeof val === 'string' && val.startsWith('[LOB_TIP:'); },
        parseLobTip(val) {
            const content = val.substring(9, val.length - 1);
            const parts = content.split(',');
            const meta = {};
            parts.forEach(p => { const [k, v] = p.split('='); meta[k] = v; });
            return meta;
        }
    }
};
</script>

<style scoped>
.sql-console {
    display: flex;
    flex-direction: column;
    height: 100%;
    background: #fff;
}

.toolbar {
    padding: 8px 12px;
    background: #f5f7fa;
    border-bottom: 1px solid #e4e7ed;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.toolbar-left {
    display: flex;
    align-items: center;
}

.status-info {
    font-size: 12px;
    color: #909399;
    font-family: Consolas, monospace;
}

.editor-area {
    flex: 1;
    min-height: 200px;
    border-bottom: 5px solid #f0f2f5;
    position: relative;
}

.result-area {
    height: 45%;
    display: flex;
    flex-direction: column;
}

.result-tabs {
    height: 100%;
    display: flex;
    flex-direction: column;
    border: none;
    box-shadow: none;
}

::v-deep .el-tabs__content {
    flex: 1;
    padding: 0 !important;
    overflow: auto;
    height: 100%;
}

::v-deep .el-tab-pane {
    height: 100%;
    position: relative;
}

.table-wrapper {
    height: 100%;
    padding: 0;
    display: flex;
    flex-direction: column;
}

.single-result-container {
    height: 100%;
    display: flex;
    flex-direction: column;
}

.table-body {
    flex: 1;
    min-height: 0;
    overflow: hidden;
}

.multi-result-container {
    height: 100%;
    display: flex;
    flex-direction: column;
}

.sub-result-tabs {
    height: 100%;
    display: flex;
    flex-direction: column;
    border: none !important;
    box-shadow: none !important;
}

.sub-result-tabs ::v-deep .el-tabs__header {
    margin: 0;
    background: #fff;
    border-bottom: 1px solid #EBEEF5;
}

.sub-result-tabs ::v-deep .el-tabs__content {
    flex: 1;
    padding: 0;
    overflow: hidden;
    position: relative;
}

.message-log {
    padding: 10px;
    font-family: Consolas, monospace;
    font-size: 12px;
    line-height: 1.6;
}

.log-item {
    margin-bottom: 4px;
    border-bottom: 1px dashed #eee;
    padding-bottom: 2px;
    word-break: break-all;
}

.log-time {
    color: #909399;
    margin-right: 5px;
}

.log-item.success {
    color: #67C23A;
}

.log-item.error {
    color: #F56C6C;
}

.log-item.info {
    color: #606266;
}

.pagination-bar {
    padding: 8px 10px;
    background: #fdfdfd;
    border-top: 1px solid #EBEEF5;
    display: flex;
    justify-content: flex-end;
    align-items: center;
    flex-shrink: 0;
}
</style>