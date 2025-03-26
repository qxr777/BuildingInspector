package edu.whut.cs.bi.biz.domain;

import com.ruoyi.common.core.domain.TreeEntity;

/**
 * @author QiXin
 * @date 2025/3/17
 */
public class Property extends TreeEntity {

    private Long id;
    private String name;
    private String value;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
