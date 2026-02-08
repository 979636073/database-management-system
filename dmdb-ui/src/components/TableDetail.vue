<template>
    <div class="table-detail-wrapper">
        <el-container class="main-workspace">
            <el-header height="50px" class="workspace-header">
                <div class="header-left">
                    <el-breadcrumb separator="/">
                        <el-breadcrumb-item><i class="el-icon-connection"></i> {{ connName }}</el-breadcrumb-item>
                        <el-breadcrumb-item>{{ currentSchema }}</el-breadcrumb-item>
                        <el-breadcrumb-item>
                            <i v-if="tableType === 'view'" class="el-icon-view"
                                style="color: #E6A23C; margin-right: 4px;"></i>
                            <i v-else class="el-icon-document-copy" style="color: #409EFF; margin-right: 4px;"></i>
                            <b>{{ activeTable }}</b>

                            <span v-if="tableComment" class="table-comment-display" :title="tableComment">
                                ({{ tableComment.length > 15 ? tableComment.substring(0, 15) + '...' : tableComment }})
                            </span>
                            <el-button type="text" icon="el-icon-edit" size="mini" class="edit-comment-btn"
                                @click="openEditComment" title="修改表注释"></el-button>
                        </el-breadcrumb-item>
                    </el-breadcrumb>
                </div>
                <div class="header-right">
                    <el-button-group style="margin-right: 15px">
                        <el-button size="small" icon="el-icon-document" @click="handleShowDDL">查看DDL</el-button>
                        <el-button v-if="tableType === 'table'" size="small" icon="el-icon-edit-outline"
                            @click="handleDesignTable">设计表</el-button>
                        <el-button v-else-if="tableType === 'view'" size="small" icon="el-icon-edit"
                            @click="handleEditView">修改视图</el-button>
                    </el-button-group>

                    <el-radio-group v-model="viewMode" size="small" @change="handleViewModeSwitch">
                        <el-radio-button label="data"><i class="el-icon-s-grid"></i> 数据视图</el-radio-button>
                        <el-radio-button label="relation"><i class="el-icon-share"></i> 关联关系</el-radio-button>
                    </el-radio-group>
                </div>
            </el-header>

            <el-main class="content-area">
                <div v-if="viewMode === 'data'" key="view-data" class="full-height view-container">
                    <div class="filter-card">
                        <div class="compact-query-panel">
                            <div class="query-actions-top-bar">
                                <div class="left-btns">
                                    <span class="panel-title"><i class="el-icon-search"></i> 筛选条件</span>
                                    <el-divider direction="vertical"></el-divider>
                                    <el-button type="text" size="mini" icon="el-icon-plus"
                                        @click="addCondition">添加条件</el-button>
                                    <template v-if="conditions.length > 1">
                                        <span class="v-divider"></span>
                                        <el-radio-group v-model="logicalOperator" size="mini">
                                            <el-radio label="AND">AND</el-radio>
                                            <el-radio label="OR">OR</el-radio>
                                        </el-radio-group>
                                    </template>
                                </div>
                                <div class="right-btns">
                                    <el-button type="primary" size="mini" icon="el-icon-search" @click="handleQuery"
                                        :loading="loading">查询</el-button>
                                    <el-button size="mini" icon="el-icon-refresh-left"
                                        @click="resetFilters">重置</el-button>
                                </div>
                            </div>
                            <div class="query-conditions-scroll-area" v-if="conditions.length > 0">
                                <div class="query-grid">
                                    <div v-for="(cond, index) in conditions" :key="index" class="query-item">
                                        <span class="cond-index">{{ index + 1 }}.</span>
                                        <el-select v-model="cond.field" placeholder="字段" size="mini" class="field-sel"
                                            filterable>
                                            <el-option v-for="col in tableColumns"
                                                :key="col.COLUMN_NAME + '_' + (col.COMMENTS || '')"
                                                :label="col.COLUMN_NAME" :value="col.COLUMN_NAME">
                                                <span style="float: left">{{ col.COLUMN_NAME }}</span>
                                                <span style="float: right; color: #8492a6; font-size: 12px">{{
                                                    col.COMMENTS }}</span>
                                            </el-option>
                                        </el-select>
                                        <el-select v-model="cond.operator" size="mini" class="op-sel">
                                            <el-option label="=" value="="></el-option>
                                            <el-option label="LIKE" value="LIKE"></el-option>
                                            <el-option label=">" value=">"></el-option>
                                            <el-option label="<" value="<"></el-option>
                                            <el-option label="IS NULL" value="IS NULL"></el-option>
                                        </el-select>
                                        <el-input v-if="!cond.operator.includes('NULL')" v-model="cond.value"
                                            placeholder="值" size="mini" class="val-input"
                                            @keyup.enter.native="handleQuery"></el-input>
                                        <el-button type="text" icon="el-icon-close" class="cond-del"
                                            @click="removeCondition(index)"></el-button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>

                    <div class="table-card">
                        <div class="data-toolbar">
                            <div class="tool-left" v-if="!isReadOnly">
                                <el-button size="mini" icon="el-icon-plus" @click="handleAddRow()">插入行</el-button>
                                <el-button size="mini" type="success" icon="el-icon-check" @click="submitBatchChanges"
                                    :disabled="!hasChanges" :loading="saving">保存修改</el-button>
                                <el-button size="mini" icon="el-icon-close" @click="handleDiscardChanges"
                                    :disabled="!hasChanges">放弃修改</el-button>
                                <el-divider direction="vertical"></el-divider>
                                <el-button size="mini" type="danger" icon="el-icon-delete" @click="handleBatchDelete"
                                    :disabled="selectedRows.length === 0" plain>批量删除</el-button>
                                <el-divider direction="vertical"></el-divider>
                                <el-tag v-if="tableType === 'view'" size="small" type="success" effect="plain"
                                    style="margin-right: 10px;">
                                    <i class="el-icon-check"></i> 简单视图 (可编辑)
                                </el-tag>
                                <el-button size="mini" icon="el-icon-refresh" @click="loadData">刷新</el-button>
                            </div>
                            <div class="tool-left" v-else>
                                <el-tag size="small" type="warning" effect="plain" style="margin-right: 10px;">
                                    <i class="el-icon-lock"></i> 复杂视图 (只读)
                                </el-tag>
                                <el-button size="mini" icon="el-icon-refresh" @click="loadData">刷新数据</el-button>
                            </div>

                            <div class="data-stats">
                                <span v-if="selectedRows.length > 0" style="color: #409EFF; margin-right: 15px;">已选 {{
                                    selectedRows.length
                                }} 行</span>
                                <span v-if="hasChanges" style="color: #E6A23C; margin-right: 10px;"><i
                                        class="el-icon-warning"></i>
                                    有未保存的更改</span>
                                <i class="el-icon-info"></i> 共 <b>{{ totalCount }}</b> 条记录
                            </div>
                        </div>

                        <div class="table-wrapper" v-loading="loading">
                            <el-table ref="dataTable" :data="currentDataList" border height="100%" size="mini" stripe
                                class="custom-table" highlight-current-row @sort-change="handleSortChange"
                                @selection-change="handleSelectionChange" @cell-dblclick="handleCellDblClick"
                                :cell-style="getCellStyle">
                                <el-table-column type="selection" width="45" align="center"
                                    fixed="left"></el-table-column>
                                <el-table-column type="index" width="55" align="center" fixed="left" label="#"
                                    :index="indexMethod"></el-table-column>
                                <template v-if="tableColumns.length > 0">
                                    <el-table-column v-for="col in tableColumns"
                                        :key="col.COLUMN_NAME + '_' + (col.COMMENTS || '')" :prop="col.COLUMN_NAME"
                                        min-width="150" show-overflow-tooltip sortable="custom">
                                        <template slot="header" slot-scope="scope">
                                            <div class="custom-header">
                                                <div class="col-name">
                                                    <i v-if="col.IS_PK == 1" class="el-icon-key"
                                                        style="color:#E6A23C;margin-right:2px;" title="主键"></i>
                                                    {{ col.COLUMN_NAME }}
                                                </div>
                                                <div v-if="col.COMMENTS" class="col-comment">{{ col.COMMENTS }}</div>
                                            </div>
                                        </template>
                                        <template slot-scope="scope">
                                            <div v-if="isBinaryLob(col.DATA_TYPE)">
                                                <div v-if="!isReadOnly && !scope.row.DB_INTERNAL_ID">
                                                    <input type="file"
                                                        :id="'file-' + scope.$index + '-' + col.COLUMN_NAME"
                                                        style="display:none"
                                                        @change="(e) => handleInlineFileUpload(e, scope.row, col.COLUMN_NAME)" />

                                                    <el-button size="mini" type="primary" plain
                                                        icon="el-icon-folder-add"
                                                        @click="triggerFileSelect(scope.$index, col.COLUMN_NAME)">
                                                        {{ scope.row[col.COLUMN_NAME] ? '已选文件 (待保存)' : '选择文件' }}
                                                    </el-button>
                                                    <el-button v-if="scope.row[col.COLUMN_NAME]" type="text"
                                                        icon="el-icon-close" style="color:#F56C6C; margin-left:5px"
                                                        @click="clearInlineFile(scope.row, col.COLUMN_NAME)"
                                                        title="清除"></el-button>
                                                </div>
                                                <div v-else-if="scope.row.DB_INTERNAL_ID">
                                                    <el-tag size="mini" type="info" style="margin-right: 5px;">{{
                                                        col.DATA_TYPE }}</el-tag>
                                                    <el-button type="text" size="mini" icon="el-icon-view" title="查看/预览"
                                                        @click="handlePreviewLob(scope.row, col)"></el-button>
                                                    <el-button type="text" size="mini" icon="el-icon-download"
                                                        title="下载"
                                                        @click="handleDownloadLob(scope.row, col)"></el-button>
                                                    <el-button type="text" size="mini" icon="el-icon-upload2"
                                                        title="上传覆盖" @click="handleUploadLob(scope.row, col)"
                                                        v-if="!isReadOnly"></el-button>
                                                </div>
                                                <span v-else class="placeholder-text">NULL</span>
                                            </div>

                                            <div v-else-if="isTextLob(col.DATA_TYPE)">
                                                <div v-if="!isReadOnly && (isCellEditing(scope.row, col.COLUMN_NAME) || !scope.row.DB_INTERNAL_ID)"
                                                    class="inline-input-box">
                                                    <el-input type="textarea" :rows="1" autosize
                                                        v-model="scope.row[col.COLUMN_NAME]"
                                                        @blur="finishEditing(scope.row, col.COLUMN_NAME)"
                                                        placeholder="请输入...">
                                                    </el-input>
                                                </div>
                                                <div v-else-if="scope.row.DB_INTERNAL_ID">
                                                    <el-tag size="mini" type="info"
                                                        style="margin-right: 5px;">CLOB</el-tag>
                                                    <el-button type="text" size="mini" icon="el-icon-document"
                                                        @click="handlePreviewLob(scope.row, col)">查看内容</el-button>
                                                </div>
                                                <span v-else class="placeholder-text">NULL</span>
                                            </div>

                                            <div v-else-if="!isReadOnly && isCellEditing(scope.row, col.COLUMN_NAME)"
                                                class="inline-input-box">
                                                <el-input v-model="scope.row[col.COLUMN_NAME]" size="mini"
                                                    @blur="finishEditing(scope.row, col.COLUMN_NAME)"
                                                    @keyup.enter.native="$event.target.blur()"></el-input>
                                            </div>
                                            <span v-else>{{ scope.row[col.COLUMN_NAME] }}</span>
                                        </template>
                                    </el-table-column>
                                </template>

                                <el-table-column v-if="!isReadOnly" label="操作" width="80" fixed="right" align="center">
                                    <template slot-scope="scope">
                                        <el-button type="text" size="mini" icon="el-icon-delete" class="danger-text"
                                            @click="handleDeleteRow(scope.$index, scope.row)"></el-button>
                                    </template>
                                </el-table-column>
                            </el-table>
                        </div>

                        <div class="pagination-bar">
                            <el-pagination @size-change="handleSizeChange" @current-change="handleCurrentChange"
                                :current-page="currentPage" :page-sizes="[20, 50, 100]" :page-size="pageSize"
                                layout="total, sizes, prev, pager, next, jumper" :total="totalCount"></el-pagination>
                        </div>
                    </div>
                </div>

                <div v-else-if="viewMode === 'relation'" key="view-relation"
                    class="relation-view-container view-container">
                    <div class="relation-toolbar">
                        <div class="legend-box">
                            <span class="legend-item"><span class="dot active"></span> 当前表</span>
                            <span class="legend-item"><span class="dot normal"></span> 关联表</span>
                            <el-divider direction="vertical"></el-divider>
                            <el-switch v-model="showAllColumns" active-text="全部展开" @change="handleShowAllChange"
                                size="mini"></el-switch>
                        </div>
                        <div class="zoom-btns">
                            <el-button-group>
                                <el-button size="mini" icon="el-icon-zoom-in" @click="handleGraphZoom(1.1)"></el-button>
                                <el-button size="mini" icon="el-icon-zoom-out"
                                    @click="handleGraphZoom(0.9)"></el-button>
                                <el-button size="mini" icon="el-icon-aim" @click="handleGraphFit"></el-button>
                            </el-button-group>
                        </div>
                    </div>
                    <div class="canvas-wrapper">
                        <div ref="relationCanvas" class="g6-canvas-box"></div>
                    </div>
                </div>
            </el-main>
        </el-container>

        <el-dialog title="修改表注释" :visible.sync="editCommentVisible" width="500px" append-to-body>
            <el-form label-width="80px" size="small">
                <el-form-item label="表名">
                    <el-input :value="activeTable" disabled></el-input>
                </el-form-item>
                <el-form-item label="注释">
                    <el-input type="textarea" :rows="4" v-model="newComment" placeholder="请输入表注释"></el-input>
                </el-form-item>
            </el-form>
            <div slot="footer">
                <el-button @click="editCommentVisible = false" size="small">取消</el-button>
                <el-button type="primary" @click="submitComment" :loading="altering" size="small">保存</el-button>
            </div>
        </el-dialog>

        <el-dialog :title="previewTitle" :visible.sync="previewVisible" width="800px" append-to-body
            custom-class="preview-dialog">
            <div v-loading="previewLoading"
                style="min-height: 200px; display: flex; justify-content: center; align-items: center; overflow: auto; max-height: 600px;">
                <img v-if="previewType === 'image'" :src="previewUrl"
                    style="max-width: 100%; max-height: 100%; box-shadow: 0 0 10px rgba(0,0,0,0.1);" />
                <pre v-else-if="previewType === 'text'"
                    style="width:100%; white-space: pre-wrap; font-family: Consolas; background: #f5f7fa; padding: 10px;">{{
            previewContent }}</pre>
                <div v-else style="color: #999; text-align: center;">
                    <i class="el-icon-document" style="font-size: 48px;"></i>
                    <p>二进制文件，不支持直接预览，请下载。</p>
                </div>
            </div>
            <div slot="footer">
                <el-button @click="previewVisible = false">关闭</el-button>
                <el-button type="primary" icon="el-icon-download" @click="downloadCurrentLob">下载文件</el-button>
            </div>
        </el-dialog>

        <input type="file" ref="lobFileInput" style="display: none" @change="handleFileChange" />

        <el-dialog title="编辑视图定义" :visible.sync="editViewVisible" width="900px" append-to-body
            :close-on-click-modal="false">
            <div style="margin-bottom: 10px;">
                <el-alert title="修改视图定义将执行 CREATE OR REPLACE VIEW 语句。" type="warning" show-icon
                    :closable="false"></el-alert>
            </div>
            <div style="height: 450px; border: 1px solid #dcdfe6;">
                <SqlEditor v-model="viewSql" language="sql" />
            </div>
            <div slot="footer">
                <el-button @click="editViewVisible = false">取消</el-button>
                <el-button type="primary" @click="submitViewAlter" :loading="altering">保存修改</el-button>
            </div>
        </el-dialog>

        <el-dialog :title="conflictTitle" :visible.sync="conflictVisible" width="800px" append-to-body
            :close-on-click-modal="false">
            <div class="conflict-alert" :class="summaryStyle.class">
                <i :class="summaryStyle.icon" style="margin-right: 8px;"></i>
                <span>{{ conflictSummaryText }}</span>
            </div>
            <el-table :data="conflictList" border size="small" style="margin-top: 15px;"
                :row-class-name="tableRowClassName">
                <el-table-column label="类型" width="100" align="center">
                    <template slot-scope="scope">
                        <el-tag v-if="scope.row.CNT === 'MISSING'" type="danger" size="mini" effect="dark"><i
                                class="el-icon-error"></i> 缺主键</el-tag>
                        <el-tag v-else type="warning" size="mini" effect="dark"><i class="el-icon-connection"></i>
                            被引用</el-tag>
                    </template>
                </el-table-column>
                <el-table-column label="涉及对象" min-width="180">
                    <template slot-scope="scope">
                        <div><span style="color:#909399;font-size:12px;">表：</span><el-tag size="mini" type="info"
                                style="font-weight:bold;">{{ scope.row.TABLE_NAME }}</el-tag></div>
                        <div style="margin-top:4px;"><span style="color:#909399;font-size:12px;">列：</span><b>{{
                            scope.row.COLUMN_NAME }}</b></div>
                    </template>
                </el-table-column>
                <el-table-column label="冲突详情" min-width="280">
                    <template slot-scope="scope">
                        <div v-if="scope.row.CNT === 'MISSING'">目标表不存在以下 <b>{{ scope.row.MY_VAL_LIST ?
                            scope.row.MY_VAL_LIST.length : 1 }}</b> 个主键值，无法插入。</div>
                        <div v-else>当前记录被子表引用了 <b>{{ scope.row.CNT }}</b> 次，必须先处理子表数据。</div>
                        <div style="margin-top:5px;font-size:12px;color:#909399;">涉及值: {{
                            formatValList(scope.row.MY_VAL_LIST) }}</div>
                    </template>
                </el-table-column>
                <el-table-column label="解决方案" width="130" align="center">
                    <template slot-scope="scope">
                        <el-button :type="scope.row.CNT === 'MISSING' ? 'danger' : 'warning'" size="mini" plain
                            @click="resolveConflict(scope.row)">{{ scope.row.CNT === 'MISSING' ? '去父表补充' : '去子表处理' }} <i
                                class="el-icon-right"></i></el-button>
                    </template>
                </el-table-column>
            </el-table>
            <div slot="footer"><el-button @click="conflictVisible = false">关 闭</el-button></div>
        </el-dialog>

        <el-dialog title="DDL 预览" :visible.sync="ddlVisible" width="800px" append-to-body>
            <div style="height: 500px; border: 1px solid #dcdfe6;">
                <SqlEditor v-model="currentDDL" :readOnly="true" />
            </div>
            <div slot="footer"><el-button @click="ddlVisible = false">关闭</el-button></div>
        </el-dialog>

        <el-dialog title="设计表结构" :visible.sync="designVisible" width="1100px" append-to-body
            :close-on-click-modal="false" top="5vh">
            <div style="margin-bottom: 10px;"><el-alert title="注意：修改结构会生成 ALTER TABLE 语句。请谨慎操作。" type="warning"
                    show-icon :closable="false"></el-alert></div>
            <el-tabs v-model="designActiveTab" type="card">
                <el-tab-pane label="字段列 (Columns)" name="columns">
                    <div style="margin-bottom: 10px; text-align: right;"><el-button size="mini" type="primary"
                            icon="el-icon-plus" @click="addDesignColumn">添加列</el-button></div>
                    <el-table :data="designColumns" border size="mini" height="400">
                        <el-table-column label="状态" width="50" align="center"><template slot-scope="scope"><i
                                    v-if="scope.row._status === 'new'" class="el-icon-plus" style="color:green"></i><i
                                    v-else-if="scope.row._status === 'modified'" class="el-icon-edit"
                                    style="color:orange"></i></template></el-table-column>
                        <el-table-column label="列名" width="150"><template slot-scope="scope"><el-input
                                    v-model="scope.row.COLUMN_NAME" @change="markModified(scope.row)"
                                    size="mini"></el-input></template></el-table-column>

                        <el-table-column label="类型" width="130">
                            <template slot-scope="scope">
                                <el-select v-model="scope.row.DATA_TYPE" @change="handleTypeChange(scope.row)"
                                    size="mini" filterable allow-create>
                                    <el-option v-for="type in dmDataTypes" :key="type" :label="type"
                                        :value="type"></el-option>
                                </el-select>
                            </template>
                        </el-table-column>

                        <el-table-column label="长度/精度" width="100">
                            <template slot-scope="scope">
                                <el-input v-model="scope.row.DATA_LENGTH" @change="markModified(scope.row)" size="mini"
                                    :disabled="isLengthDisabled(scope.row.DATA_TYPE)"
                                    :placeholder="getLengthPlaceholder(scope.row.DATA_TYPE)">
                                </el-input>
                            </template>
                        </el-table-column>

                        <el-table-column label="标度" width="80">
                            <template slot-scope="scope">
                                <el-input v-model="scope.row.DATA_SCALE" @change="markModified(scope.row)" size="mini"
                                    :disabled="isScaleDisabled(scope.row.DATA_TYPE)" placeholder="0">
                                </el-input>
                            </template>
                        </el-table-column>

                        <el-table-column label="默认值" width="120">
                            <template slot-scope="scope">
                                <el-input v-model="scope.row.DATA_DEFAULT" @change="markModified(scope.row)" size="mini"
                                    placeholder="无"></el-input>
                            </template>
                        </el-table-column>

                        <el-table-column label="主键" width="50"><template slot-scope="scope"><el-checkbox
                                    v-model="scope.row.IS_PK"
                                    @change="handlePkChange(scope.row)"></el-checkbox></template></el-table-column>
                        <el-table-column label="空值" width="50"><template slot-scope="scope"><el-checkbox
                                    v-model="scope.row.NULLABLE" @change="markModified(scope.row)" true-label="Y"
                                    false-label="N"></el-checkbox></template></el-table-column>
                        <el-table-column label="注释"><template slot-scope="scope"><el-input v-model="scope.row.COMMENTS"
                                    @change="markModified(scope.row)"
                                    size="mini"></el-input></template></el-table-column>
                        <el-table-column label="操作"><template slot-scope="scope"><el-button type="text"
                                    style="color:#F56C6C" icon="el-icon-delete"
                                    @click="removeDesignColumn(scope.$index, scope.row)"></el-button></template></el-table-column>
                    </el-table>
                </el-tab-pane>
                <el-tab-pane label="索引 (Indexes)" name="indexes">
                    <div style="margin-bottom: 10px; text-align: right;"><el-button size="mini" type="primary"
                            icon="el-icon-plus" @click="addDesignIndex">添加索引</el-button></div>
                    <el-table :data="designIndexes" border size="mini" height="400">
                        <el-table-column label="状态" width="50" align="center"><template slot-scope="scope"><i
                                    v-if="scope.row._status === 'new'" class="el-icon-plus"
                                    style="color:green"></i></template></el-table-column>
                        <el-table-column label="索引名称" width="200"><template slot-scope="scope"><el-input
                                    v-model="scope.row.INDEX_NAME" size="mini"></el-input></template></el-table-column>
                        <el-table-column label="包含列"><template slot-scope="scope"><el-select v-model="scope.row.COLUMNS"
                                    multiple size="mini" style="width: 100%"><el-option v-for="c in designColumns"
                                        :key="c.COLUMN_NAME" :label="c.COLUMN_NAME"
                                        :value="c.COLUMN_NAME"></el-option></el-select></template></el-table-column>
                        <el-table-column label="操作" width="60" align="center"><template slot-scope="scope"><el-button
                                    type="text" style="color:#F56C6C" icon="el-icon-delete"
                                    @click="removeDesignIndex(scope.$index, scope.row)"></el-button></template></el-table-column>
                    </el-table>
                </el-tab-pane>
                <el-tab-pane label="外键 (Foreign Keys)" name="fks">
                    <div style="margin-bottom: 10px; text-align: right;"><el-button size="mini" type="primary"
                            icon="el-icon-plus" @click="addDesignFk">添加外键</el-button></div>
                    <el-table :data="designForeignKeys" border size="mini" height="400">
                        <el-table-column label="状态" width="50" align="center"><template slot-scope="scope"><i
                                    v-if="scope.row._status === 'new'" class="el-icon-plus"
                                    style="color:green"></i></template></el-table-column>
                        <el-table-column label="外键名称" width="180"><template slot-scope="scope"><el-input
                                    v-model="scope.row.CONSTRAINT_NAME"
                                    size="mini"></el-input></template></el-table-column>
                        <el-table-column label="本表字段" width="150"><template slot-scope="scope"><el-select
                                    v-model="scope.row.COLUMN_NAME" size="mini"><el-option v-for="c in designColumns"
                                        :key="c.COLUMN_NAME" :label="c.COLUMN_NAME"
                                        :value="c.COLUMN_NAME"></el-option></el-select></template></el-table-column>
                        <el-table-column label="关联主表" width="180"><template slot-scope="scope"><el-select
                                    v-model="scope.row.R_TABLE_NAME" size="mini" allow-create
                                    @change="handleRefTableChange(scope.row)"><el-option v-for="t in allTableNames"
                                        :key="t" :label="t"
                                        :value="t"></el-option></el-select></template></el-table-column>
                        <el-table-column label="目标列" width="150"><template slot-scope="scope"><el-select
                                    v-model="scope.row.R_COLUMN_NAME" size="mini"
                                    @visible-change="(vis) => vis && loadRefColumns(scope.row.R_TABLE_NAME)"><el-option
                                        v-for="col in (refColumnsCache[scope.row.R_TABLE_NAME] || [])"
                                        :key="col.COLUMN_NAME" :label="col.COLUMN_NAME"
                                        :value="col.COLUMN_NAME"></el-option></el-select></template></el-table-column>
                        <el-table-column label="操作" width="60" align="center"><template slot-scope="scope"><el-button
                                    type="text" style="color:#F56C6C" icon="el-icon-delete"
                                    @click="removeDesignFk(scope.$index, scope.row)"></el-button></template></el-table-column>
                    </el-table>
                </el-tab-pane>
            </el-tabs>
            <div slot="footer"><el-button @click="designVisible = false">取消</el-button><el-button type="primary"
                    @click="generateAndRunAlter">预览并保存</el-button></div>
        </el-dialog>

        <el-dialog title="确认修改" :visible.sync="sqlConfirmVisible" width="600px" append-to-body>
            <div style="margin-bottom:10px;">即将执行以下变更语句：</div>
            <div style="height: 300px; border: 1px solid #dcdfe6;">
                <SqlEditor v-model="generatedSql" />
            </div>
            <div slot="footer"><el-button @click="sqlConfirmVisible = false">取消</el-button><el-button type="primary"
                    @click="executeAlter" :loading="altering">确认执行</el-button></div>
        </el-dialog>
    </div>
