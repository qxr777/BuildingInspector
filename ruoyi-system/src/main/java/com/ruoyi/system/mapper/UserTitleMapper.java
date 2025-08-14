package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.UserTitle;

import java.util.List;

/**
 * 用户职称Mapper接口
 * 
 * @author chenwenqi
 */
public interface UserTitleMapper
{
    /**
     * 查询用户职称
     * 
     * @param id 用户职称主键
     * @return 用户职称
     */
    public UserTitle selectUserTitleById(Long id);

    /**
     * 查询用户职称列表
     * 
     * @param UserTitle 用户职称
     * @return 用户职称集合
     */
    public List<UserTitle> selectUserTitleList(UserTitle UserTitle);
    
    /**
     * 新增用户职称
     * 
     * @param UserTitle 用户职称
     * @return 结果
     */
    public int insertUserTitle(UserTitle UserTitle);

    /**
     * 修改用户职称
     * 
     * @param UserTitle 用户职称
     * @return 结果
     */
    public int updateUserTitle(UserTitle UserTitle);

    /**
     * 删除用户职称
     * 
     * @param id 用户职称主键
     * @return 结果
     */
    public int deleteUserTitleById(Long id);

    /**
     * 批量删除用户职称
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     * @throws Exception 异常
     */
    public int deleteUserTitleByIds(Long[] ids);

}
