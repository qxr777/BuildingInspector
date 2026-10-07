package edu.whut.cs.bi.biz.service.std;

import java.util.List;

/**
 * 标准版本隔离所需的 schema 演进（管理员触发，幂等）。
 */
public interface StdSchemaService {

    /**
     * 确保 schema 就绪。dryRun 时只返回将执行的 DDL，不实际执行。
     *
     * @return 本次需要（或已实际执行）的 DDL 列表
     */
    List<String> ensureSchema(boolean dryRun);
}
