package edu.whut.cs.bi.biz.service;

import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.List;

@SpringBootTest(classes = com.ruoyi.RuoYiApplication.class)
public class BridgeSpanDataFixTest {

    @Autowired
    private BiObjectMapper biObjectMapper;

    @Test
    public void testQueryTree() {
        Long rootId = 1034167L;
        List<BiObject> objects = biObjectMapper.selectChildrenByIdRemoveLeafNew(rootId);
        System.out.println("Tree Size: " + objects.size());
        for (BiObject obj : objects) {
            System.out.println("Node => id=" + obj.getId() + ", name=" + obj.getName() + ", parentId=" + obj.getParentId() + ", templateId=" + obj.getTemplateObjectId());
        }
    }
}
