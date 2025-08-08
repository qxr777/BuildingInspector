package edu.whut.cs.bi.api.task;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.system.domain.SysUserRole;
import com.ruoyi.system.mapper.SysUserMapper;
import com.ruoyi.system.mapper.SysUserRoleMapper;
import edu.whut.cs.bi.api.service.ApiService;
import edu.whut.cs.bi.biz.domain.Package;
import edu.whut.cs.bi.biz.mapper.PackageMapper;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.IPackageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

/**
 * @Author:wanzheng
 * @Date:2025/7/11 10:58
 * @Description:
 **/
@Component("userPackageTask")
public class UserPackageTask {
    private static final Logger log = LoggerFactory.getLogger(UserPackageTask.class);
    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysUserRoleMapper sysUserRoleMapper;

    @Autowired
    private ApiService apiService;

    @Autowired
    private PackageMapper packageMapper;

    @Autowired
    private IFileMapService fileMapService;

    @Autowired
    private IPackageService packageService;

    public AjaxResult generateUserDataPackage() {
        try {
            SysUserRole query = new SysUserRole();
            query.setRoleId(7L);
            // 所有普通职员
            List<SysUserRole> sysUserRole = sysUserRoleMapper.selectSysUserRoleList(query);
            Long[] ids = new Long[sysUserRole.size()];
            for (int i = 0; i < sysUserRole.size(); i++) {
                ids[i] = sysUserRole.get(i).getUserId();
            }
            List<SysUser> sysUsers = sysUserMapper.selectUserByIds(ids);
            // 所有已打包的数据
            List<Package> packageAll = packageMapper.selectPackageList(new Package());
            HashMap<Long, Package> packageMap = new HashMap<>();
            List<Package> packagesUpdate = new ArrayList<>();
            List<Package> packagesInsert = new ArrayList<>();
            HashMap<Long, SysUser> sysUserMap = new HashMap<>(16);
            for (Package aPackage : packageAll) {
                packageMap.put(aPackage.getUserId(), aPackage);
            }
            // 插入从未打包的数据
            for (SysUser user : sysUsers) {
                sysUserMap.put(user.getUserId(), user);
                if (!packageMap.containsKey(user.getUserId())) {
                    AjaxResult ajaxResult = packageService.generateUserDataPackage(user);
                    if (ajaxResult.isSuccess()) {
                        Package aPackage = new Package();
                        aPackage.setUserId(user.getUserId());
                        Date nowDate = DateUtils.getNowDate();
                        aPackage.setPackageTime(nowDate);
                        aPackage.setUpdateTime(nowDate);
                        aPackage.setMinioId(Long.valueOf(ajaxResult.get("data").toString()));
                        aPackage.setPackageSize(ajaxResult.get("size").toString());
                        packagesInsert.add(aPackage);
                    }
                }
            }
            // 更新压缩包
            for (Package aPackage : packageAll) {
                if (aPackage.getUpdateTime().after(aPackage.getPackageTime())) {
                    if (sysUserMap.containsKey(aPackage.getUserId())) {
                        fileMapService.deleteFileMapById(aPackage.getMinioId());
                        AjaxResult ajaxResult = packageService.generateUserDataPackage(sysUserMap.get(aPackage.getUserId()));
                        if (ajaxResult.isSuccess()) {
                            Date nowDate = DateUtils.getNowDate();
                            aPackage.setPackageTime(nowDate);
                            aPackage.setUpdateTime(nowDate);
                            aPackage.setMinioId(Long.valueOf(ajaxResult.get("data").toString()));
                            aPackage.setPackageSize(ajaxResult.get("size").toString());
                            packagesUpdate.add(aPackage);
                        } else {
                            return AjaxResult.error("打包用户数据失败");
                        }
                    }
                }
            }
            if (!packagesUpdate.isEmpty()) {
                packageMapper.batchUpdatePackage(packagesUpdate);
            }
            if (!packagesInsert.isEmpty()) {
                packageMapper.batchInsertPackage(packagesInsert);
            }
            return AjaxResult.success("全部用户数据打包完成");
        } catch (Exception e) {
            log.error("打包用户数据失败：" + e.getMessage());
            return AjaxResult.error("打包用户数据失败：{}" + e.getMessage());
        }
    }
}
