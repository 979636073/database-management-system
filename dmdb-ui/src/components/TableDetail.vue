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
                        <el-radio-button label="sql"><i class="el-icon-cpu"></i> SQL终端</el-radio-button>
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
                                <el-button size="mini" icon="el-icon-plus" @click="handleAddRow">插入行</el-button>
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
                                            <div v-if="!isReadOnly && isCellEditing(scope.row, col.COLUMN_NAME)"
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

                <div v-else-if="viewMode === 'sql'" key="view-sql" class="full-height view-container">
                    <div class="sql-box"
                        style="height: 300px; padding: 0; border: 1px solid #dcdfe6; overflow: hidden; display: flex; flex-direction: column;">
                        <SqlEditor v-model="customSql" language="sql" style="flex: 1; width: 100%;" />
                    </div>
                    <div style="text-align: right; padding: 10px 0;"><el-button type="success" size="small"
                            @click="runSql" icon="el-icon-video-play">执行 SQL</el-button></div>
                    <div class="sql-result" v-if="sqlResult.length">
                        <el-divider content-position="left">结果预览</el-divider>
                        <el-table :data="sqlResult" border height="100%" size="mini" stripe><el-table-column
                                v-for="(val, key) in sqlResult[0]" :key="key" :prop="key" :label="key"
                                show-overflow-tooltip></el-table-column></el-table>
                    </div>
                </div>
            </el-main>
        </el-container>

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

        <el-dialog :title="conflictTitle" :visible.sync="conflictVisible" width="750px" append-to-body
            :close-on-click-modal="false">
            <div class="conflict-alert"
                :style="{ backgroundColor: isParentMissing ? '#fef0f0' : '#fdf6ec', color: isParentMissing ? '#F56C6C' : '#e6a23c', borderColor: isParentMissing ? '#fde2e2' : '#faecd8' }">
                <i class="el-icon-warning" style="margin-right: 8px;"></i>
                {{ conflictMessage }}
            </div>
            <el-table :data="conflictList" border size="small" style="margin-top: 15px;">
                <el-table-column :label="isParentMissing ? '目标主表名' : '引用表名'" width="220" prop="TABLE_NAME">
                    <template slot-scope="scope"><el-tag size="mini" type="info">{{ scope.row.TABLE_NAME
                            }}</el-tag></template>
                </el-table-column>
                <el-table-column :label="isParentMissing ? '目标主键列' : '关联字段'" width="180"
                    prop="COLUMN_NAME"></el-table-column>
                <el-table-column :label="isParentMissing ? '状态' : '冲突记录数'" align="center" width="120">
                    <template slot-scope="scope">
                        <span v-if="scope.row.CNT === 'MISSING'" style="color:#F56C6C;font-weight:bold;">主键缺失</span>
                        <span v-else style="color:#F56C6C;font-weight:bold;">{{ scope.row.CNT }}</span>
                    </template>
                </el-table-column>
                <el-table-column label="操作" align="center">
                    <template slot-scope="scope">
                        <el-button type="primary" plain size="mini" @click="resolveConflict(scope.row)">
                            去处理 <i class="el-icon-right"></i>
                        </el-button>
                    </template>
                </el-table-column>
            </el-table>
            <div slot="footer"><el-button @click="conflictVisible = false">关闭</el-button></div>
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
                        <el-table-column label="类型" width="120"><template slot-scope="scope"><el-select
                                    v-model="scope.row.DATA_TYPE" @change="markModified(scope.row)" size="mini"
                                    filterable allow-create><el-option value="VARCHAR2"></el-option><el-option
                                        value="NUMBER"></el-option><el-option
                                        value="DATE"></el-option></el-select></template></el-table-column>
                        <el-table-column label="长度" width="100"><template slot-scope="scope"><el-input
                                    v-model="scope.row.DATA_LENGTH" @change="markModified(scope.row)"
                                    size="mini"></el-input></template></el-table-column>
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

