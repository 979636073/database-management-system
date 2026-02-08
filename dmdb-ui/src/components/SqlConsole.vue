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
                    <el-button type="primary" size="small" icon="el-icon-video-play" @click="handleExecute"
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
            <SqlEditor ref="sqlEditor" v-model="sqlContent" @execute="handleExecute" />
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
                            <el-table :data="queryResults[0].data" border height="100%" size="mini" stripe>
                                <el-table-column type="index" width="50" label="#" align="center"
                                    fixed></el-table-column>
                                <el-table-column v-for="col in getColumns(queryResults[0].data)" :key="col" :prop="col"
                                    :label="col" min-width="120" show-overflow-tooltip>
                                </el-table-column>
                            </el-table>
                        </div>

                        <div v-if="queryResults.length > 1" class="multi-result-container">
                            <el-tabs v-model="activeSubTab" type="card" class="sub-result-tabs">
                                <el-tab-pane v-for="(res, idx) in queryResults" :key="idx"
                                    :label="`结果 ${res.index} (${res.rows}行)`" :name="String(idx)">
                                    <div style="height: 100%">
                                        <el-table :data="res.data" border height="100%" size="mini" stripe>
                                            <el-table-column type="index" width="50" label="#" align="center"
                                                fixed></el-table-column>
                                            <el-table-column v-for="col in getColumns(res.data)" :key="col" :prop="col"
                                                :label="col" min-width="120" show-overflow-tooltip>
                                            </el-table-column>
                                        </el-table>
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
                            <span v-if="log.sqlIndex"
                                style="font-weight:bold; margin-right:5px;">[语句{{ log.sqlIndex }}]</span>
                            {{ log.msg }}
                        </div>
                    </div>
                </el-tab-pane>
            </el-tabs>
        </div>
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

            // 结果面板控制
            activeResultTab: 'data',
            activeSubTab: '0',

            // 存放查询结果集: [{ index: 1, data: [...], rows: 10 }, ...]
            queryResults: [],

            errorMsg: '',
            execTime: 0,
            logs: [],

            userList: [],
            currentUser: '',

            // 事务脏状态
            isDirty: false
        };
    },
    created() {
        this.initConsole();
    },
    methods: {
        getColumns(data) {
            if (data && data.length > 0) return Object.keys(data[0]);
            return [];
        },

        async initConsole() {
            if (this.initSql) {
                this.sqlContent = this.initSql;
            } else {
                this.sqlContent = `-- 连接: ${this.connName}\n-- 说明: 支持多条语句执行(按分号分隔)\n-- DML语句需点击【提交】生效\n\nSELECT * FROM v$instance;\nSELECT * FROM v$database;`;
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
            } catch (e) {
                console.error("检查事务状态失败", e);
            }
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

        // 执行 SQL (核心逻辑)
        async handleExecute() {
            const sqlText = this.$refs.sqlEditor.getSelectionOrAll();
            if (!sqlText || !sqlText.trim()) return this.$message.warning('请先输入SQL语句');

            // 按分号分割 SQL
            const statements = sqlText.split(/;\s*(\n|$)/).map(s => s.trim()).filter(s => s.length > 0);

            this.executing = true;
            this.errorMsg = '';
            this.queryResults = []; // 清空旧结果
            this.activeSubTab = '0';
            const startTime = Date.now();

            try {
                if (statements.length > 1) {
                    // === 多条执行 (脚本模式) ===
                    this.appendLog(`开始执行脚本 (共 ${statements.length} 条语句)...`, 'info');

                    const res = await request.post('/db/execute/script', {
                        sqls: statements,
                        manualCommit: true
                    }, { headers: { 'Conn-Id': this.connId } });

                    const duration = Date.now() - startTime;
                    this.execTime = duration;

                    if (res.data.code === 200) {
                        const data = res.data.data; // { results: [...], dirty: bool }

                        // 解析返回的列表
                        if (data.results) {
                            data.results.forEach(item => {
                                const isQuery = item.type === 'QUERY';
                                const isError = item.type === 'ERROR';
                                // 构造简短消息
                                let msg = item.msg;
                                if (isQuery) msg = `查询成功 (${item.rows}行)`;

                                // 记录日志
                                this.appendLog(msg, isError ? 'error' : 'success', item.index);

                                // 收集查询结果
                                if (isQuery && item.success) {
                                    this.queryResults.push(item);
                                }
                            });
                        }

                        if (data.dirty !== undefined) this.isDirty = data.dirty;
                        this.appendLog(`脚本执行完成, 耗时 ${duration}ms`, 'info');

                        // 自动切 Tab
                        if (this.queryResults.length > 0) {
                            this.activeResultTab = 'data';
                        } else {
                            this.activeResultTab = 'message';
                        }
                    } else {
                        this.errorMsg = res.data.msg;
                        this.appendLog(`脚本执行失败: ${res.data.msg}`, 'error');
                        this.activeResultTab = 'message';
                    }

                } else {
                    // === 单条执行 ===
                    const sql = statements.length === 1 ? statements[0] : sqlText;
                    this.appendLog(`执行: ${sql.substring(0, 50)}...`, 'info');

                    const res = await request.post('/db/execute', {
                        sql: sql,
                        manualCommit: true
                    }, { headers: { 'Conn-Id': this.connId } });

                    const duration = Date.now() - startTime;
                    this.execTime = duration;

                    if (res.data.code === 200) {
                        const data = res.data.data;
                        if (Array.isArray(data)) {
                            // 查询成功
                            this.queryResults = [{ index: 1, data: data, rows: data.length }];
                            this.activeResultTab = 'data';
                            this.appendLog(`查询成功, 返回 ${data.length} 行`, 'success');
                        } else {
                            // DML/DDL 成功
                            if (data.dirty !== undefined) this.isDirty = data.dirty;
                            this.appendLog(data.msg, 'success');
                            this.activeResultTab = 'message';
                        }
                    } else {
                        this.errorMsg = res.data.msg;
                        this.appendLog(`执行失败: ${res.data.msg}`, 'error');
                        this.activeResultTab = 'message';
                    }
                }
            } catch (e) {
                this.errorMsg = '请求异常: ' + e.message;
                this.appendLog(`请求异常: ${e.message}`, 'error');
                this.activeResultTab = 'message';
            } finally {
                this.executing = false;
            }
        },

        // 事务提交/回滚
        async executeTransactionCommand(sql, successMsg) {
            this.submitting = true;
            this.appendLog(`执行: ${sql}`, 'info');
            try {
                const res = await request.post('/db/execute', { sql: sql, manualCommit: true }, { headers: { 'Conn-Id': this.connId } });
                if (res.data.code === 200) {
                    if (res.data.data && res.data.data.dirty !== undefined) this.isDirty = res.data.data.dirty;
                    const msg = res.data.data.msg || successMsg;
                    if (msg.includes("没有需要")) {
                        this.appendLog(msg, 'info');
                        this.$message.info(msg);
                    } else {
                        this.appendLog(msg, 'success');
                        this.$message.success(msg);
                    }
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

/* 强制让 Tabs 内容区撑满 */
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
}

.multi-result-container {
    height: 100%;
    display: flex;
    flex-direction: column;
}

/* 子Tabs样式微调 */
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
</style>