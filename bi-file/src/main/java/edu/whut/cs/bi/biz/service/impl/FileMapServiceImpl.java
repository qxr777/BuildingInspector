package edu.whut.cs.bi.biz.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import io.minio.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import edu.whut.cs.bi.biz.mapper.FileMapMapper;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.config.MinioConfig;
import com.ruoyi.common.core.text.Convert;
import org.apache.commons.io.FilenameUtils;

/**
 * 文件管理Service业务层处理
 * 
 * @author zzzz
 * @date 2025-03-29
 */
@Service
public class FileMapServiceImpl implements IFileMapService {
    @Autowired
    private FileMapMapper fileMapMapper;

    @Autowired
    private MinioClient minioClient;

    @Autowired
    private MinioConfig minioConfig;

    /**
     * 查询文件管理
     * 
     * @param id 文件管理主键
     * @return 文件管理
     */
    @Override
    public FileMap selectFileMapById(Long id) {
        return fileMapMapper.selectFileMapById(id);
    }

    /**
     * 查询文件管理列表
     * 
     * @param fileMap 文件管理
     * @return 文件管理
     */
    @Override
    public List<FileMap> selectFileMapList(FileMap fileMap) {
        return fileMapMapper.selectFileMapList(fileMap);
    }

    /**
     * 新增文件管理
     * 
     * @param fileMap 文件管理
     * @return 结果
     */
    @Override
    public int insertFileMap(FileMap fileMap) {
        fileMap.setCreateTime(DateUtils.getNowDate());
        return fileMapMapper.insertFileMap(fileMap);
    }

    /**
     * 修改文件管理
     * 
     * @param fileMap 文件管理
     * @return 结果
     */
    @Override
    public int updateFileMap(FileMap fileMap) {
        fileMap.setUpdateTime(DateUtils.getNowDate());
        return fileMapMapper.updateFileMap(fileMap);
    }

