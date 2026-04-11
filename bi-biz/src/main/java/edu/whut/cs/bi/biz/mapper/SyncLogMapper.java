package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.SyncLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 同步日志 Mapper
 *
 * @author QiXin
 * @date 2026/04/09
 */
@Mapper
public interface SyncLogMapper {
    int insert(SyncLog log);
    int updateStatus(@Param("syncUuid") String syncUuid, @Param("status") Integer status, @Param("remark") String remark);
    SyncLog selectBySyncUuid(@Param("syncUuid") String syncUuid);
}