</template>

<script>
import request from '@/utils/request';
import G6 from '@antv/g6';
import SqlEditor from './SqlEditor.vue';

// G6 RegisterNode
G6.registerNode('db-table', {
    draw: (cfg, group) => {
        const { label, tableComment, columns = [], isCenter, isTruncated, totalColCount } = cfg;
        const width = 300; const headerHeight = 50; const rowHeight = 28; const contentHeight = columns.length * rowHeight;
        let footerHeight = 0; if (isTruncated) footerHeight = 24; else if (totalColCount > 10) footerHeight = 24;
        const height = headerHeight + contentHeight + footerHeight + 8;
        group.addShape('rect', { attrs: { x: 0, y: 0, width, height, fill: '#fff', stroke: isCenter ? '#409EFF' : '#DCDFE6', lineWidth: 1, radius: 4 }, name: 'container-shape' });
        group.addShape('rect', { attrs: { x: 0, y: 0, width, height: headerHeight, fill: isCenter ? '#409EFF' : '#F2F6FC', radius: [4, 4, 0, 0] }, name: 'header-shape' });
        group.addShape('text', { attrs: { x: 10, y: 20, text: label, fill: isCenter ? '#fff' : '#333', fontSize: 13, fontWeight: 'bold', textBaseline: 'middle' }, name: 'title-text' });
        if (tableComment) group.addShape('text', { attrs: { x: 10, y: 38, text: tableComment, fill: isCenter ? '#eee' : '#909399', fontSize: 11, textBaseline: 'middle' }, name: 'comment-text' });
        columns.forEach((col, i) => {
            const y = headerHeight + (i * rowHeight) + (rowHeight / 2);
            group.addShape('text', { attrs: { x: 28, y: y, text: col.COLUMN_NAME, fill: col.IS_PK ? '#E6A23C' : '#333', fontSize: 12, textBaseline: 'middle', fontWeight: col.IS_PK ? 'bold' : 'normal' } });
            if (col.IS_PK) group.addShape('circle', { attrs: { x: 14, y: y, r: 3, fill: '#E6A23C' } });
            if (col.COMMENTS) { let comment = col.COMMENTS; if (comment.length > 10) comment = comment.substring(0, 10) + '...'; group.addShape('text', { attrs: { x: 160, y: y, text: comment, fill: '#909399', fontSize: 11, textBaseline: 'middle' } }); }
        });
        const y = headerHeight + contentHeight + 12;
        if (isTruncated) group.addShape('text', { attrs: { x: width / 2, y: y, text: `... (共 ${totalColCount} 列，点击展开)`, fill: '#909399', fontSize: 11, textAlign: 'center', textBaseline: 'middle', cursor: 'pointer' }, name: 'expand-text' });
        else if (totalColCount > 10) group.addShape('text', { attrs: { x: width / 2, y: y, text: '⬆ 点击折叠', fill: '#409EFF', fontSize: 11, textAlign: 'center', textBaseline: 'middle', cursor: 'pointer' }, name: 'collapse-text' });
        return group.get('children')[0];
    }
});

