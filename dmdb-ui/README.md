# dmdb-ui

## Project setup
```
npm install
```

### Compiles and hot-reloads for development
```
npm run serve
```

### Compiles and minifies for production
```
npm run build
```

### Lints and fixes files
```
npm run lint
```

### Customize configuration
See [Configuration Reference](https://cli.vuejs.org/config/).


src/
├── main.js                  // [入口] 引入 Vue, ElementUI, Axios 等
├── App.vue                  // [根组件] 仅负责挂载 MultiWindowLayout
│
└── components/
    │
    ├── MultiWindowLayout.vue  // [核心容器] "壳"
    │   ├── 职责：
    │   │   1. 左侧连接树 (Tree) 的渲染与逻辑
    │   │   2. 连接的增删改查 (调用后端 /connection 接口)
    │   │   3. 浏览器缓存 (localStorage) 的读取与自动重连
    │   │   4. 右侧多标签页 (Tabs) 的管理 (增/删/切换)
    │   │   5. 弹窗 (新建连接)
    │   └── 包含：<el-tabs> 包裹着 <TableDetail>
    │
    └── TableDetail.vue        // [业务组件] "内容"
        ├── 职责：
        │   1. 接收 props: connId, schema, tableName
        │   2. 数据视图 (Data Grid): 增删改查、分页、筛选、外键冲突跳转
        │   3. 关系视图 (ER Graph): G6渲染、双击跳转($emit)
        │   4. SQL终端: 执行自定义SQL
        │   5. 所有的具体业务请求 (调用后端 /db 接口)
        └── 包含：独立维护的 G6 实例、数据表格、筛选器