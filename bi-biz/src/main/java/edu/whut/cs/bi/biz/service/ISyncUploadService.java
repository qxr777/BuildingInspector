package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.vo.SyncResultVo;
import java.util.Map;

/**
 * 离线数据上传同步服务接口
 *
 * @author QiXin
 * @date 2026/04/09
 */
public interface ISyncUploadService {

    /**
     * 同步上传离线采集的数据包
     * @param dataMap 包含各类实体列表的原始 JSON Map
     * @return 同步结果
     */
    SyncResultVo syncUpload(Map<String, Object> dataMap);
}