// 类型常量
const DM_DATA_TYPES = [
    'CHAR', 'VARCHAR', 'VARCHAR2', 'TEXT', 'LONG', 'CLOB', 'BLOB', 'IMAGE',
    'NUMBER', 'NUMERIC', 'DECIMAL', 'INTEGER', 'INT', 'BIGINT', 'TINYINT', 'BYTE', 'SMALLINT',
    'FLOAT', 'DOUBLE', 'DOUBLE PRECISION', 'REAL',
    'BIT', 'BINARY', 'VARBINARY',
    'DATE', 'TIME', 'TIMESTAMP', 'TIMESTAMP WITH TIME ZONE', 'TIMESTAMP WITH LOCAL TIME ZONE',
    'INTERVAL YEAR TO MONTH', 'INTERVAL DAY TO SECOND',
    'BOOLEAN'
];

const NO_LENGTH_TYPES = ['INT', 'INTEGER', 'BIGINT', 'TINYINT', 'SMALLINT', 'BYTE', 'DATE', 'TIME', 'TIMESTAMP', 'CLOB', 'BLOB', 'TEXT', 'LONG', 'BOOLEAN', 'IMAGE'];
const SCALE_TYPES = ['NUMBER', 'DECIMAL', 'NUMERIC', 'FLOAT', 'DOUBLE', 'REAL'];
const BINARY_LOB_TYPES = ['BLOB', 'IMAGE', 'BFILE', 'BINARY', 'VARBINARY', 'GEOMETRY'];
const TEXT_LOB_TYPES = ['CLOB', 'TEXT', 'LONG'];

