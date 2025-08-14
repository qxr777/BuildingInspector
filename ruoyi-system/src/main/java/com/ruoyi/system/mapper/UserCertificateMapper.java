package com.ruoyi.system.mapper;

import com.ruoyi.system.domain.UserCertificate;

import java.util.List;

/**
 * 用户证书Mapper接口
 * 
 * @author chenwenqi
 */
public interface UserCertificateMapper
{
    /**
     * 查询用户证书
     * 
     * @param id 用户证书主键
     * @return 用户证书
     */
    public UserCertificate selectUserCertificateById(Long id);

    /**
     * 查询用户证书列表
     * 
     * @param UserCertificate 用户证书
     * @return 用户证书集合
     */
    public List<UserCertificate> selectUserCertificateList(UserCertificate UserCertificate);
    
    /**
     * 新增用户证书
     * 
     * @param UserCertificate 用户证书
     * @return 结果
     */
    public int insertUserCertificate(UserCertificate UserCertificate);

    /**
     * 修改用户证书
     * 
     * @param UserCertificate 用户证书
     * @return 结果
     */
    public int updateUserCertificate(UserCertificate UserCertificate);

    /**
     * 删除用户证书
     * 
     * @param id 用户证书主键
     * @return 结果
     */
    public int deleteUserCertificateById(Long id);

    /**
     * 批量删除用户证书
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     * @throws Exception 异常
     */
    public int deleteUserCertificateByIds(Long[] ids);

}
