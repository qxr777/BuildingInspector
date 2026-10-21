package edu.whut.cs.bi.biz.mapper;

import edu.whut.cs.bi.biz.domain.FileUploadRegistry;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 通道A预上传幂等登记 Mapper
 */
public interface FileUploadRegistryMapper {

    /**
     * 按 uploadUuid 查询登记记录
     */
    FileUploadRegistry selectByUploadUuid(@Param("uploadUuid") String uploadUuid);

    /**
     * 按 uploadUuid 批量查询（已传回查用，保持入参顺序由服务层处理）
     */
    List<FileUploadRegistry> selectByUploadUuids(@Param("list") List<String> uploadUuids);

    /**
     * 插入登记记录（uk_file_upload_registry_upload_uuid 冲突时抛 DuplicateKeyException）
     */
    int insertFileUploadRegistry(FileUploadRegistry registry);
}