export default {
    name: 'TableDetail',
    components: { SqlEditor },
    props: ['connId', 'connName', 'schema', 'tableName', 'initialFilter', 'initViewMode', 'tableType'],
    data() {
        return {
            currentSchema: this.schema, activeTable: this.tableName,
            viewMode: this.initViewMode || 'data',
            loading: false, saving: false,
            currentDataList: [], totalCount: 0, currentPage: 1, pageSize: 50,
            editingCell: null, originalDataMap: {}, modifiedRows: new Set(), newRows: [],
            tableColumns: [], conditions: [], logicalOperator: 'AND',
            conflictVisible: false, conflictList: [], conflictPkValue: null, conflictType: 'delete',
            ddlVisible: false, currentDDL: '',

            editViewVisible: false, viewSql: '',
            isSimpleView: true,

            jumpFromConflict: false,
            selectedRows: [],

            // 表注释相关
            tableComment: '',
            editCommentVisible: false,
            newComment: '',

            designVisible: false, designActiveTab: 'columns',
            designColumns: [], originalColumns: [], deleteColumnList: [],
            designIndexes: [], deleteIndexList: [],
            designForeignKeys: [], deleteFkList: [],
            allTableNames: [], refColumnsCache: {},
            dmDataTypes: DM_DATA_TYPES,

            showAllColumns: false, expandedTableList: [], graphNodeIds: [],
            sqlConfirmVisible: false, generatedSql: '', altering: false,

            // LOB 预览相关
            previewVisible: false,
            previewType: 'text',
            previewContent: '',
            previewUrl: '',
            previewLoading: false,
            currentLobRow: null,
            currentLobCol: null,
        };
    },

    computed: {
        rawKeys() { return this.currentDataList.length ? Object.keys(this.currentDataList[0]).filter(k => k !== 'DB_INTERNAL_ID') : []; },
        hasChanges() { return this.modifiedRows.size > 0 || this.newRows.length > 0; },
        isReadOnly() {
            if (this.tableType === 'table') return false;
            if (this.tableType === 'view') return !this.isSimpleView;
            return true;
        },
        isParentMissing() { return this.conflictList.length > 0 && this.conflictList[0].CNT === 'MISSING'; },
        hasMixedConflict() {
            if (!this.conflictList.length) return false;
            const hasMissing = this.conflictList.some(c => c.CNT === 'MISSING');
            const hasRef = this.conflictList.some(c => c.CNT !== 'MISSING');
            return hasMissing && hasRef;
        },
        conflictTitle() {
            if (this.hasMixedConflict) return '⚠️ 批量操作存在多类冲突';
            if (this.isParentMissing) return '⚠️ 保存失败：目标主键缺失';
            return '⚠️ 操作受阻：存在数据引用';
        },
        conflictSummaryText() {
            const missingCount = this.conflictList.filter(c => c.CNT === 'MISSING').length;
            const refCount = this.conflictList.filter(c => c.CNT !== 'MISSING').length;
            if (this.hasMixedConflict) return `检测到混合冲突：有 ${missingCount} 项外键在父表中不存在，且有 ${refCount} 项记录正被子表引用。`;
            else if (missingCount > 0) return `检测到 ${missingCount} 处外键依赖缺失，请前往父表补充对应数据。`;
            else return `检测到 ${refCount} 处引用约束，以下数据正被其他表使用，无法直接删除或修改。`;
        },
        summaryStyle() {
            if (this.hasMixedConflict) return { class: 'conflict-mixed', icon: 'el-icon-warning' };
            if (this.isParentMissing) return { class: 'conflict-missing', icon: 'el-icon-error' };
            return { class: 'conflict-ref', icon: 'el-icon-connection' };
        },
        previewTitle() {
            if (!this.currentLobCol) return '预览';
            return `预览 ${this.currentLobCol.COLUMN_NAME} (${this.currentLobCol.DATA_TYPE})`;
        }
    },

    mounted() { this.initView(); },

    watch: {
        initialFilter: {
            immediate: true, deep: true,
            handler(val) {
                if (val && val.field && val.value !== undefined) {
                    if (this.viewMode !== 'data') this.handleViewModeSwitch('data');
                    this.jumpFromConflict = true;
                    this.applyAutoFilter(val);
                }
            }
        },
        tableName: {
            immediate: true,
            handler(val) {
                if (val) {
                    this.activeTable = val; this.currentSchema = this.schema;
                    this.currentPage = 1; this.expandedTableList = []; this.showAllColumns = false; this.selectedRows = [];
                    if (this.initialFilter && this.initialFilter.field) return;
                    this.loadData();
                }
            }
        },
        initViewMode(val) { if (val && val !== this.viewMode) this.handleViewModeSwitch(val); },
    },

    methods: {
        async request(method, url, dataOrParams = {}) {
            const config = { method, url, headers: { 'Conn-Id': this.connId } };
            if (!url.startsWith('/db')) url = '/db' + url;
            config.url = url;
            if (method === 'get' || method === 'delete') config.params = dataOrParams; else config.data = dataOrParams;
            return request(config);
        },

        handleError(e, title = '操作失败') {
            let rawMsg = e.response?.data?.msg || e.response?.data?.message || e.message || '未知错误';
            if (rawMsg.includes('nested exception is')) rawMsg = rawMsg.split('nested exception is').pop();
            rawMsg = rawMsg.replace(/[\w\.]*Exception:\s*/g, '').trim();
            if (rawMsg.length > 300) rawMsg = rawMsg.substring(0, 300) + '...';
            this.$alert(`<div style="font-size: 14px; line-height: 1.5; color: #606266;"><p style="font-weight: bold; color: #F56C6C;">❌ ${title}：</p><p style="background: #fef0f0; padding: 8px; border-radius: 4px; margin: 5px 0; word-break: break-all;">${rawMsg}</p></div>`, title, { dangerouslyUseHTMLString: true, confirmButtonText: '知道了', type: 'error', customClass: 'error-dialog-width' });
        },

        initView() {
            this.destroyGraph();
            if (this.viewMode === 'relation') this.loadRelations();
            else if (this.viewMode === 'data') {
                if (!this.initialFilter || !this.initialFilter.field) this.loadData();
            }
        },

        handleViewModeSwitch(val) {
            if (this.hasChanges) {
                this.$confirm('当前有未保存的修改，切换视图将丢失这些修改，是否继续？', '提示', { type: 'warning' })
                    .then(() => { this.resetEditState(); this._doSwitch(val); })
                    .catch(() => { this.viewMode = 'data'; });
            } else { this._doSwitch(val); }
        },

        _doSwitch(val) {
            const mode = (typeof val === 'string') ? val : this.viewMode;
            this.viewMode = mode;
            this.$emit('view-mode-change', mode);
            if (mode === 'relation') this.$nextTick(() => { this.loadRelations(); });
            else { this.destroyGraph(); if (mode === 'data') this.loadData(); }
        },

        async loadTableComment() {
            try {
                const sql = `SELECT COMMENTS FROM ALL_TAB_COMMENTS WHERE OWNER='${this.currentSchema}' AND TABLE_NAME='${this.activeTable}' AND TABLE_TYPE IN ('TABLE', 'VIEW')`;
                const res = await this.request('post', '/execute', { sql: sql });
                if (res.data.code === 200 && res.data.data && res.data.data.length > 0) {
                    this.tableComment = res.data.data[0].COMMENTS || '';
                } else {
                    this.tableComment = '';
                }
            } catch (e) {
                console.error("加载表注释失败", e);
            }
        },

        openEditComment() {
            this.newComment = this.tableComment;
            this.editCommentVisible = true;
        },

        async submitComment() {
            this.altering = true;
            try {
                const safeComment = this.newComment.replace(/'/g, "''");
                const sql = `COMMENT ON TABLE "${this.currentSchema}"."${this.activeTable}" IS '${safeComment}'`;
                const res = await this.request('post', '/execute', { sql: sql });
                if (res.data.code === 200) {
                    this.$message.success('注释更新成功');
                    this.tableComment = this.newComment;
                    this.editCommentVisible = false;
                    this.$emit('table-comment-change', this.newComment);
                } else {
                    throw new Error(res.data.msg);
                }
            } catch (e) {
                this.handleError(e, '更新注释失败');
            } finally {
                this.altering = false;
            }
        },

        async loadData() {
            this.resetEditState(); this.selectedRows = []; this.loading = true;
            this.loadTableComment();

            try {
                const colRes = await this.request('get', '/columns', { schema: this.currentSchema, tableName: this.activeTable });
                this.tableColumns = colRes.data.data || [];
                let list = []; let total = 0;
                const params = { schema: this.currentSchema, tableName: this.activeTable, page: this.currentPage, size: this.pageSize };

                if (this.conditions.length > 0) {
                    const filterRes = await this.request('post', '/filter', { ...params, logic: this.logicalOperator, conditions: this.conditions });
                    list = filterRes.data.data.list || []; total = filterRes.data.data.total || 0;
                    if (filterRes.data.data.isView) this.isSimpleView = filterRes.data.data.isSimpleView;
                } else {
                    const dataRes = await this.request('get', '/data', params);
                    list = dataRes.data.data.list || []; total = dataRes.data.data.total || 0;
                    if (dataRes.data.data.isView) this.isSimpleView = dataRes.data.data.isSimpleView;
                }
                this.currentDataList = list; this.totalCount = total; this.originalDataMap = {};
                list.forEach(row => { if (row.DB_INTERNAL_ID) this.originalDataMap[row.DB_INTERNAL_ID] = JSON.parse(JSON.stringify(row)); });

                if (this.jumpFromConflict && this.totalCount === 0) {
                    this.jumpFromConflict = false;
                    this.$nextTick(() => {
                        if (this.conditions.length > 0 && this.logicalOperator === 'OR') {
                            let addedCount = 0;
                            this.conditions.forEach(cond => {
                                if (cond.operator === '=' && (cond.value || cond.value === 0)) {
                                    this.handleAddRow(cond.value, cond.field); addedCount++;
                                }
                            });
                            if (addedCount > 0) this.$message({ message: `已为您批量创建 ${addedCount} 条新行并填入缺失的主键，请补充其他信息。`, type: 'success', duration: 6000, showClose: true });
                        }
                        else {
                            this.handleAddRow();
                            this.$message({ message: '未找到指定主键记录，已自动为您创建新行并填入主键，请补充其他信息。', type: 'success', duration: 5000, showClose: true });
                        }
                        if (this.$refs.dataTable) this.$refs.dataTable.doLayout();
                    });
                } else if (this.totalCount > 0) this.jumpFromConflict = false;
            } catch (e) { this.handleError(e, '数据加载失败'); } finally { this.loading = false; }
        },

        async handleEditView() {
            try {
                const res = await this.request('get', '/ddl', { schema: this.currentSchema, tableName: this.activeTable });
                let rawDDL = res.data.data || '';
                this.viewSql = rawDDL.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
                this.editViewVisible = true;
            } catch (e) { this.handleError(e, '获取视图定义失败'); }
        },

        async submitViewAlter() {
            this.altering = true;
            try {
                const res = await this.request('post', '/execute', { sql: this.viewSql });
                if (res.data.code === 200) { this.$message.success('视图修改成功'); this.editViewVisible = false; this.loadData(); }
                else { throw new Error(res.data.msg); }
            } catch (e) { this.handleError(e, '修改视图失败'); } finally { this.altering = false; }
        },

        resetEditState() { this.editingCell = null; this.modifiedRows = new Set(); this.newRows = []; },
        handleDiscardChanges() { this.resetEditState(); this.loadData(); },
        handleCellDblClick(row, column) {
            if (this.isReadOnly) return;
            this.editingCell = { rowId: row.DB_INTERNAL_ID || row._tempId, col: column.property };
            this.$nextTick(() => { const inputs = document.getElementsByTagName('input'); if (inputs && inputs.length > 0) inputs[inputs.length - 1].focus(); });
        },
        isCellEditing(row, colName) { if (!this.editingCell) return false; const id = row.DB_INTERNAL_ID || row._tempId; return this.editingCell.rowId === id && this.editingCell.col === colName; },
        finishEditing(row, colName) { this.editingCell = null; const id = row.DB_INTERNAL_ID; if (!id) return; const originalRow = this.originalDataMap[id]; let newVal = row[colName]; let oldVal = originalRow ? originalRow[colName] : ''; if (newVal == null) newVal = ''; if (oldVal == null) oldVal = ''; if (String(newVal) !== String(oldVal)) { this.modifiedRows.add(id); this.modifiedRows = new Set(this.modifiedRows); } },
        getCellStyle({ row, column }) { const id = row.DB_INTERNAL_ID; const col = column.property; if (!id && row._tempId) return { backgroundColor: '#f0f9eb' }; if (this.modifiedRows.has(id)) { const original = this.originalDataMap[id]; let newVal = row[col]; let oldVal = original ? original[col] : ''; if (newVal == null) newVal = ''; if (oldVal == null) oldVal = ''; if (String(newVal) !== String(oldVal)) return { backgroundColor: '#fdf6ec', color: '#E6A23C', fontWeight: 'bold' }; } return {}; },

        handleAddRow(prefillValue = null, prefillField = null) {
            const newRow = { _tempId: 'NEW_' + Date.now() + Math.random().toString(36).substr(2, 5) };

            this.tableColumns.forEach(c => {
                let defVal = null;
                if (c.DATA_DEFAULT !== undefined && c.DATA_DEFAULT !== null) {
                    let rawDefault = String(c.DATA_DEFAULT).trim();
                    if (rawDefault.startsWith("'") && rawDefault.endsWith("'")) {
                        defVal = rawDefault.substring(1, rawDefault.length - 1);
                    } else if (!isNaN(rawDefault)) {
                        defVal = rawDefault;
                    }
                }
                newRow[c.COLUMN_NAME] = defVal;
            });

            if (prefillValue !== null && prefillField) {
                const matchKey = Object.keys(newRow).find(k => k.toUpperCase() === prefillField.toUpperCase());
                if (matchKey) newRow[matchKey] = prefillValue;
            }
            else if (this.conditions.length === 1 && this.conditions[0].operator === '=') {
                const fieldName = this.conditions[0].field; const val = this.conditions[0].value;
                const matchKey = Object.keys(newRow).find(k => k.toUpperCase() === fieldName.toUpperCase());
                if (matchKey && val !== undefined && val !== null) newRow[matchKey] = val;
            }
            this.currentDataList.unshift(newRow); this.newRows.push(newRow);
        },

        getPkColumnName() {
            const pkCol = this.tableColumns.find(c => ['1', 1, 'Y', true].includes(c.IS_PK));
            if (pkCol) return pkCol.COLUMN_NAME;
            const idCol = this.tableColumns.find(c => c.COLUMN_NAME.toUpperCase() === 'ID');
            if (idCol) return idCol.COLUMN_NAME;
            if (this.tableColumns.length > 0) return this.tableColumns[0].COLUMN_NAME;
            return null;
        },
        getCleanRowData(row) { const cleanData = {}; this.tableColumns.forEach(col => { cleanData[col.COLUMN_NAME] = row[col.COLUMN_NAME]; }); if (row.DB_INTERNAL_ID) cleanData.DB_INTERNAL_ID = row.DB_INTERNAL_ID; return cleanData; },

        async submitBatchChanges() {
            if (this.saving) return;
            this.saving = true;
            const insertList = this.newRows.map(row => { const clean = this.getCleanRowData(row); delete clean.DB_INTERNAL_ID; return clean; });
            const updateList = Array.from(this.modifiedRows).map(id => { const row = this.currentDataList.find(r => r.DB_INTERNAL_ID === id); return row ? this.getCleanRowData(row) : null; }).filter(r => r !== null);
            if (insertList.length === 0 && updateList.length === 0) { this.$message.info("没有检测到有效变更"); this.saving = false; return; }
            const loadingInstance = this.$loading({ lock: true, text: '正在批量保存中...', background: 'rgba(0, 0, 0, 0.7)' });
            try {
                const res = await this.request('post', `/save/batch?schema=${this.currentSchema}&tableName=${this.activeTable}`, { insertList, updateList });
                loadingInstance.close();
                if (res.data.code === 200) { this.$message.success('批量保存成功！'); this.loadData(); }
                else if (res.data.code === 503) { this.conflictList = res.data.data; this.conflictType = 'save'; this.conflictVisible = true; }
                else { throw new Error(res.data.msg); }
            } catch (e) { loadingInstance.close(); this.handleError(e, '保存失败'); } finally { this.saving = false; }
        },

        handleDeleteRow(index, row) {
            if (!row.DB_INTERNAL_ID) { this.currentDataList.splice(index, 1); const idx = this.newRows.indexOf(row); if (idx > -1) this.newRows.splice(idx, 1); }
            else {
                this.$confirm('确定删除该行数据吗?', '提示', { type: 'warning' }).then(async () => {
                    const pkName = this.getPkColumnName(); const pkVal = pkName ? row[pkName] : null;
                    const res = await this.request('delete', '/delete', { schema: this.currentSchema, tableName: this.activeTable, internalId: row.DB_INTERNAL_ID, pkValue: pkVal });
                    if (res.data.code === 200) { this.$message.success('删除成功'); this.loadData(); }
                    else if (res.data.code === 503) { this.conflictList = res.data.data; this.conflictPkValue = pkVal; this.conflictType = 'delete'; this.conflictVisible = true; }
                    else { throw new Error(res.data.msg); }
                }).catch((e) => { if (e !== 'cancel') this.handleError({ message: e.message || '删除失败' }, '删除失败'); });
            }
        },

        addCondition() { if (this.tableColumns.length > 0) this.conditions.push({ field: this.tableColumns[0].COLUMN_NAME, operator: '=', value: '' }); },
        removeCondition(index) { this.conditions.splice(index, 1); },
        handleQuery() { this.currentPage = 1; this.loadData(); },
        resetFilters() { this.conditions = []; this.handleQuery(); },

        applyAutoFilter(filter) {
            if (Array.isArray(filter.value)) { this.conditions = filter.value.map(v => ({ field: filter.field, operator: '=', value: v })); this.logicalOperator = 'OR'; }
            else { this.conditions = [{ field: filter.field, operator: '=', value: filter.value }]; }
            this.$nextTick(() => { this.handleQuery(); });
        },

        resolveConflict(row) {
            this.conflictVisible = false;
            let filterValue = [];
            if (row.MY_VAL_LIST && row.MY_VAL_LIST.length > 0) filterValue = row.MY_VAL_LIST;
            else if (row.MY_VAL !== undefined && row.MY_VAL !== null) filterValue = [row.MY_VAL];
            else if (this.conflictPkValue) filterValue = [this.conflictPkValue];
            this.$emit('open-table', { connId: this.connId, schema: this.currentSchema, tableName: row.TABLE_NAME, initViewMode: 'data', filter: { field: row.COLUMN_NAME, value: filterValue } });
        },

        handleSelectionChange(val) { this.selectedRows = val; },

        async handleBatchDelete() {
            if (this.selectedRows.length === 0) return;
            const existRowsToDelete = this.selectedRows.filter(row => row.DB_INTERNAL_ID).map(row => row.DB_INTERNAL_ID);
            const newRowsToDelete = this.selectedRows.filter(row => !row.DB_INTERNAL_ID);
            this.$confirm(`确定要删除选中的 ${this.selectedRows.length} 条数据吗？`, '批量删除', { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }).then(async () => {
                if (newRowsToDelete.length > 0) {
                    newRowsToDelete.forEach(row => {
                        const idx = this.currentDataList.indexOf(row); if (idx > -1) this.currentDataList.splice(idx, 1);
                        const newIdx = this.newRows.indexOf(row); if (newIdx > -1) this.newRows.splice(newIdx, 1);
                    });
                }
                if (existRowsToDelete.length > 0) {
                    const loadingInstance = this.$loading({ lock: true, text: '正在批量删除中...', background: 'rgba(0, 0, 0, 0.7)' });
                    try {
                        const res = await this.request('post', `/delete/batch?schema=${this.currentSchema}&tableName=${this.activeTable}`, existRowsToDelete);
                        loadingInstance.close();
                        if (res.data.code === 200) { this.$message.success('批量删除成功'); this.loadData(); }
                        else if (res.data.code === 503) { this.conflictList = res.data.data; this.conflictType = 'delete'; this.conflictVisible = true; }
                        else { throw new Error(res.data.msg); }
                    } catch (e) { loadingInstance.close(); this.handleError(e, '批量删除失败'); }
                } else { this.$message.success('删除成功'); this.selectedRows = []; }
            }).catch(() => { });
        },

        formatValList(list) { if (!list || list.length === 0) return ''; const preview = list.slice(0, 3).join(', '); return list.length > 3 ? `${preview} ...等 ${list.length} 个` : preview; },
        tableRowClassName({ row }) { return row.CNT === 'MISSING' ? 'row-missing' : 'row-ref'; },

        async handleShowDDL() {
            try {
                const res = await this.request('get', '/ddl', { schema: this.currentSchema, tableName: this.activeTable });
                let rawDDL = res.data.data || '';
                this.currentDDL = rawDDL.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
                this.ddlVisible = true;
            } catch (e) { this.handleError(e, '获取DDL失败'); }
        },

        // --- 设计器相关方法 ---
        async handleDesignTable() {
            try {
                const colRes = await this.request('get', '/columns', { schema: this.currentSchema, tableName: this.activeTable });
                const cols = colRes.data.data;
                this.originalColumns = JSON.parse(JSON.stringify(cols.map(c => {
                    let len = '';
                    let scale = '';
                    const type = c.DATA_TYPE ? c.DATA_TYPE.toUpperCase() : '';

                    if (['NUMBER', 'DECIMAL', 'NUMERIC', 'FLOAT', 'DOUBLE', 'REAL'].includes(type)) {
                        len = c.DATA_PRECISION !== null ? String(c.DATA_PRECISION) : '';
                        scale = c.DATA_SCALE !== null ? String(c.DATA_SCALE) : '';
                    } else if (NO_LENGTH_TYPES.includes(type)) {
                        len = '';
                        scale = '';
                    } else {
                        len = c.DATA_LENGTH ? String(c.DATA_LENGTH) : '';
                        scale = '';
                    }

                    return {
                        ...c,
                        DATA_LENGTH: len,
                        DATA_SCALE: scale,
                        COMMENTS: c.COMMENTS || '',
                        DATA_DEFAULT: c.DATA_DEFAULT || '',
                        IS_PK: ['1', 1, 'Y', 'y', 'true', true].includes(c.IS_PK)
                    };
                })));
                this.designColumns = JSON.parse(JSON.stringify(this.originalColumns)).map(c => ({ ...c, _status: 'original', _originalName: c.COLUMN_NAME }));
                try {
                    const idxRes = await this.request('get', '/indexes', { schema: this.currentSchema, tableName: this.activeTable });
                    if (idxRes.data.code === 200) this.designIndexes = idxRes.data.data.map(i => ({ ...i, _status: 'original', COLUMNS: i.COLUMNS.split(',') }));
                } catch (e) { this.designIndexes = []; }
                try {
                    const fkRes = await this.request('get', '/foreign-keys', { schema: this.currentSchema, tableName: this.activeTable });
                    if (fkRes.data.code === 200) {
                        this.designForeignKeys = fkRes.data.data.map(f => ({ ...f, _status: 'original' }));
                        const refTables = [...new Set(this.designForeignKeys.map(f => f.R_TABLE_NAME).filter(t => t))];
                        refTables.forEach(t => this.loadRefColumns(t));
                    }
                } catch (e) { this.designForeignKeys = []; }
                const tablesRes = await this.request('get', '/tables', { schema: this.currentSchema });
                this.allTableNames = (tablesRes.data.data || []).map(t => t.TABLE_NAME);
                this.deleteColumnList = []; this.deleteIndexList = []; this.deleteFkList = [];
                this.designActiveTab = 'columns';
                this.designVisible = true;
            } catch (e) { this.handleError(e, '加载设计器失败'); }
        },

        async loadRefColumns(tableName) {
            if (!tableName || this.refColumnsCache[tableName]) return;
            try {
                const res = await this.request('get', '/columns', { schema: this.currentSchema, tableName: tableName });
                this.$set(this.refColumnsCache, tableName, res.data.data || []);
            } catch (e) { console.error("加载引用表列失败", e); }
        },

        handleRefTableChange(row) { row.R_COLUMN_NAME = ''; if (row.R_TABLE_NAME) this.loadRefColumns(row.R_TABLE_NAME); },
        isDiff(newVal, oldVal) { const n = (newVal === null || newVal === undefined) ? "" : String(newVal).trim(); const o = (oldVal === null || oldVal === undefined) ? "" : String(oldVal).trim(); return n !== o; },

        addDesignColumn() {
            this.designColumns.push({
                COLUMN_NAME: '',
                DATA_TYPE: 'VARCHAR2',
                DATA_LENGTH: '50',
                DATA_SCALE: '',
                DATA_DEFAULT: '',
                NULLABLE: 'Y',
                IS_PK: false,
                _status: 'new'
            });
        },
        removeDesignColumn(index, row) { if (row._status !== 'new') this.deleteColumnList.push(row.COLUMN_NAME); this.designColumns.splice(index, 1); },

        isLengthDisabled(type) { if (!type) return false; return NO_LENGTH_TYPES.includes(type.toUpperCase()); },
        isScaleDisabled(type) { if (!type) return true; return !SCALE_TYPES.includes(type.toUpperCase()); },

        getLengthPlaceholder(type) { if (!type) return ''; const t = type.toUpperCase(); if (NO_LENGTH_TYPES.includes(t)) return '无'; return '长度'; },
        handleTypeChange(row) { if (this.isLengthDisabled(row.DATA_TYPE)) { row.DATA_LENGTH = ''; } this.markModified(row); },

        markModified(row) {
            if (row._status === 'new') return;
            const original = this.originalColumns.find(o => o.COLUMN_NAME === row._originalName);
            if (!original) return;
            const isChanged = this.isDiff(row.COLUMN_NAME, original.COLUMN_NAME) ||
                this.isDiff(row.DATA_TYPE, original.DATA_TYPE) ||
                this.isDiff(row.DATA_LENGTH, original.DATA_LENGTH) ||
                this.isDiff(row.DATA_SCALE, original.DATA_SCALE) ||
                (row.IS_PK !== original.IS_PK) ||
                this.isDiff(row.NULLABLE, original.NULLABLE) ||
                this.isDiff(row.DATA_DEFAULT, original.DATA_DEFAULT) ||
                this.isDiff(row.COMMENTS, original.COMMENTS);
            row._status = isChanged ? 'modified' : 'original';
        },

        handlePkChange(row) { this.markModified(row); },
        addDesignIndex() { this.designIndexes.push({ INDEX_NAME: '', INDEX_TYPE: 'NORMAL', COLUMNS: [], _status: 'new' }); },
        removeDesignIndex(index, row) { if (row._status !== 'new') this.deleteIndexList.push(row.INDEX_NAME); this.designIndexes.splice(index, 1); },
        addDesignFk() { this.designForeignKeys.push({ CONSTRAINT_NAME: '', COLUMN_NAME: '', R_TABLE_NAME: '', R_COLUMN_NAME: '', DELETE_RULE: 'NO ACTION', _status: 'new' }); },
        removeDesignFk(index, row) { if (row._status !== 'new') this.deleteFkList.push(row.CONSTRAINT_NAME); this.designForeignKeys.splice(index, 1); },

        generateAndRunAlter() {
            let sqls = []; const s = this.currentSchema; const t = this.activeTable;
            const nameRegex = /^[a-zA-Z0-9_]+$/;
            for (const c of this.designColumns) { if (!nameRegex.test(c.COLUMN_NAME)) { this.$message.error(`列名 "${c.COLUMN_NAME}" 不合法，仅支持字母、数字和下划线。`); return; } }
            this.deleteColumnList.forEach(c => sqls.push(`ALTER TABLE "${s}"."${t}" DROP COLUMN "${c}";`));

            this.designColumns.filter(c => c._status === 'modified').forEach(c => {
                const original = this.originalColumns.find(o => o.COLUMN_NAME === c._originalName);
                if (!original) return;
                if (this.isDiff(c.COLUMN_NAME, c._originalName)) sqls.push(`ALTER TABLE "${s}"."${t}" RENAME COLUMN "${c._originalName}" TO "${c.COLUMN_NAME}";`);

                let typeLen = c.DATA_TYPE;
                if (!NO_LENGTH_TYPES.includes(c.DATA_TYPE.toUpperCase()) && c.DATA_LENGTH) {
                    if (c.DATA_SCALE && !this.isScaleDisabled(c.DATA_TYPE)) {
                        typeLen += `(${c.DATA_LENGTH},${c.DATA_SCALE})`;
                    } else {
                        typeLen += `(${c.DATA_LENGTH})`;
                    }
                }

                let modifySql = `ALTER TABLE "${s}"."${t}" MODIFY "${c.COLUMN_NAME}" ${typeLen}`;
                if (this.isDiff(c.DATA_DEFAULT, original.DATA_DEFAULT)) { modifySql += (c.DATA_DEFAULT ? ` DEFAULT ${c.DATA_DEFAULT}` : ` DEFAULT NULL`); }
                if (this.isDiff(c.NULLABLE, original.NULLABLE)) modifySql += (c.NULLABLE === 'N' ? ' NOT NULL' : ' NULL');
                const isAttrChanged = this.isDiff(c.DATA_TYPE, original.DATA_TYPE) ||
                    this.isDiff(c.DATA_LENGTH, original.DATA_LENGTH) ||
                    this.isDiff(c.DATA_SCALE, original.DATA_SCALE) ||
                    this.isDiff(c.NULLABLE, original.NULLABLE) ||
                    this.isDiff(c.DATA_DEFAULT, original.DATA_DEFAULT);
                if (isAttrChanged) sqls.push(modifySql + ';');
                if (this.isDiff(c.COMMENTS, original.COMMENTS)) sqls.push(`COMMENT ON COLUMN "${s}"."${t}"."${c.COLUMN_NAME}" IS '${c.COMMENTS}';`);
            });

            const oldPkCols = this.originalColumns.filter(c => ['1', 1, 'Y', true].includes(c.IS_PK)).map(c => c.COLUMN_NAME).sort();
            const newPkCols = this.designColumns.filter(c => c.IS_PK).map(c => c.COLUMN_NAME).sort();
            const isPkChanged = JSON.stringify(oldPkCols) !== JSON.stringify(newPkCols);
            if (isPkChanged) {
                if (oldPkCols.length > 0) sqls.push(`ALTER TABLE "${s}"."${t}" DROP PRIMARY KEY;`);
                if (newPkCols.length > 0) { const pkList = newPkCols.map(c => `"${c}"`).join(','); sqls.push(`ALTER TABLE "${s}"."${t}" ADD PRIMARY KEY (${pkList});`); }
            }

            this.designColumns.filter(c => c._status === 'new').forEach(c => {
                let l = `ALTER TABLE "${s}"."${t}" ADD "${c.COLUMN_NAME}" ${c.DATA_TYPE}`;
                if (!NO_LENGTH_TYPES.includes(c.DATA_TYPE.toUpperCase()) && c.DATA_LENGTH) {
                    if (c.DATA_SCALE && !this.isScaleDisabled(c.DATA_TYPE)) {
                        l += `(${c.DATA_LENGTH},${c.DATA_SCALE})`;
                    } else {
                        l += `(${c.DATA_LENGTH})`;
                    }
                }
                if (c.DATA_DEFAULT) l += ` DEFAULT ${c.DATA_DEFAULT}`;
                if (c.NULLABLE === 'N') l += ' NOT NULL';
                sqls.push(l + ';');
                if (c.COMMENTS) sqls.push(`COMMENT ON COLUMN "${s}"."${t}"."${c.COLUMN_NAME}" IS '${c.COMMENTS}';`);
            });

            this.deleteIndexList.forEach(idx => sqls.push(`DROP INDEX "${s}"."${idx}";`));
            this.designIndexes.filter(i => i._status === 'new').forEach(i => {
                if (!i.INDEX_NAME || i.COLUMNS.length === 0) return;
                const cols = i.COLUMNS.map(c => `"${c}"`).join(',');
                const unique = i.INDEX_TYPE === 'UNIQUE' ? 'UNIQUE' : (i.INDEX_TYPE === 'BITMAP' ? 'BITMAP' : '');
                sqls.push(`CREATE ${unique} INDEX "${i.INDEX_NAME}" ON "${s}"."${t}" (${cols});`);
            });

            this.deleteFkList.forEach(fk => sqls.push(`ALTER TABLE "${s}"."${t}" DROP CONSTRAINT "${fk}";`));
            this.designForeignKeys.filter(f => f._status === 'new').forEach(f => {
                if (!f.CONSTRAINT_NAME || !f.COLUMN_NAME || !f.R_TABLE_NAME || !f.R_COLUMN_NAME) return;
                let sql = `ALTER TABLE "${s}"."${t}" ADD CONSTRAINT "${f.CONSTRAINT_NAME}" FOREIGN KEY ("${f.COLUMN_NAME}") REFERENCES "${s}"."${f.R_TABLE_NAME}"("${f.R_COLUMN_NAME}")`;
                if (f.DELETE_RULE === 'CASCADE') sql += ' ON DELETE CASCADE'; else if (f.DELETE_RULE === 'SET NULL') sql += ' ON DELETE SET NULL';
                sqls.push(sql + ';');
            });

            if (!sqls.length) return this.$message.info('无结构变更');
            this.generatedSql = sqls.join('\n');
            this.sqlConfirmVisible = true;
        },

        async executeAlter() {
            this.altering = true;
            try {
                const statements = this.generatedSql.split(';').map(s => s.trim()).filter(s => s);
                const res = await this.request('post', '/execute/batch', { sqls: statements });
                if (res.data.code === 200) { this.$message.success('修改成功'); this.sqlConfirmVisible = false; this.designVisible = false; if (this.viewMode === 'relation') this.loadRelations(); else this.loadData(); }
                else { throw new Error(res.data.msg); }
            } catch (e) { this.handleError(e, '表结构变更失败'); } finally { this.altering = false; }
        },

        indexMethod(index) { return (this.currentPage - 1) * this.pageSize + index + 1; },
        handleSortChange({ prop, order }) { if (!prop) return; const sortedData = [...this.currentDataList]; sortedData.sort((a, b) => { let valA = a[prop]; let valB = b[prop]; if (valA == null) valA = ''; if (valB == null) valB = ''; if (valA > valB) return order === 'ascending' ? 1 : -1; if (valA < valB) return order === 'ascending' ? -1 : 1; return 0; }); this.currentDataList = sortedData; },
        handleSizeChange(v) { this.pageSize = v; this.currentPage = 1; this.loadData(); },
        handleCurrentChange(v) { this.currentPage = v; this.loadData(); },

        destroyGraph() { if (this.graphInstance) { this.graphInstance.destroy(); this.graphInstance = null; } const c = this.$refs.relationCanvas; if (c) c.innerHTML = ''; },

        handleShowAllChange(val) { if (val) { if (this.graphNodeIds.length > 0) this.expandedTableList = [...this.graphNodeIds]; } else { this.expandedTableList = []; } this.loadRelations(); },
        async loadRelations() { this.destroyGraph(); try { const res = await this.request('get', '/er-data', { schema: this.currentSchema, tableName: this.activeTable, showAll: this.showAllColumns, expandedTables: this.expandedTableList.join(',') }); if (this.viewMode === 'relation') { this.graphNodeIds = res.data.data.nodes.map(n => n.id); this.checkAutoExpandState(); this.$nextTick(() => this.initGraph(res.data.data)); } } catch (e) { } },
        checkAutoExpandState() { if (this.graphNodeIds.length > 0) { const allExpanded = this.graphNodeIds.every(id => this.expandedTableList.includes(id)); if (this.showAllColumns !== allExpanded) this.showAllColumns = allExpanded; } },
        handleGraphZoom(ratio) { if (!this.graphInstance) return; const zoom = this.graphInstance.getZoom(); this.graphInstance.zoomTo(zoom * ratio, { x: this.$refs.relationCanvas.offsetWidth / 2, y: this.$refs.relationCanvas.offsetHeight / 2 }); },
        handleGraphFit() { if (!this.graphInstance) return; this.graphInstance.fitView(); },
        initGraph(data) {
            const container = this.$refs.relationCanvas; if (!container) return; container.innerHTML = '';
            const nodeCount = data.nodes.length; const ranksep = nodeCount > 5 ? 150 : 100;
            this.graphInstance = new G6.Graph({ container, width: container.offsetWidth, height: container.offsetHeight, fitView: true, fitViewPadding: 40, renderer: 'canvas', layout: { type: 'dagre', rankdir: 'LR', nodesep: 50, ranksep: ranksep }, defaultNode: { type: 'db-table', anchorPoints: [[0, 0.5], [1, 0.5]] }, defaultEdge: { type: 'cubic-horizontal', style: { stroke: '#A3B1BF', lineWidth: 2, endArrow: true }, labelCfg: { autoRotate: true, style: { fontSize: 10, fill: '#aaa' } } }, modes: { default: ['drag-canvas', 'zoom-canvas', 'drag-node'] } });
            this.graphInstance.data(JSON.parse(JSON.stringify(data))); this.graphInstance.render();
            this.graphInstance.on('node:click', (e) => { const shapeName = e.target.get('name'); const nodeId = e.item.getModel().id; if (shapeName === 'expand-text') { if (!this.expandedTableList.includes(nodeId)) { this.expandedTableList.push(nodeId); this.loadRelations(); } } else if (shapeName === 'collapse-text') { const index = this.expandedTableList.indexOf(nodeId); if (index > -1) { this.expandedTableList.splice(index, 1); this.showAllColumns = false; this.loadRelations(); } } });
            this.graphInstance.on('node:dblclick', (e) => { const t = e.item.getModel().label; if (t !== this.activeTable) this.$emit('open-table', { connId: this.connId, schema: this.currentSchema, tableName: t, initViewMode: 'data' }); });
        },

        // [辅助] 判断类型
        isBinaryLob(type) {
            return type && BINARY_LOB_TYPES.includes(type.toUpperCase());
        },
        isTextLob(type) {
            return type && TEXT_LOB_TYPES.includes(type.toUpperCase());
        },
        isLob(type) {
            return this.isBinaryLob(type) || this.isTextLob(type);
        },

        // [新增] 触发文件选择
        triggerFileSelect(rowIndex, colName) {
            const el = document.getElementById(`file-${rowIndex}-${colName}`);
            if (el) el.click();
        },

        // [新增] 处理行内文件上传 (转Base64)
        handleInlineFileUpload(event, row, colName) {
            const file = event.target.files[0];
            if (!file) return;

            // 限制一下大小，比如 10MB，防止浏览器卡死
            if (file.size > 10 * 1024 * 1024) {
                this.$message.warning("文件过大，建议保存行数据后使用单独的上传功能");
                return;
            }

            const reader = new FileReader();
            reader.onload = (e) => {
                // 将 Base64 赋值给 row，后端会识别并处理
                this.$set(row, colName, e.target.result);

                // 标记该行已被修改 (如果是现有行)
                if (row.DB_INTERNAL_ID) {
                    this.modifiedRows.add(row.DB_INTERNAL_ID);
                    this.modifiedRows = new Set(this.modifiedRows); // 触发响应式
                }
            };
            reader.readAsDataURL(file);
        },

        // [新增] 清除行内文件
        clearInlineFile(row, colName) {
            this.$set(row, colName, null);
            const el = document.getElementById(`file-${row._tempId}-${colName}`);
            if (el) el.value = '';
        },

        handlePreviewLob(row, col) {
            this.currentLobRow = row;
            this.currentLobCol = col;
            this.previewLoading = true;
            this.previewContent = '';
            this.previewUrl = '';

            // 判断类型
            const type = col.DATA_TYPE.toUpperCase();
            if (type === 'IMAGE' || type === 'BLOB') {
                this.previewType = 'image';
                this.previewUrl = this.getLobUrl(row, col, false);
            } else {
                this.previewType = 'text';
                this.fetchLobText(row, col);
            }
            this.previewVisible = true;
            this.previewLoading = false;
        },
        getLobUrl(row, col, download) {
            const baseUrl = process.env.VUE_APP_BASE_API || '/api';
            return `${baseUrl}/db/lob/preview?schema=${this.currentSchema}&tableName=${this.activeTable}&colName=${col.COLUMN_NAME}&rowId=${row.DB_INTERNAL_ID}&download=${download}`;
        },
        async fetchLobText(row, col) {
            this.previewLoading = true;
            try {
                const res = await this.request('get', '/lob/preview', {
                    schema: this.currentSchema,
                    tableName: this.activeTable,
                    colName: col.COLUMN_NAME,
                    rowId: row.DB_INTERNAL_ID,
                    download: false
                });
                if (typeof res.data === 'string') {
                    this.previewContent = res.data;
                } else {
                    this.previewContent = JSON.stringify(res.data, null, 2);
                }
            } catch (e) {
                this.previewContent = "加载失败: " + e.message;
            } finally {
                this.previewLoading = false;
            }
        },
        handleDownloadLob(row, col) {
            const url = this.getLobUrl(row, col, true);
            window.open(url, '_blank');
        },
        handleUploadLob(row, col) {
            this.currentLobRow = row;
            this.currentLobCol = col;
            this.$refs.lobFileInput.value = null;
            this.$refs.lobFileInput.click();
        },
        async handleFileChange(e) {
            const file = e.target.files[0];
            if (!file) return;

            const formData = new FormData();
            formData.append('file', file);
            formData.append('schema', this.currentSchema);
            formData.append('tableName', this.activeTable);
            formData.append('colName', this.currentLobCol.COLUMN_NAME);
            formData.append('rowId', this.currentLobRow.DB_INTERNAL_ID);

            const loadingInstance = this.$loading({ lock: true, text: '正在上传...', background: 'rgba(0, 0, 0, 0.7)' });

            try {
                const res = await this.request('post', '/lob/upload', formData);
                loadingInstance.close();
                if (res.data.code === 200) {
                    this.$message.success('上传成功');
                    this.loadData();
                } else {
                    this.$message.error(res.data.msg);
                }
            } catch (e) {
                loadingInstance.close();
                this.handleError(e, '上传失败');
            }
        },
        downloadCurrentLob() {
            if (this.currentLobRow && this.currentLobCol) {
                this.handleDownloadLob(this.currentLobRow, this.currentLobCol);
            }
        }
    },
    beforeDestroy() { this.destroyGraph(); }
};
</script>

<style scoped>
/* 样式保持不变 */
.table-detail-wrapper {
    height: 100%;
    display: flex;
    flex-direction: column;
    background: #f5f7fa;
}

.workspace-header {
    background: #fff;
    border-bottom: 1px solid #e4e7ed;
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 20px;
    box-shadow: 0 1px 4px rgba(0, 21, 41, .08);
    z-index: 10;
}

.content-area {
    flex: 1;
    padding: 15px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

.view-container {
    height: 100%;
    display: flex;
    flex-direction: column;
    gap: 12px;
    background: #f5f7fa;
}

.full-height {
    height: 100%;
}

.filter-card {
    background: #fff;
    padding: 12px;
    border-radius: 4px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
    flex-shrink: 0;
}

.compact-query-panel {
    display: flex;
    flex-direction: column;
}

.query-actions-top-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 8px;
}

.panel-title {
    font-weight: bold;
    color: #303133;
    font-size: 14px;
}

.query-conditions-scroll-area {
    max-height: 120px;
    overflow-y: auto;
    padding: 5px;
    background: #fafafa;
    border-radius: 4px;
    border: 1px dashed #dcdfe6;
}

.query-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(400px, 1fr));
    gap: 10px;
}

