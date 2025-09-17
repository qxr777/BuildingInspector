package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.Building;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.util.Map;

/**
 * @Author:wanzheng
 * @Date:2025/9/17 21:40
 * @Description:桥梁卡片生成服务接口
 **/
public interface IBridgeCardService {

    /**
     * 生成桥梁卡片Word文档
     *
     * @param buildingId 建筑物ID
     * @return Word文档对象
     */
    XWPFDocument generateBridgeCardDocument(Long buildingId);

    /**
     * 处理桥梁卡片表格数据替换
     *
     * @param document Word文档
     * @param building 建筑物ID
     */
    void processBridgeCardData(XWPFDocument document, Building building);
}