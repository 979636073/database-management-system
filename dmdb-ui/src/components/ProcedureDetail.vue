<template>
    <div class="proc-container" v-loading="loading">
        <div class="toolbar">
            <div class="left-info">
                <el-tag size="small" :type="type === 'PROCEDURE' ? 'primary' : 'warning'" effect="dark">{{ type
                    }}</el-tag>
                <span class="proc-name">{{ schema }}.{{ name }}</span>
                <el-tag v-if="isEditing" size="mini" type="danger" effect="plain"
                    style="margin-left: 10px">编辑模式</el-tag>
            </div>
            <div class="actions">
                <template v-if="!isEditing">
                    <el-button type="primary" size="small" icon="el-icon-edit" @click="startEdit">编辑</el-button>
                    <el-button size="small" icon="el-icon-refresh" @click="loadSource">刷新</el-button>
                </template>

                <template v-else>
                    <el-button type="success" size="small" icon="el-icon-video-play" @click="handleCompile"
                        :loading="compiling">
                        编译 / 保存
                    </el-button>
                    <el-button size="small" icon="el-icon-close" @click="cancelEdit">取消</el-button>
                </template>
            </div>
        </div>

        <div class="editor-wrapper">
            <div class="line-numbers" ref="lineNumbers">
                <div v-for="n in lineCount" :key="n" class="line-num">{{ n }}</div>
            </div>

            <el-input ref="editor" type="textarea" v-model="sourceCode" :rows="25" class="code-input"
                :class="{ 'is-readonly': !isEditing }" spellcheck="false" placeholder="PL/SQL Source Code..."
                :readonly="!isEditing" @input="handleInput"></el-input>
        </div>

        <div class="status-bar" v-if="lastMessage">
            <i :class="lastStatus === 'success' ? 'el-icon-success' : 'el-icon-error'"
                :style="{ color: lastStatus === 'success' ? '#67C23A' : '#F56C6C' }"></i>
            {{ lastMessage }}
        </div>
    </div>
</template>

<script>
import request from '@/utils/request';

export default {
    name: 'ProcedureDetail',
    props: ['connId', 'schema', 'name', 'type'],
    data() {
        return {
            loading: false,
            compiling: false,
            isEditing: false,
            sourceCode: '',
            lastMessage: '',
            lastStatus: ''
        };
    },
    computed: {
        // 计算总行数
        lineCount() {
            if (!this.sourceCode) return 1;
            return this.sourceCode.split('\n').length;
        }
    },
    watch: {
        name: {
            immediate: true,
            handler(val) {
                if (val) this.loadSource();
            }
        }
    },
    mounted() {
        this.initScrollSync();
    },
    methods: {
        // 初始化滚动同步
        initScrollSync() {
            // 找到 el-input 内部原本的 textarea 元素
            const textarea = this.$refs.editor.$el.querySelector('textarea');
            const lineNumbers = this.$refs.lineNumbers;

            if (textarea && lineNumbers) {
                textarea.addEventListener('scroll', () => {
                    // 将 textarea 的垂直滚动位置同步给行号栏
                    lineNumbers.scrollTop = textarea.scrollTop;
                });
            }
        },

        async loadSource() {
            this.loading = true;
            this.isEditing = false;
            try {
                const res = await request.get('/db/proc/detail', {
                    headers: { 'Conn-Id': this.connId },
                    params: { schema: this.schema, name: this.name, type: this.type }
                });
                if (res.data.code === 200) {
                    this.sourceCode = res.data.data.source || '';
                    this.lastMessage = '加载成功';
                    this.lastStatus = 'success';
                } else {
                    this.$message.error(res.data.msg);
                }
            } catch (e) {
                this.$message.error("加载失败");
            } finally {
                this.loading = false;
                // 数据加载后，重新同步一下滚动位置（回到顶部）
                this.$nextTick(() => {
                    const textarea = this.$refs.editor.$el.querySelector('textarea');
                    if (textarea) textarea.scrollTop = 0;
                });
            }
        },

        startEdit() {
            this.isEditing = true;
            this.lastMessage = '';
            this.$nextTick(() => {
                if (this.$refs.editor) this.$refs.editor.focus();
            });
        },

        cancelEdit() {
            this.$confirm('确定取消修改吗？未保存的内容将丢失。', '提示', {
                confirmButtonText: '确定',
                cancelButtonText: '再想想',
                type: 'warning'
            }).then(() => {
                this.loadSource();
            }).catch(() => { });
        },

        handleInput() {
            // 输入时如果有换行变化，Vue 的 computed 会自动更新 lineCount
        },

        async handleCompile() {
            if (!this.sourceCode) return;
            this.compiling = true;
            this.lastMessage = '';
            try {
                const res = await request.post('/db/proc/compile', { sql: this.sourceCode }, {
                    headers: { 'Conn-Id': this.connId }
                });

                if (res.data.code === 200) {
                    this.$message.success('编译成功');
                    this.lastMessage = `[${new Date().toLocaleTimeString()}] 编译成功`;
                    this.lastStatus = 'success';
                    this.isEditing = false;
                } else {
                    this.$alert(res.data.msg, '编译错误', { type: 'error' });
                    this.lastMessage = `[${new Date().toLocaleTimeString()}] 编译失败`;
                    this.lastStatus = 'error';
                }
            } catch (e) {
                this.$message.error("请求异常");
            } finally {
                this.compiling = false;
            }
        }
    }
};
</script>