.query-item {
    display: flex;
    align-items: center;
    gap: 8px;
}

.cond-index {
    color: #909399;
    font-size: 12px;
    width: 15px;
}

.table-card {
    flex: 1;
    background: #fff;
    border-radius: 4px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
    padding: 12px;
    display: flex;
    flex-direction: column;
    min-height: 0;
}

.data-toolbar {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 12px;
}

.data-stats {
    font-size: 13px;
    color: #606266;
    background: #f4f4f5;
    padding: 4px 10px;
    border-radius: 4px;
}

.table-wrapper {
    flex: 1;
    min-height: 0;
}

.pagination-bar {
    padding-top: 12px;
    display: flex;
    justify-content: flex-end;
}

.danger-text {
    color: #F56C6C;
}

::v-deep .custom-table th>.cell {
    display: flex !important;
    align-items: center;
    justify-content: flex-start;
    line-height: 1.2;
}

.custom-header {
    display: inline-flex;
    flex-direction: column;
    vertical-align: middle;
    overflow: hidden;
    text-overflow: ellipsis;
    max-width: calc(100% - 24px);
}

::v-deep .custom-table .caret-wrapper {
    margin-left: 4px;
    flex-shrink: 0;
}

::v-deep .custom-table th {
    background-color: #eef1f6 !important;
    color: #303133;
    font-weight: 600;
    height: 40px;
    padding: 4px 0;
}

