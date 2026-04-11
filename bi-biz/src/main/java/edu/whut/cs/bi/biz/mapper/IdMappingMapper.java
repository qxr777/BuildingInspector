package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.IdMapping;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * ID 映射 Mapper
 *
 * @author QiXin
 * @date 2026/04/09
 */
@Mapper
public interface IdMappingMapper {
    int insert(IdMapping mapping);
    IdMapping selectByOfflineUuid(@Param("entityType") String entityType, @Param("offlineUuid") String offlineUuid);
    List<IdMapping> selectBySyncUuid(@Param("syncUuid") String syncUuid);
}
