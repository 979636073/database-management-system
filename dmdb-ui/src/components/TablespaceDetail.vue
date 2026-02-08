<template>
    <div class="ts-container" v-loading="loading">
        <div class="ts-header">
            <div class="header-left">
                <i class="el-icon-folder-opened ts-icon"></i>
                <span class="ts-name">{{ name }}</span>
                <el-tag size="small" :type="status === 'ONLINE' ? 'success' : 'danger'" style="margin-left: 10px">{{
                    status }}</el-tag>
            </div>
            <div class="header-right">
                <el-button icon="el-icon-refresh" size="small" @click="loadData">刷新</el-button>
            </div>
        </div>

        <div class="ts-content">
            <h4 class="section-title">数据文件 (Data Files)</h4>
            <el-table :data="datafiles" border stripe size="small" style="width: 100%">
                <el-table-column prop="FILE_ID" label="ID" width="60" align="center"></el-table-column>
                <el-table-column prop="FILE_NAME" label="文件路径" min-width="250" show-overflow-tooltip></el-table-column>
                <el-table-column prop="SIZE_MB" label="当前大小(MB)" width="120" align="right"></el-table-column>

                <el-table-column label="自动扩展" width="100" align="center">
                    <template slot-scope="scope">
                        <i v-if="scope.row.AUTOEXTENSIBLE === 'YES'" class="el-icon-check"
                            style="color: #67C23A; font-weight: bold; font-size: 16px;"></i>
                        <i v-else class="el-icon-close" style="color: #909399; font-size: 16px;"></i>
                    </template>
                </el-table-column>
                <el-table-column prop="NEXT_SIZE_MB" label="每次扩充(MB)" width="120" align="right">
                    <template slot-scope="scope">{{ scope.row.AUTOEXTENSIBLE === 'YES' ? scope.row.NEXT_SIZE_MB : '-'
                        }}</template>
                </el-table-column>
                <el-table-column prop="MAX_SIZE_MB" label="上限(MB)" width="120" align="right">
                    <template slot-scope="scope">
                        {{ scope.row.AUTOEXTENSIBLE === 'YES' ? (scope.row.MAX_SIZE_MB > 30000000 ? '无限制' :
                            scope.row.MAX_SIZE_MB) : '-' }}
                    </template>
                </el-table-column>

                <el-table-column label="操作" width="100" align="center">
                    <template slot-scope="scope">
                        <el-button type="text" icon="el-icon-edit" size="mini"
                            @click="openEditDialog(scope.row)">修改</el-button>
                    </template>
                </el-table-column>
            </el-table>

            <div class="alert-box">
                <el-alert title="提示：表空间管理属于高危操作，增加数据文件或修改大小时请谨慎。" type="info" :closable="false" show-icon></el-alert>
            </div>
        </div>

        <el-dialog title="修改数据文件属性" :visible.sync="editVisible" width="500px" append-to-body
            :close-on-click-modal="false">
            <el-form :model="editForm" label-width="120px" size="small">
                <el-form-item label="文件路径">
                    <el-input v-model="editForm.filePath" disabled title="数据文件路径不可修改"></el-input>
                </el-form-item>

                <el-form-item label="自动扩充">
                    <el-switch v-model="editForm.autoExtend" active-text="开启" inactive-text="关闭"
                        @change="handleAutoExtendChange"></el-switch>
                </el-form-item>

                <el-form-item label="扩充尺寸(MB)">
                    <el-input-number v-model="editForm.nextSize" :min="0" :max="2048" :disabled="!editForm.autoExtend"
                        style="width: 100%"></el-input-number>
                    <div style="font-size: 12px; color: #909399; line-height: 1.5;">范围: 0 ~ 2048 MB</div>
                </el-form-item>

                <el-form-item label="扩充上限(MB)">
                    <el-input-number v-model="editForm.maxSize" :min="0" :max="16777215"
                        :disabled="!editForm.autoExtend" style="width: 100%"></el-input-number>
                    <div style="font-size: 12px; color: #909399; line-height: 1.5;">0 表示无限制 (UNLIMITED)，最大 16777215
                    </div>
                </el-form-item>
            </el-form>
            <div slot="footer">
                <el-button @click="editVisible = false" size="small">取消</el-button>
                <el-button type="primary" @click="submitEdit" size="small" :loading="submitting">保存</el-button>
            </div>
        </el-dialog>
    </div>