::v-deep .custom-table td {
    padding: 6px 0;
}

::v-deep .el-table--striped .el-table__body tr.el-table__row--striped td {
    background: #fafafa;
}

::v-deep .el-table--border,
.el-table--group {
    border-color: #dcdfe6;
}

::v-deep .el-table td,
::v-deep .el-table th.is-leaf {
    border-bottom: 1px solid #dcdfe6;
}

::v-deep .el-table--border td,
::v-deep .el-table--border th {
    border-right: 1px solid #dcdfe6;
}

.col-name {
    font-size: 13px;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.col-comment {
    font-size: 12px;
    color: #909399;
    font-weight: normal;
    transform: scale(0.95);
    transform-origin: left;
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}

.inline-input-box {
    width: 100%;
    padding: 0 2px;
}

::v-deep .inline-input-box .el-input__inner {
    height: 26px;
    line-height: 26px;
    padding: 0 5px;
    border-radius: 0;
    border: 1px solid #409EFF;
}

.conflict-alert {
    padding: 12px 16px;
    border-radius: 4px;
    font-size: 14px;
    margin-bottom: 15px;
    display: flex;
    align-items: center;
}

.conflict-missing {
    background-color: #fef0f0;
    color: #f56c6c;
    border: 1px solid #fde2e2;
}

.conflict-ref {
    background-color: #fdf6ec;
    color: #e6a23c;
    border: 1px solid #faecd8;
}

.conflict-mixed {
    background-color: #f4f4f5;
    color: #909399;
    border: 1px solid #dcdfe6;
    font-weight: bold;
}

::v-deep .el-table .row-missing {
    background: #fffafa;
}

::v-deep .el-table .row-ref {
    background: #fcfcfc;
}

.relation-view-container {
    height: 100%;
    display: flex;
    flex-direction: column;
    background: #f5f7fa;
    position: relative;
    overflow: hidden;
}

.relation-toolbar {
    padding: 10px 20px;
    background: #fff;
    border-bottom: 1px solid #eee;
    display: flex;
    justify-content: space-between;
    align-items: center;
    z-index: 10;
}

.legend-box {
    display: flex;
    align-items: center;
    gap: 15px;
    font-size: 12px;
    color: #666;
}

.dot {
    width: 8px;
    height: 8px;
    display: inline-block;
    border-radius: 50%;
    margin-right: 5px;
}

.dot.active {
    background: #409EFF;
}

.dot.normal {
    background: #DCDFE6;
}

.canvas-wrapper {
    width: 100%;
    height: 100%;
    background: #f5f7fa;
    overflow: hidden;
    position: relative;
}

.g6-canvas-box {
    width: 100%;
    height: 100%;
    display: block;
}

.sql-box {
    padding: 0;
    background: #fff;
    border-radius: 4px;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
    display: flex;
    flex-direction: column;
    overflow: hidden;
    height: 300px;
}

.sql-result {
    flex: 1;
    margin-top: 10px;
    overflow: auto;
    background: #fff;
    padding: 10px;
    border-radius: 4px;
}

.error-dialog-width {
    max-width: 500px;
}

.table-comment-display {
    color: #909399;
    font-weight: normal;
    font-size: 12px;
    margin-left: 8px;
    cursor: default;
    max-width: 200px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    display: inline-block;
    vertical-align: middle;
}

.edit-comment-btn {
    margin-left: 4px;
    padding: 2px;
    color: #409EFF;
}

.edit-comment-btn:hover {
    background-color: #ecf5ff;
    border-radius: 4px;
}

.preview-dialog {
    border-radius: 8px;
    overflow: hidden;
}

/* 增加样式 */
.placeholder-text {
    color: #C0C4CC;
    font-style: italic;
    font-size: 12px;
}

/* 优化大文本输入体验 */
.inline-input-box textarea {
    font-family: Consolas, monospace;
    line-height: 1.4;
    resize: none;
    /* 禁用手动调整大小，使用 autosize */
}
</style>