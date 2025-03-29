package edu.whut.cs.bi.biz.service.impl;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.mapper.PropertyMapper;
import edu.whut.cs.bi.biz.service.IPropertyIndexService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.ruoyi.common.utils.PageUtils.startPage;


/**
 * 建筑属性Service业务层处理
 *
 */
@Service
public class PropertyIndexServiceImpl implements IPropertyIndexService
{
    @Resource
    private PropertyMapper propertyMapper;

    /**
     * 查询属性
     *
     * @param id 属性ID
     * @return 属性
     */
    @Override
    public Property selectPropertyById(Long id)
    {
        return propertyMapper.selectPropertyById(id);
    }

    /**
     * 查询属性列表
     *
     * @param property 属性
     * @return 属性
     */
    @Override
    public List<Property> selectPropertyList(Property property)
    {
        return propertyMapper.selectPropertyList(property);
    }

    @Override
    public List<Ztree> selectPropertyTree(String name) {
        Property query = new Property();

        if (StringUtils.isNotBlank(name)) {
            query.setName(name);
        }

        // 使用PageHelper分页，查询第一页的10条记录
        PageHelper.startPage(1, 10);
        List<Property> ps = propertyMapper.selectPropertyByName(query);
        PageHelper.clearPage();  // 清除分页设置，防止影响后续查询

        // 节点顺序要求
        List<Property> propertyList = new ArrayList<>();
        for (Property p : ps) {
            propertyList.add(p);
            List<Property> properties = propertyMapper.selectChildrenObjectById(p.getId());
            propertyList.addAll(properties);
        }

        // 封装成 Ztree
        List<Ztree> ztrees = new ArrayList<>();
        for (Property property : propertyList)
        {
            Ztree ztree = new Ztree();
            ztree.setId(property.getId());
            ztree.setpId(property.getParentId());
            ztree.setName(property.getName());
            ztree.setTitle(property.getName());
            ztrees.add(ztree);
        }
        return ztrees;
    }

}
