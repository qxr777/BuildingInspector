package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.Project;
import edu.whut.cs.bi.biz.domain.enums.ProjectUserRoleEnum;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目Mapper接口
 * 
 * @author: chenwenqi
 * @date: 2025-04-02
 */
public interface ProjectMapper 
{
    /**
     * 查询项目
     * 
     * @param id 项目主键
     * @return 项目
     */
    public Project selectProjectById(Long id);

    /**
     * 查询项目列表
     * 
     * @param project 项目
     * @return 项目集合
     */
    public List<Project> selectProjectList(@Param("project") Project project, @Param("currentUserId") Long currentUserId);

    /**
     * 新增项目
     * 
     * @param project 项目
     * @return 结果
     */
    public int insertProject(Project project);

    /**
     * 修改项目
     * 
     * @param project 项目
     * @return 结果
     */
    public int updateProject(Project project);

    /**
     * 删除项目
     * 
     * @param id 项目主键
     * @return 结果
     */
    public int deleteProjectById(Long id);

    /**
     * 批量删除项目
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteProjectByIds(String[] ids);
}
