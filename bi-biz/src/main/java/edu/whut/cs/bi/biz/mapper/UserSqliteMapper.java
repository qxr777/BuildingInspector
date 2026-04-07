package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.UserSqlite;
import org.apache.ibatis.annotations.Param;
import java.util.List;

/**
 * 用户级 SQLite 离线包记录 Mapper 接口
 */
public interface UserSqliteMapper {
    /**
     * 根据用户 ID 查询记录
     */
    UserSqlite selectUserSqliteByUserId(Long userId);

    /**
     * 新增记录
     */
    int insertUserSqlite(UserSqlite userSqlite);

    /**
     * 更新记录
     */
    int updateUserSqlite(UserSqlite userSqlite);
}