</template>

<script>
import request from '@/utils/request';

export default {
    name: 'TablespaceDetail',
    props: ['connId', 'name'],
    data() {
        return {
            loading: false,
            datafiles: [],
            status: 'ONLINE',
            editVisible: false,
            submitting: false,
            editForm: {
                filePath: '',
                autoExtend: false,
                nextSize: 0,
                maxSize: 0
            }
        };
    },
    watch: {
        name: { immediate: true, handler(val) { if (val) this.loadData(); } }
        // [修改] 移除 editForm.autoExtend 的 watch，防止回显时覆盖数据
    },
    methods: {
        async loadData() {
            this.loading = true;
            try {
                const res = await request.get('/db/tablespace/files', {
                    headers: { 'Conn-Id': this.connId },
                    params: { name: this.name }
                });
                if (res.data.code === 200) {
                    this.datafiles = res.data.data || [];
                    this.status = this.datafiles.length > 0 && this.datafiles[0].STATUS === 'AVAILABLE' ? 'ONLINE' : 'OFFLINE';
                }
            } catch (e) {
                this.$message.error("加载数据文件失败");
            } finally {
                this.loading = false;
            }
        },

        // [新增] 处理开关切换逻辑：仅在用户手动点击时触发
      handleAutoExtendChange(val) {
        if (!val) {
            // 关闭时置 0
            this.editForm.nextSize = 0;
            this.editForm.maxSize = 0;
        } else {
            // [修改] 开启时，按照需求默认为 0，由用户填写 (或后端处理默认值)
            // 原代码: if (this.editForm.nextSize === 0) this.editForm.nextSize = 10;
            // 新代码: 保持为 0，不做自动填充
            if (this.editForm.nextSize === 0) this.editForm.nextSize = 0;
        }
    },

        openEditDialog(row) {
            this.editForm.filePath = row.FILE_NAME;
            this.editForm.autoExtend = row.AUTOEXTENSIBLE === 'YES';
            // 回显逻辑：直接取值，不再受 watcher 干扰
            this.editForm.nextSize = row.NEXT_SIZE_MB || 0;
            this.editForm.maxSize = (row.MAX_SIZE_MB && row.MAX_SIZE_MB > 30000000) ? 0 : (row.MAX_SIZE_MB || 0);

            this.editVisible = true;
        },

        async submitEdit() {
            this.submitting = true;
            try {
                const payload = {
                    tablespaceName: this.name,
                    ...this.editForm
                };
                const res = await request.post('/db/tablespace/alter', payload, {
                    headers: { 'Conn-Id': this.connId }
                });
                if (res.data.code === 200) {
                    this.$message.success("修改成功");
                    this.editVisible = false;
                    this.loadData();
                } else {
                    this.$alert(res.data.msg, '修改失败', { type: 'error' });
                }
            } catch (e) {
                this.$message.error("请求异常");
            } finally {
                this.submitting = false;
            }
        }
    }
};
</script>

<style scoped>
/* 样式保持不变 */
.ts-container {
    height: 100%;
    background: #fff;
    display: flex;
    flex-direction: column;
}

.ts-header {
    padding: 15px 20px;
    border-bottom: 1px solid #ebeef5;
    background: #f8f9fa;
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.header-left {
    display: flex;
    align-items: center;
}

.ts-icon {
    font-size: 24px;
    color: #409EFF;
    margin-right: 10px;
}

.ts-name {
    font-size: 18px;
    font-weight: bold;
    color: #303133;
}

.ts-content {
    flex: 1;
    padding: 20px;
    overflow-y: auto;
}

.section-title {
    margin: 0 0 15px 0;
    padding-left: 10px;
    border-left: 4px solid #409EFF;
    color: #606266;
    font-size: 14px;
}

.alert-box {
    margin-top: 20px;
}
</style>