<style scoped>
.proc-container {
    height: 100%;
    display: flex;
    flex-direction: column;
    background: #fff;
}

.toolbar {
    padding: 10px 15px;
    border-bottom: 1px solid #ebeef5;
    display: flex;
    justify-content: space-between;
    align-items: center;
    background: #fcfcfc;
    height: 52px;
    flex-shrink: 0;
}

.proc-name {
    font-weight: bold;
    margin-left: 10px;
    color: #303133;
}

/* 编辑器容器改为 Flex 布局 */
.editor-wrapper {
    flex: 1;
    overflow: hidden;
    padding: 0;
    display: flex;
    position: relative;
    border-bottom: 1px solid #ebeef5;
}

/* 左侧行号样式 */
.line-numbers {
    width: 45px;
    /* 固定宽度 */
    background-color: #f0f0f0;
    border-right: 1px solid #dcdfe6;
    text-align: right;
    padding: 15px 5px;
    /* 上边距必须与 textarea 的 padding-top 一致 */
    color: #999;
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 14px;
    line-height: 1.5;
    overflow: hidden;
    /* 隐藏滚动条，通过 JS 控制滚动 */
    user-select: none;
    /* 防止行号被选中 */
    flex-shrink: 0;
}

.line-num {
    padding-right: 4px;
}

/* 覆盖 el-input 样式 */
.code-input {
    flex: 1;
    height: 100%;
}

.code-input>>>.el-textarea__inner {
    height: 100%;
    border: none;
    border-radius: 0;
    resize: none;

    /* 关键样式：统一字体和行高，确保与行号对齐 */
    font-family: 'Consolas', 'Monaco', monospace;
    font-size: 14px;
    line-height: 1.5;
    padding: 15px;
    /* 上边距必须与 line-numbers 一致 */

    /* 关键样式：强制不换行，防止行号错位 */
    white-space: pre !important;
    overflow-x: auto;
    /* 允许水平滚动 */

    background-color: #fff;
    color: #333;
    transition: background-color 0.3s;
}

.code-input.is-readonly>>>.el-textarea__inner {
    background-color: #fafafa;
    color: #555;
}

.code-input>>>.el-textarea__inner:focus {
    background-color: #fff;
    box-shadow: none;
}

.status-bar {
    height: 30px;
    line-height: 30px;
    background: #f5f7fa;
    border-top: 1px solid #e4e7ed;
    padding: 0 15px;
    font-size: 12px;
    color: #606266;
    flex-shrink: 0;
}
</style>