// G6 logic remains same
G6.registerNode('db-table', {
    draw: (cfg, group) => {
        const { label, tableComment, columns = [], isCenter, isTruncated, totalColCount } = cfg;
        const width = 300;
        const headerHeight = 50;
        const rowHeight = 28;
        const contentHeight = columns.length * rowHeight;

        let footerHeight = 0;
        if (isTruncated) footerHeight = 24;
        else if (totalColCount > 10) footerHeight = 24;

        const height = headerHeight + contentHeight + footerHeight + 8;

        group.addShape('rect', { attrs: { x: 0, y: 0, width, height, fill: '#fff', stroke: isCenter ? '#409EFF' : '#DCDFE6', lineWidth: 1, radius: 4 }, name: 'container-shape' });
        group.addShape('rect', { attrs: { x: 0, y: 0, width, height: headerHeight, fill: isCenter ? '#409EFF' : '#F2F6FC', radius: [4, 4, 0, 0] }, name: 'header-shape' });

        group.addShape('text', { attrs: { x: 10, y: 20, text: label, fill: isCenter ? '#fff' : '#333', fontSize: 13, fontWeight: 'bold', textBaseline: 'middle' }, name: 'title-text' });
        if (tableComment) {
            group.addShape('text', { attrs: { x: 10, y: 38, text: tableComment, fill: isCenter ? '#eee' : '#909399', fontSize: 11, textBaseline: 'middle' }, name: 'comment-text' });
        }

        columns.forEach((col, i) => {
            const y = headerHeight + (i * rowHeight) + (rowHeight / 2);
            group.addShape('text', { attrs: { x: 28, y: y, text: col.COLUMN_NAME, fill: col.IS_PK ? '#E6A23C' : '#333', fontSize: 12, textBaseline: 'middle', fontWeight: col.IS_PK ? 'bold' : 'normal' } });
            if (col.IS_PK) {
                group.addShape('circle', { attrs: { x: 14, y: y, r: 3, fill: '#E6A23C' } });
            }
            if (col.COMMENTS) {
                let comment = col.COMMENTS;
                if (comment.length > 10) comment = comment.substring(0, 10) + '...';
                group.addShape('text', { attrs: { x: 160, y: y, text: comment, fill: '#909399', fontSize: 11, textBaseline: 'middle' } });
            }
        });

        const y = headerHeight + contentHeight + 12;
        if (isTruncated) {
            group.addShape('text', { attrs: { x: width / 2, y: y, text: `... (共 ${totalColCount} 列，点击展开)`, fill: '#909399', fontSize: 11, textAlign: 'center', textBaseline: 'middle', cursor: 'pointer' }, name: 'expand-text' });
        } else if (totalColCount > 10) {
            group.addShape('text', { attrs: { x: width / 2, y: y, text: '⬆ 点击折叠', fill: '#409EFF', fontSize: 11, textAlign: 'center', textBaseline: 'middle', cursor: 'pointer' }, name: 'collapse-text' });
        }

        return group.get('children')[0];
    }
});

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
            customSql: '', sqlResult: [],
            ddlVisible: false, currentDDL: '',

            editViewVisible: false, viewSql: '',
            isSimpleView: true,

            // 标记是否从冲突跳转
            jumpFromConflict: false,

            // 【新增】选中的行
            selectedRows: [],

            designVisible: false, designActiveTab: 'columns',
            designColumns: [], originalColumns: [], deleteColumnList: [],
            designIndexes: [], deleteIndexList: [],
            designForeignKeys: [], deleteFkList: [],
            allTableNames: [], refColumnsCache: {},

            showAllColumns: false, expandedTableList: [], graphNodeIds: [],
            sqlConfirmVisible: false, generatedSql: '', altering: false
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
        isParentMissing() {
            return this.conflictList.length > 0 && this.conflictList[0].CNT === 'MISSING';
        },
        conflictTitle() {
            if (this.isParentMissing) return '⚠️ 保存失败：目标主键缺失';
            return this.conflictType === 'delete' ? '⚠️ 无法删除：存在关联引用' : '⚠️ 无法修改：存在关联引用';
        },
        conflictMessage() {
            if (this.isParentMissing) return '您输入的 外键值 在目标主表中不存在。请先在主表中创建该数据，或修改为已存在的值。';
            return `该记录被以下 ${this.conflictList.length} 张表引用，请先处理这些关联数据。`;
        }
    },

    mounted() { this.initView(); },

    watch: {
        initialFilter: {
            immediate: true, deep: true,
            handler(val) {
                if (val && val.field && val.value !== undefined) {
                    if (this.viewMode !== 'data') this.handleViewModeSwitch('data');
                    // 标记从外部跳转
                    this.jumpFromConflict = true;
                    this.applyAutoFilter(val);
                }
            }
        },
        tableName: {
            immediate: true,
            handler(val) {
                if (val) {
                    this.activeTable = val;
                    this.currentSchema = this.schema;
                    this.currentPage = 1;
                    this.expandedTableList = [];
                    this.showAllColumns = false;
                    this.selectedRows = []; // 清空选中

                    // 如果有跳转条件，跳过普通加载
                    if (this.initialFilter && this.initialFilter.field) {
                        return;
                    }
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

        async loadData() {
            this.resetEditState();
            this.selectedRows = [];
            this.loading = true;
            try {
                const colRes = await this.request('get', '/columns', { schema: this.currentSchema, tableName: this.activeTable });
                this.tableColumns = colRes.data.data || [];
                let list = []; let total = 0;
                const params = { schema: this.currentSchema, tableName: this.activeTable, page: this.currentPage, size: this.pageSize };
                if (this.conditions.length > 0) {
                    const filterRes = await this.request('post', '/filter', { ...params, logic: this.logicalOperator, conditions: this.conditions });
                    list = filterRes.data.data.list || []; total = filterRes.data.data.total || 0; if (filterRes.data.data.isView) { this.isSimpleView = filterRes.data.data.isSimpleView; }
                } else {
                    const dataRes = await this.request('get', '/data', params);
                    list = dataRes.data.data.list || []; total = dataRes.data.data.total || 0; if (dataRes.data.data.isView) { this.isSimpleView = dataRes.data.data.isSimpleView; }
                }
                this.currentDataList = list; this.totalCount = total; this.originalDataMap = {}; list.forEach(row => { if (row.DB_INTERNAL_ID) this.originalDataMap[row.DB_INTERNAL_ID] = JSON.parse(JSON.stringify(row)); });

                // 【核心优化】从冲突跳转来且无数据 -> 批量自动插入
                if (this.jumpFromConflict && this.totalCount === 0) {
                    this.jumpFromConflict = false;
                    this.$nextTick(() => {
                        // 遍历所有过滤条件，如果存在多个 OR 条件，生成多行
                        if (this.conditions.length > 0 && this.logicalOperator === 'OR') {
                            let addedCount = 0;
                            this.conditions.forEach(cond => {
                                if (cond.operator === '=' && cond.value) {
                                    this.handleAddRow(cond.value, cond.field);
                                    addedCount++;
                                }
                            });
                            if (addedCount > 0) {
                                this.$message({ message: `已为您批量创建 ${addedCount} 条新行并填入缺失的主键，请补充其他信息。`, type: 'success', duration: 6000, showClose: true });
                            }
                        } else {
                            // 单条件情况
                            this.handleAddRow();
                            this.$message({ message: '未找到指定主键记录，已自动为您创建新行并填入主键，请补充其他信息。', type: 'success', duration: 5000, showClose: true });
                        }
                    });
                } else if (this.totalCount > 0) {
                    this.jumpFromConflict = false;
                }
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
                if (res.data.code === 200) {
                    this.$message.success('视图修改成功');
                    this.editViewVisible = false;
                    this.loadData();
                } else {
                    throw new Error(res.data.msg);
                }
            } catch (e) {
                this.handleError(e, '修改视图失败');
            } finally {
                this.altering = false;
            }
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

        // 【核心修改】支持参数化填充
        handleAddRow(prefillValue = null, prefillField = null) {
            const newRow = { _tempId: 'NEW_' + Date.now() + Math.random().toString(36).substr(2, 5) };
            this.tableColumns.forEach(c => newRow[c.COLUMN_NAME] = null);

            // 优先使用传入的填充值
            if (prefillValue !== null && prefillField) {
                newRow[prefillField] = prefillValue;
            }
            // 否则使用当前的筛选条件 (单条件)
            else if (this.conditions.length === 1 && this.conditions[0].operator === '=') {
                const fieldName = this.conditions[0].field;
                const val = this.conditions[0].value;
                const matchKey = Object.keys(newRow).find(k => k.toUpperCase() === fieldName.toUpperCase());
                if (matchKey && val !== undefined && val !== null) {
                    newRow[matchKey] = val;
                }
            }
            this.currentDataList.unshift(newRow);
            this.newRows.push(newRow);
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

            const insertList = this.newRows.map(row => {
                const clean = this.getCleanRowData(row);
                delete clean.DB_INTERNAL_ID;
                return clean;
            });

            const updateList = Array.from(this.modifiedRows).map(id => {
                const row = this.currentDataList.find(r => r.DB_INTERNAL_ID === id);
                return row ? this.getCleanRowData(row) : null;
            }).filter(r => r !== null);

            if (insertList.length === 0 && updateList.length === 0) {
                this.$message.info("没有检测到有效变更");
                this.saving = false;
                return;
            }

            const loadingInstance = this.$loading({ lock: true, text: '正在批量保存中...', background: 'rgba(0, 0, 0, 0.7)' });

            try {
                const res = await this.request('post', `/save/batch?schema=${this.currentSchema}&tableName=${this.activeTable}`, {
                    insertList,
                    updateList
                });

                loadingInstance.close();

                if (res.data.code === 200) {
                    this.$message.success('批量保存成功！');
                    this.loadData();
                } else if (res.data.code === 503) {
                    this.conflictList = res.data.data;
                    this.conflictType = 'save';
                    this.conflictVisible = true;
                } else {
                    throw new Error(res.data.msg);
                }
            } catch (e) {
                loadingInstance.close();
                this.handleError(e, '保存失败');
            } finally {
                this.saving = false;
            }
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
                }).catch((e) => {
                    if (e !== 'cancel') this.handleError({ message: e.message || '删除失败' }, '删除失败');
                });
            }
        },

        // 【新增】选区变更
        handleSelectionChange(val) {
            this.selectedRows = val;
        },

        // 【新增】批量删除方法
        async handleBatchDelete() {
            if (this.selectedRows.length === 0) return;
            const existRowsToDelete = this.selectedRows.filter(row => row.DB_INTERNAL_ID).map(row => row.DB_INTERNAL_ID);
            const newRowsToDelete = this.selectedRows.filter(row => !row.DB_INTERNAL_ID);

            this.$confirm(`确定要删除选中的 ${this.selectedRows.length} 条数据吗？`, '批量删除', { type: 'warning' }).then(async () => {
                // 1. 先删本地新行
                newRowsToDelete.forEach(row => {
                    const idx = this.currentDataList.indexOf(row);
                    if (idx > -1) this.currentDataList.splice(idx, 1);
                    const nIdx = this.newRows.indexOf(row);
                    if (nIdx > -1) this.newRows.splice(nIdx, 1);
                });

                // 2. 再调后端删持久化行
                if (existRowsToDelete.length > 0) {
                    const loadingInstance = this.$loading({ lock: true, text: '正在批量删除...', background: 'rgba(0,0,0,0.7)' });
                    try {
                        const res = await this.request('post', `/delete/batch?schema=${this.currentSchema}&tableName=${this.activeTable}`, existRowsToDelete);
                        loadingInstance.close();

                        if (res.data.code === 200) {
                            this.$message.success('批量删除成功');
                            this.loadData();
                        } else if (res.data.code === 503) {
                            this.conflictList = res.data.data;
                            this.conflictType = 'delete';
                            this.conflictVisible = true;
                        } else {
                            throw new Error(res.data.msg);
                        }
                    } catch (e) {
                        loadingInstance.close();
                        this.handleError(e, '批量删除失败');
                    }
                } else {
                    this.$message.success('删除成功');
                }
            }).catch(() => { });
        },

        addCondition() { if (this.tableColumns.length > 0) this.conditions.push({ field: this.tableColumns[0].COLUMN_NAME, operator: '=', value: '' }); },
        removeCondition(index) { this.conditions.splice(index, 1); },
        handleQuery() { this.currentPage = 1; this.loadData(); },
        resetFilters() { this.conditions = []; this.handleQuery(); },
        // 【核心修复】自动筛选：支持多值 (OR)
        applyAutoFilter(filter) {
            if (Array.isArray(filter.value)) {
                this.conditions = filter.value.map(v => ({
                    field: filter.field,
                    operator: '=',
                    value: v
                }));
                this.logicalOperator = 'OR';
            } else {
                this.conditions = [{ field: filter.field, operator: '=', value: filter.value }];
            }
            this.$nextTick(() => { this.handleQuery(); });
        },

        // 【核心修复】处理聚合冲突列表
        resolveConflict(row) {
            this.conflictVisible = false;

            // 优先使用聚合值列表
            let filterValue = [];
            if (row.MY_VAL_LIST && row.MY_VAL_LIST.length > 0) {
                filterValue = row.MY_VAL_LIST;
            } else if (row.MY_VAL !== undefined && row.MY_VAL !== null) {
                filterValue = [row.MY_VAL];
            } else if (this.conflictPkValue) {
                filterValue = [this.conflictPkValue];
            }

            this.$emit('open-table', {
                connId: this.connId,
                schema: this.currentSchema,
                tableName: row.TABLE_NAME,
                initViewMode: 'data',
                filter: {
                    field: row.COLUMN_NAME,
                    value: filterValue // 传数组
                }
            });
        },
        async handleShowDDL() {
            try {
                const res = await this.request('get', '/ddl', { schema: this.currentSchema, tableName: this.activeTable });
                let rawDDL = res.data.data || '';
                this.currentDDL = rawDDL.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&amp;/g, '&');
                this.ddlVisible = true;
            } catch (e) { this.handleError(e, '获取DDL失败'); }
        },

        // ... Design table related methods (omitted for brevity, keep as is) ...
        async handleDesignTable() {
            try {
                const colRes = await this.request('get', '/columns', { schema: this.currentSchema, tableName: this.activeTable });
                const cols = colRes.data.data;
                this.originalColumns = JSON.parse(JSON.stringify(cols.map(c => ({
                    ...c,
                    DATA_LENGTH: c.DATA_LENGTH ? String(c.DATA_LENGTH) : '',
                    COMMENTS: c.COMMENTS || '',
                    DATA_DEFAULT: c.DATA_DEFAULT || '',
                    IS_PK: ['1', 1, 'Y', 'y', 'true', true].includes(c.IS_PK)
                }))));

                this.designColumns = JSON.parse(JSON.stringify(this.originalColumns)).map(c => ({ ...c, _status: 'original', _originalName: c.COLUMN_NAME }));

                try {
                    const idxRes = await this.request('get', '/indexes', { schema: this.currentSchema, tableName: this.activeTable });
                    if (idxRes.data.code === 200) {
                        this.designIndexes = idxRes.data.data.map(i => ({ ...i, _status: 'original', COLUMNS: i.COLUMNS.split(',') }));
                    }
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

                this.deleteColumnList = [];
                this.deleteIndexList = [];
                this.deleteFkList = [];
                this.designActiveTab = 'columns';
                this.designVisible = true;
            } catch (e) { this.handleError(e, '加载设计器失败'); }
        },

        async loadRefColumns(tableName) {
            if (!tableName || this.refColumnsCache[tableName]) return;
            try {
                const res = await this.request('get', '/columns', { schema: this.currentSchema, tableName: tableName });
                this.$set(this.refColumnsCache, tableName, res.data.data || []);
            } catch (e) {
                console.error("加载引用表列失败", e);
            }
        },

        handleRefTableChange(row) {
            row.R_COLUMN_NAME = '';
            if (row.R_TABLE_NAME) {
                this.loadRefColumns(row.R_TABLE_NAME);
            }
        },

        isDiff(newVal, oldVal) {
            const n = (newVal === null || newVal === undefined) ? "" : String(newVal).trim();
            const o = (oldVal === null || oldVal === undefined) ? "" : String(oldVal).trim();
            return n !== o;
        },

        addDesignColumn() { this.designColumns.push({ COLUMN_NAME: 'NEW_COL', DATA_TYPE: 'VARCHAR2', DATA_LENGTH: '50', NULLABLE: 'Y', IS_PK: false, _status: 'new' }); },

        removeDesignColumn(index, row) { if (row._status !== 'new') this.deleteColumnList.push(row.COLUMN_NAME); this.designColumns.splice(index, 1); },

        markModified(row) {
            if (row._status === 'new') return;

            const original = this.originalColumns.find(o => o.COLUMN_NAME === row._originalName);
            if (!original) return;

            const isChanged =
                this.isDiff(row.COLUMN_NAME, original.COLUMN_NAME) ||
                this.isDiff(row.DATA_TYPE, original.DATA_TYPE) ||
                this.isDiff(row.DATA_LENGTH, original.DATA_LENGTH) ||
                (row.IS_PK !== original.IS_PK) ||
                this.isDiff(row.NULLABLE, original.NULLABLE) ||
                this.isDiff(row.DATA_DEFAULT, original.DATA_DEFAULT) ||
                this.isDiff(row.COMMENTS, original.COMMENTS);

            row._status = isChanged ? 'modified' : 'original';
        },

        handlePkChange(row) {
            this.markModified(row);
        },

        addDesignIndex() { this.designIndexes.push({ INDEX_NAME: '', INDEX_TYPE: 'NORMAL', COLUMNS: [], _status: 'new' }); },
        removeDesignIndex(index, row) { if (row._status !== 'new') this.deleteIndexList.push(row.INDEX_NAME); this.designIndexes.splice(index, 1); },

        addDesignFk() { this.designForeignKeys.push({ CONSTRAINT_NAME: '', COLUMN_NAME: '', R_TABLE_NAME: '', R_COLUMN_NAME: '', DELETE_RULE: 'NO ACTION', _status: 'new' }); },
        removeDesignFk(index, row) { if (row._status !== 'new') this.deleteFkList.push(row.CONSTRAINT_NAME); this.designForeignKeys.splice(index, 1); },

        generateAndRunAlter() {
            let sqls = []; const s = this.currentSchema; const t = this.activeTable;

            const nameRegex = /^[a-zA-Z0-9_]+$/;
            for (const c of this.designColumns) {
                if (!nameRegex.test(c.COLUMN_NAME)) {
                    this.$message.error(`列名 "${c.COLUMN_NAME}" 不合法，仅支持字母、数字和下划线。`);
                    return;
                }
            }

            this.deleteColumnList.forEach(c => sqls.push(`ALTER TABLE "${s}"."${t}" DROP COLUMN "${c}";`));

            this.designColumns.filter(c => c._status === 'modified').forEach(c => {
                const original = this.originalColumns.find(o => o.COLUMN_NAME === c._originalName);
                if (!original) return;

                if (this.isDiff(c.COLUMN_NAME, c._originalName)) {
                    sqls.push(`ALTER TABLE "${s}"."${t}" RENAME COLUMN "${c._originalName}" TO "${c.COLUMN_NAME}";`);
                }

                let typeLen = c.DATA_TYPE;
                if (c.DATA_LENGTH && !['NUMBER', 'DATE', 'TIMESTAMP', 'CLOB', 'BLOB'].includes(c.DATA_TYPE)) typeLen += `(${c.DATA_LENGTH})`;

                let modifySql = `ALTER TABLE "${s}"."${t}" MODIFY "${c.COLUMN_NAME}" ${typeLen}`;
                if (c.DATA_DEFAULT && this.isDiff(c.DATA_DEFAULT, original.DATA_DEFAULT)) modifySql += ` DEFAULT ${c.DATA_DEFAULT}`;
                if (this.isDiff(c.NULLABLE, original.NULLABLE)) modifySql += (c.NULLABLE === 'N' ? ' NOT NULL' : ' NULL');

                const isAttrChanged = this.isDiff(c.DATA_TYPE, original.DATA_TYPE) ||
                    this.isDiff(c.DATA_LENGTH, original.DATA_LENGTH) ||
                    this.isDiff(c.NULLABLE, original.NULLABLE) ||
                    this.isDiff(c.DATA_DEFAULT, original.DATA_DEFAULT);

                if (isAttrChanged) sqls.push(modifySql + ';');

                if (this.isDiff(c.COMMENTS, original.COMMENTS)) {
                    sqls.push(`COMMENT ON COLUMN "${s}"."${t}"."${c.COLUMN_NAME}" IS '${c.COMMENTS}';`);
                }
            });

            const oldPkCols = this.originalColumns.filter(c => ['1', 1, 'Y', true].includes(c.IS_PK)).map(c => c.COLUMN_NAME).sort();
            const newPkCols = this.designColumns.filter(c => c.IS_PK).map(c => c.COLUMN_NAME).sort();

            const isPkChanged = JSON.stringify(oldPkCols) !== JSON.stringify(newPkCols);

            if (isPkChanged) {
                if (oldPkCols.length > 0) sqls.push(`ALTER TABLE "${s}"."${t}" DROP PRIMARY KEY;`);
                if (newPkCols.length > 0) {
                    const pkList = newPkCols.map(c => `"${c}"`).join(',');
                    sqls.push(`ALTER TABLE "${s}"."${t}" ADD PRIMARY KEY (${pkList});`);
                }
            }

            this.designColumns.filter(c => c._status === 'new').forEach(c => {
                let l = `ALTER TABLE "${s}"."${t}" ADD "${c.COLUMN_NAME}" ${c.DATA_TYPE}`;
                if (c.DATA_LENGTH && !['NUMBER', 'DATE', 'TIMESTAMP', 'CLOB', 'BLOB'].includes(c.DATA_TYPE)) l += `(${c.DATA_LENGTH})`;
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
                if (f.DELETE_RULE === 'CASCADE') sql += ' ON DELETE CASCADE';
                else if (f.DELETE_RULE === 'SET NULL') sql += ' ON DELETE SET NULL';
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
                if (res.data.code === 200) {
                    this.$message.success('修改成功');
                    this.sqlConfirmVisible = false;
                    this.designVisible = false;

                    if (this.viewMode === 'relation') {
                        this.loadRelations();
                    } else {
                        this.loadData();
                    }
                } else {
                    throw new Error(res.data.msg);
                }
            } catch (e) {
                this.handleError(e, '表结构变更失败');
            } finally { this.altering = false; }
        },

        indexMethod(index) { return (this.currentPage - 1) * this.pageSize + index + 1; },
        handleSortChange({ prop, order }) { if (!prop) return; const sortedData = [...this.currentDataList]; sortedData.sort((a, b) => { let valA = a[prop]; let valB = b[prop]; if (valA == null) valA = ''; if (valB == null) valB = ''; if (valA > valB) return order === 'ascending' ? 1 : -1; if (valA < valB) return order === 'ascending' ? -1 : 1; return 0; }); this.currentDataList = sortedData; },
        handleSizeChange(v) { this.pageSize = v; this.currentPage = 1; this.loadData(); },
        handleCurrentChange(v) { this.currentPage = v; this.loadData(); },

        async runSql() { const res = await this.request('post', '/execute', { sql: this.customSql }); if (res.data.code === 200) this.sqlResult = res.data.data; },
        destroyGraph() { if (this.graphInstance) { this.graphInstance.destroy(); this.graphInstance = null; } const c = this.$refs.relationCanvas; if (c) c.innerHTML = ''; },

        handleShowAllChange(val) {
            if (val) {
                if (this.graphNodeIds.length > 0) {
                    this.expandedTableList = [...this.graphNodeIds];
                }
            } else {
                this.expandedTableList = [];
            }
            this.loadRelations();
        },

        async loadRelations() {
            this.destroyGraph();
            try {
                const res = await this.request('get', '/er-data', {
                    schema: this.currentSchema,
                    tableName: this.activeTable,
                    showAll: this.showAllColumns,
                    expandedTables: this.expandedTableList.join(',')
                });
                if (this.viewMode === 'relation') {
                    this.graphNodeIds = res.data.data.nodes.map(n => n.id);
                    this.checkAutoExpandState();
                    this.$nextTick(() => this.initGraph(res.data.data));
                }
            } catch (e) { }
        },

        checkAutoExpandState() {
            if (this.graphNodeIds.length > 0) {
                const allExpanded = this.graphNodeIds.every(id => this.expandedTableList.includes(id));
                if (this.showAllColumns !== allExpanded) {
                    this.showAllColumns = allExpanded;
                }
            }
        },

        handleGraphZoom(ratio) {
            if (!this.graphInstance) return;
            const zoom = this.graphInstance.getZoom();
            this.graphInstance.zoomTo(zoom * ratio, { x: this.$refs.relationCanvas.offsetWidth / 2, y: this.$refs.relationCanvas.offsetHeight / 2 });
        },
        handleGraphFit() {
            if (!this.graphInstance) return;
            this.graphInstance.fitView();
        },

        initGraph(data) {
            const container = this.$refs.relationCanvas; if (!container) return;
            container.innerHTML = '';

            const nodeCount = data.nodes.length;
            const ranksep = nodeCount > 5 ? 150 : 100;

            this.graphInstance = new G6.Graph({
                container,
                width: container.offsetWidth,
                height: container.offsetHeight,
                fitView: true,
                fitViewPadding: 40,
                renderer: 'canvas',
                layout: {
                    type: 'dagre',
                    rankdir: 'LR',
                    nodesep: 50,
                    ranksep: ranksep
                },
                defaultNode: {
                    type: 'db-table',
                    anchorPoints: [[0, 0.5], [1, 0.5]]
                },
                defaultEdge: {
                    type: 'cubic-horizontal',
                    style: { stroke: '#A3B1BF', lineWidth: 2, endArrow: true },
                    labelCfg: { autoRotate: true, style: { fontSize: 10, fill: '#aaa' } }
                },
                modes: { default: ['drag-canvas', 'zoom-canvas', 'drag-node'] }
            });
            this.graphInstance.data(JSON.parse(JSON.stringify(data)));
            this.graphInstance.render();

            this.graphInstance.on('node:click', (e) => {
                const shapeName = e.target.get('name');
                const nodeId = e.item.getModel().id;

                if (shapeName === 'expand-text') {
                    if (!this.expandedTableList.includes(nodeId)) {
                        this.expandedTableList.push(nodeId);
                        this.loadRelations();
                    }
                } else if (shapeName === 'collapse-text') {
                    const index = this.expandedTableList.indexOf(nodeId);
                    if (index > -1) {
                        this.expandedTableList.splice(index, 1);
                        this.showAllColumns = false;
                        this.loadRelations();
                    }
                }
            });

            this.graphInstance.on('node:dblclick', (e) => {
                const t = e.item.getModel().label;
                if (t !== this.activeTable) {
                    this.$emit('open-table', { connId: this.connId, schema: this.currentSchema, tableName: t, initViewMode: 'data' });
                }
            });
        }
    },
    beforeDestroy() { this.destroyGraph(); }
};
</script>

<style scoped>
/* 保持原有样式，省略以节省空间，与提供的 TableDetail.vue 原始样式一致 */
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
    background: #fdf6ec;
    color: #e6a23c;
    padding: 12px 15px;
    border-radius: 4px;
    display: flex;
    align-items: center;
    font-size: 14px;
    border: 1px solid #faecd8;
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
</style>