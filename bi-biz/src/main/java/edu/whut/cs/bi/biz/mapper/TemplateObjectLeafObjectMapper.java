package edu.whut.cs.bi.biz.mapper;

import java.util.List;
import edu.whut.cs.bi.biz.domain.TemplateObjectLeafObject;
import org.apache.ibatis.annotations.Param;

/**
 * 模板 PART 节点-叶子部件多对多绑定（bi_template_object_leaf_object）Mapper 接口
 */
public interface TemplateObjectLeafObjectMapper {

    /**
     * 查询绑定列表（空条件 = 全量，common.db 下发用）
     */
    public List<TemplateObjectLeafObject> selectTemplateObjectLeafObjectList(TemplateObjectLeafObject query);

    /**
     * 按模板 PART 节点 ID 集合批量查询绑定
     */
    public List<TemplateObjectLeafObject> selectByTemplateObjectIds(@Param("templateObjectIds") List<Long> templateObjectIds);
}
