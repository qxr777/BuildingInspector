package edu.whut.cs.bm.biz.vo;

import java.util.List;

/**
 * @Author dawson
 * @Date 2022/8/3 16:47
 */
public class IndexDataVo {

    //id
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
//监测对象id

    private Long objectId;
    //指标名字

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    //监测对象名
    private String objectName;

    /**
     * 监测指标名
     */
    private String indexName;

    /**
     * 监测数据单位
     */
    private String unit;

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    //数据(纵坐标)
    private Long[] data;

    //日期(横坐标)
    private String[] date;

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public Long[] getData() {
        return data;
    }

    public void setData(Long[] data) {
        this.data = data;
    }

    public String[] getDate() {
        return date;
    }

    public void setDate(String[] date) {
        this.date = date;
    }
}
