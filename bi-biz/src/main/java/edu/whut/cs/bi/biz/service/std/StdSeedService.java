package edu.whut.cs.bi.biz.service.std;

import java.util.List;

/**
 * 正式稿标准数据种子：幂等 upsert 病害字典与模板树，并做受限对账。
 * 仅由管理员显式触发，不在启动时自动执行。
 */
public interface StdSeedService {

    /**
     * 执行种子。
     *
     * @param scope       执行范围
     * @param dryRun      true 时只计算变更不写入
     * @param bridgeTypes 桥型子集（如 B01..B07）；为空表示全部。仅影响模板节点与绑定，病害字典始终全量
     * @return 执行报告
     */
    StdSeedReport seed(StdSeedScope scope, boolean dryRun, List<String> bridgeTypes);
}
