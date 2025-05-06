package edu.whut.cs.bi.biz.mapper;

import com.ruoyi.common.core.domain.entity.SysUser;
import edu.whut.cs.bi.biz.domain.Project;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目用户Mapper接口
 * 
 * @author: chenwenqi
 * @date: 2025-04-02
 */
public interface ProjectUserMapper
{
    /**
     * 根据项目ID和角色查询用户ID列表
     * ·
     * @param projectId 项目ID
     * @return 用户ID列表
     */
    List<Long> selectUserIdsByProjectAndRole(@Param("projectId") Long projectId, @Param("role") String role);

    /**
     * 根据项目ID和角色查询用户ID列表
     * ·
     * @param projectId 项目ID
     * @return 用户ID列表
     */
    List<SysUser> selectUsersByProjectAndRole(@Param("projectId") Long projectId, @Param("role") String role);

    /**
     * 根据项目ID查询项目用户数量
     *
     * @param projectId
     * @return
     */
    int countProjectUser(Long projectId);

    /**
     * 根据项目ID删除项目用户
     *
     * @param projectId
     */
    void deleteProjectUser(Long projectId);

    /**
     * 保存项目用户
     *
     * @param projectId
     * @param inspectorIds
     * @param role
     * @return
     */
    int saveProjectUser(@Param("projectId") Long projectId, @Param("inspectorIds") List<Long> inspectorIds, @Param("role") String role);
}