    /**
     * 批量删除文件管理
     * 
     * @param ids 需要删除的文件管理主键
     * @return 结果
     */
    @Override
    public int deleteFileMapByIds(String ids) {
        String[] fileIds = Convert.toStrArray(ids);
        for (String idStr : fileIds) {
            Long id = Long.valueOf(idStr);
            FileMap fileMap = fileMapMapper.selectFileMapById(id);
            if (fileMap != null) {
                try {
                    // 从MinIO删除文件
                    minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(fileMap.getNewName())
                            .build());
                } catch (Exception e) {
                    throw new RuntimeException("删除文件失败", e);
                }
            }
        }
        return fileMapMapper.deleteFileMapByIds(fileIds);
    }

    /**
     * 删除文件管理信息
     * 
     * @param id 文件管理主键
     * @return 结果
     */
    @Override
    public int deleteFileMapById(Long id) {
        FileMap fileMap = fileMapMapper.selectFileMapById(id);
        if (fileMap != null) {
            try {
                // 从MinIO删除文件
                minioClient.removeObject(RemoveObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(fileMap.getNewName())
                        .build());
            } catch (Exception e) {
                throw new RuntimeException("删除文件失败", e);
            }
        }
        return fileMapMapper.deleteFileMapById(id);
    }

    @Override
    public FileMap handleFileUpload(MultipartFile file) {
        InputStream fileInputStream = null;
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = FilenameUtils.getExtension(originalFilename);

            // 使用UUID作为新文件名
            String uuid = UUID.randomUUID().toString().replace("-", "");
            // 构建新的文件名，包含路径
            String objectName =  uuid + "." + extension;

            // 获取输入流并确保它会被关闭
            fileInputStream = file.getInputStream();

            // 上传文件到MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName.substring(0,2)+"/"+objectName)
                    .stream(fileInputStream, file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            // 保存文件信息
            FileMap fileMap = new FileMap();
            fileMap.setOldName(originalFilename);
            fileMap.setNewName(objectName);
            fileMap.setCreateTime(DateUtils.getNowDate());
            fileMap.setCreateBy(ShiroUtils.getLoginName());
            fileMapMapper.insertFileMap(fileMap);

            return fileMap;
        } catch (Exception e) {
            throw new RuntimeException("文件上传失败", e);
        } finally {
            // 确保输入流关闭
            if (fileInputStream != null) {
                try {
                    fileInputStream.close();
                } catch (Exception e) {
                    // 记录关闭流失败但不抛出异常
                }
            }
        }
    }

    @Override
    public List<FileMap> handleBatchFileUpload(MultipartFile[] files) {
        List<FileMap> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(handleFileUpload(file));
        }
        return results;
    }

    @Override
    public byte[] handleFileDownload(Long id) {
        try {
            FileMap fileMap = fileMapMapper.selectFileMapById(id);
            if (fileMap == null) {
                throw new RuntimeException("文件不存在");
            }

            // 使用try-with-resources确保流被关闭
            try (
                    InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(fileMap.getNewName())
                            .build());
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                // 将InputStream转换为byte数组
                int nRead;
                byte[] data = new byte[16384];
                while ((nRead = stream.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
                buffer.flush();
                return buffer.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }

    @Override
    public byte[] handleBatchFileDownload(Long[] ids) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(baos)) {

            for (Long id : ids) {
                FileMap fileMap = fileMapMapper.selectFileMapById(id);
                if (fileMap == null) {
                    continue;
                }

                // 从MinIO获取文件并确保流被关闭
                try (InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(fileMap.getNewName().substring(0,2)+"/"+ fileMap.getNewName())
                        .build())) {

                    // 添加到ZIP
                    ZipEntry entry = new ZipEntry(fileMap.getOldName());
                    zos.putNextEntry(entry);

                    // 复制数据
                    byte[] buffer = new byte[16384];
                    int len;
                    while ((len = stream.read(buffer)) > 0) {
                        zos.write(buffer, 0, len);
                    }
                    zos.closeEntry();
                }
            }
            zos.finish();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("批量下载文件失败", e);
        }
    }

    @Override
    public FileMap copyFile(Long id) {
        InputStream sourceStream = null;
        try {
            // 查询源文件信息
            FileMap sourceFile = fileMapMapper.selectFileMapById(id);
            if (sourceFile == null) {
                throw new RuntimeException("源文件不存在");
            }

            // 从MinIO获取源文件
            sourceStream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(sourceFile.getNewName().substring(0,2)+"/"+sourceFile.getNewName())
                    .build());

            // 准备新文件信息
            String originalFilename = sourceFile.getOldName();
            String extension = FilenameUtils.getExtension(originalFilename);

            // 修改文件名，添加"副本"后缀
            String filenameWithoutExt = FilenameUtils.removeExtension(originalFilename);
            String newOriginalFilename = filenameWithoutExt + " - 副本." + extension;

            // 使用新的UUID作为复制文件的文件名
            String uuid = UUID.randomUUID().toString().replace("-", "");

            // 构建新的文件名，包含路径
            String newObjectName = uuid + "." + extension;

            // 上传复制的文件到MinIO
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(newObjectName.substring(0,2)+"/"+uuid+"."+extension)
                    .stream(sourceStream, -1, 10485760)
                    .contentType("application/octet-stream")
                    .build());

            // 获取当前登录用户
            String loginName = ShiroUtils.getLoginName();
            if (loginName == null || loginName.isEmpty()) {
                loginName = "system";
            }

            // 创建新的文件记录
            FileMap newFile = new FileMap();
            newFile.setOldName(newOriginalFilename); // 使用添加了"副本"的文件名
            newFile.setNewName(newObjectName);
            newFile.setCreateTime(DateUtils.getNowDate());
            newFile.setCreateBy(loginName);
            fileMapMapper.insertFileMap(newFile);

            return newFile;
        } catch (Exception e) {
            throw new RuntimeException("复制文件失败", e);
        } finally {
            // 确保输入流关闭
            if (sourceStream != null) {
                try {
                    sourceStream.close();
                } catch (Exception e) {
                    // 记录关闭流失败但不抛出异常
                }
            }
        }
    }

    @Override
    public byte[] handleFileDownloadByNewName(String newName) {
        try (
                InputStream stream = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(minioConfig.getBucketName())
                        .object(newName.substring(0,2)+"/" + newName)
                        .build());
                ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            // 将InputStream转换为byte数组
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = stream.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            buffer.flush();
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("文件下载失败", e);
        }
    }

    @Override
    public FileMap selectFileMapByNewName(String newName) {
        // 创建查询条件
        FileMap fileMap = new FileMap();
        fileMap.setNewName(newName);
        List<FileMap> list = fileMapMapper.selectFileMapList(fileMap);

        // 返回第一个匹配的结果
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }
}
