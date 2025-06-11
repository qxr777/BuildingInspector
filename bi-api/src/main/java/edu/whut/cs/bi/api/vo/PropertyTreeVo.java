package edu.whut.cs.bi.api.vo;

import edu.whut.cs.bi.biz.domain.Property;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class PropertyTreeVo {

    private Property property;

    private Map<String, List<String>> images;
}
