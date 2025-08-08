package edu.whut.cs.bi.biz.service;

import org.springframework.web.multipart.MultipartFile;

public interface ReadFileService {

    /**
     * 批量导入病害信息
     *
     * @param file
     */
    void readDiseaseExcel(MultipartFile file, Long taskId);
}
