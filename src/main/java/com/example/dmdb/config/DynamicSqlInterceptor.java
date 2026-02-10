package com.example.dmdb.config;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.DefaultReflectorFactory;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.wrapper.DefaultObjectWrapperFactory;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

import java.util.Properties;

/**
 * MyBatis 拦截器 - 针对 Oracle 11g 的分页兼容修复
 */
@Intercepts({
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class})
})
public class DynamicSqlInterceptor implements Interceptor {

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        MappedStatement ms = (MappedStatement) args[0];
        Object parameter = args[1];
        RowBounds rowBounds = (RowBounds) args[2];

        // 检查是否有分页参数 (RowBounds 不为默认值时才拦截)
        if (rowBounds != RowBounds.DEFAULT) {

            // 1. 获取原始 SQL
            BoundSql boundSql = ms.getBoundSql(parameter);
            String originalSql = boundSql.getSql();

            // 2. 获取分页参数
            int offset = rowBounds.getOffset();
            int limit = rowBounds.getLimit();

            // 3. 生成 Oracle 11g 兼容的分页 SQL (三层嵌套)
            String pageSql = getOracle11gPageSql(originalSql, offset, limit);

            // 4. 将修改后的 SQL 重新写回 BoundSql
            // 使用 MetaObject 修改 BoundSql 中的 "sql" 字段
            MetaObject metaBoundSql = SystemMetaObject.forObject(boundSql);
            metaBoundSql.setValue("sql", pageSql);

            // 5. 重要：禁用 MyBatis 内存分页
            // 因为我们已经在 SQL 层做了物理分页，必须把 RowBounds 重置为默认，
            // 否则 MyBatis 会在结果集返回后再次尝试 skip/limit，导致数据为空或错误。
            args[2] = RowBounds.DEFAULT;
        }

        return invocation.proceed();
    }

    /**
     * 构建 Oracle 11g 兼容的三层嵌套分页 SQL
     * * 结构说明：
     * 最外层：WHERE ROW_ID > offset (过滤掉前几页的数据)
     * 中间层：WHERE ROWNUM <= endRow (限制最大行数，获取 Top N)
     * 最内层：原始 SQL (必须包含 ORDER BY)
     */
    private String getOracle11gPageSql(String originalSql, int offset, int limit) {
        // 计算结束行号 (例如：第2页，每页10条 -> offset=10, limit=10 -> endRow=20)
        int endRow = offset + limit;

        StringBuilder sqlBuilder = new StringBuilder();

        // --- Layer 3: 最终过滤 (去掉 offset 之前的数据) ---
        sqlBuilder.append("SELECT * FROM ( ");

        // --- Layer 2: 加上行号并限制最大行数 (ROWNUM <= endRow) ---
        sqlBuilder.append(" SELECT TMP_PAGE.*, ROWNUM ROW_ID FROM ( ");

        // --- Layer 1: 原始 SQL ---
        sqlBuilder.append(originalSql);

        sqlBuilder.append(" ) TMP_PAGE WHERE ROWNUM <= ").append(endRow);

        sqlBuilder.append(" ) WHERE ROW_ID > ").append(offset);

        return sqlBuilder.toString();
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 可以接收配置文件中的参数
    }
}