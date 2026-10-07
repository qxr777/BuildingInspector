package edu.whut.cs.bi.biz.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 标准种子的 schema 自省与 DDL 执行（仅管理员触发时使用）。
 */
public interface StdSchemaMapper {

    /** 当前数据库（schema）名。 */
    String selectCurrentSchema();

    /** 某表的全部列名。 */
    List<String> selectTableColumns(@Param("schema") String schema, @Param("table") String table);

    /** 某表的全部索引列信息：index_name/column_name/non_unique/seq_in_index。 */
    List<Map<String, Object>> selectIndexColumns(@Param("schema") String schema, @Param("table") String table);

    /** 执行由本服务构造的 DDL（无用户输入，使用字符串替换）。 */
    int executeDdl(@Param("ddl") String ddl);
}
