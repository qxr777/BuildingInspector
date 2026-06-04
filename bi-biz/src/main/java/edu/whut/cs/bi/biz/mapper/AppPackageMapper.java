package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.AppPackage;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 移动端安装包Mapper。
 */
public interface AppPackageMapper {
    AppPackage selectAppPackageById(Long id);

    List<AppPackage> selectAppPackageList(AppPackage appPackage);

    AppPackage selectPublishedAppPackage();

    int insertAppPackage(AppPackage appPackage);

    int updateAppPackage(AppPackage appPackage);

    int deleteAppPackageByIds(String[] ids);

    List<Long> selectMinioIdsByIds(String[] ids);

    int clearPublished(@Param("excludeId") Long excludeId);
}
