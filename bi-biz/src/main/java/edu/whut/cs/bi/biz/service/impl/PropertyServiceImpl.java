package edu.whut.cs.bi.biz.service.impl;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.mapper.PropertyMapper;
import edu.whut.cs.bi.biz.service.IPropertyService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 建筑属性Service业务层处理
 *
 */
@Service
public class PropertyServiceImpl implements IPropertyService
{
    @Autowired
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

    @Override
    @Transactional
    public Boolean readJsonFile(MultipartFile file, Property property) {
        if (file.isEmpty()) {
            throw new ServiceException("文件不能为空");
        }

        try {
            // 将文件内容转化成字符串
            String json = new String(file.getBytes(), "UTF-8");
            // 解析json数据
            JSONObject jsonObject = JSONUtil.parseObj(json,false);
            buildTree(jsonObject, property);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    public void buildTree(JSONObject jsonObject, Property oldProperty) {
        // 用于显示顺序， 只需要查找一轮数据库找到相同祖先的属性实体
        AtomicInteger orderNum = new AtomicInteger(1);
        Long oldId = oldProperty.getId();
        String ancestors;
        if (oldId != null) {
            ancestors = (oldProperty.getAncestors() != null ? oldProperty.getAncestors() : "") + "," + oldProperty.getId();
        } else {
            ancestors = null;
        }

        // 查找相同父节点的属性数量，如果大于1，则需要设置显示顺序字段
        int count = 0;
        if (oldId != null) {
            count = propertyMapper.getOrderNum(oldId);
        } else {
            // 也会存在大桥一级节点
            count = propertyMapper.getParentIdIsNullNum();
        }
        if (count > 1) {
            orderNum.set(count + 1);
        }


        Set<Map.Entry<String, Object>> entries = jsonObject.entrySet();
        entries.forEach(entry -> {
            Property property = new Property();
            BeanUtils.copyProperties(oldProperty, property);

            if (oldId != null) {
                property.setParentId(oldId);
            }
            property.setName(entry.getKey());
            if (ancestors != null) {
                property.setAncestors(ancestors);
            }
            property.setOrderNum(orderNum.getAndIncrement());
            // 设置id为null，让主键id自动生成，否则会冲突
            property.setId(null);

            // 判断是否为json对象
            Object value = entry.getValue();
            if (value instanceof JSONObject) {
                // 不存value值
                propertyMapper.insertProperty(property);
                buildTree((JSONObject) value, property);
            } else if (value instanceof JSONArray) {
                // json值，这里需要将时间属性作为父节点
                List<JSONObject> list = JSONUtil.toList((JSONArray) value, JSONObject.class);

                // 检测评定历史
                if (property.getName().equals("检测评定历史")) {
                    propertyMapper.insertProperty(property);
                    for (int i = 1; i <= list.size(); i++) {
                        JSONObject jO = list.get(i - 1);
                        // 设置评定时间为下一级节点
                        Property childProperty = new Property();
                        childProperty.setName(jO.getStr("评定时间"));
                        childProperty.setParentId(property.getId());
                        childProperty.setAncestors(property.getAncestors() + "," + property.getId());
                        childProperty.setOrderNum(i);
                        propertyMapper.insertProperty(childProperty);
                        buildTree(jO, childProperty);
                    }
                }

                // 养护处治记录，暂时不知道都有什么值，暂不处理！！！
                if (property.getName().equals("养护处治记录")) {
                    property.setValue(value.toString());
                    propertyMapper.insertProperty(property);
                }
            } else {
                String valueString = value.toString();
                // value为String值，即树的叶子节点
                if (valueString != null && !valueString.equals("null")) {
                    property.setValue(valueString);
                }
                propertyMapper.insertProperty(property);
            }

        });
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
        // 这里是根据属性名称查找的，应将其子属性也全都查找出来
        List<Property> properties = propertyMapper.selectPropertyList(property);
//        if (ReflectionUtils.isAllFieldsNull(property)) {
//            return properties;
//        }
        if (property.getName() == null || property.getName().equals("")) {
            return properties;
        }

        return properties.stream().flatMap(p -> {
            List<Property> ps = propertyMapper.selectChildrenObjectById(p.getId());
            ps.add(0, p);
            return ps.stream();
        }).collect(Collectors.toList());
    }

    /**
     * 新增属性
     *
     * @param property 属性
     * @return 结果
     */
    @Override
    public int insertProperty(Property property)
    {
        property.setCreateTime(DateUtils.getNowDate());

        Property info = propertyMapper.selectPropertyById(property.getParentId());
        // 如果父节点不存在，则不允许新增
        if (info == null)
        {
            throw new ServiceException("父节点不存在，不允许新增");
        }
        property.setAncestors(info.getAncestors() + "," + property.getParentId());
        // 设置显示顺序字段
        int count = propertyMapper.getOrderNum(property.getParentId());
        property.setOrderNum(count + 1);

        return propertyMapper.insertProperty(property);
    }

    /**
     * 修改属性
     *
     * @param property 属性
     * @return 结果
     */
    @Override
    public int updateProperty(Property property)
    {
        Property newParentObject = propertyMapper.selectPropertyById(property.getParentId());
        Property oldObject = propertyMapper.selectPropertyById(property.getId());
        if (StringUtils.isNotNull(newParentObject) && StringUtils.isNotNull(oldObject))
        {
            // 这里要判断一下其是否存在祖先节点, 否则存储到数据库中的值会出现 null,1 这样情况
            String newAncestors = (newParentObject.getAncestors() != null ? newParentObject.getAncestors() : "") + "," + newParentObject.getId();
            String oldAncestors = oldObject.getAncestors();
            property.setAncestors(newAncestors);
            updateObjectChildren(property.getId(), newAncestors, oldAncestors);
        }
        property.setUpdateTime(DateUtils.getNowDate());
        // 这里property的显示顺序字段重复不会造成什么影响，所以这里不做校验
        return propertyMapper.updateProperty(property);
    }

    /**
     * 修改子元素关系
     *
     * @param objectId 被修改的对象ID
     * @param newAncestors 新的父ID集合
     * @param oldAncestors 旧的父ID集合
     */
    public void updateObjectChildren(Long objectId, String newAncestors, String oldAncestors)
    {
        List<Property> children = propertyMapper.selectChildrenObjectById(objectId);
        for (Property child : children)
        {
            child.setAncestors(child.getAncestors().replaceFirst(oldAncestors, newAncestors));
        }
        if (children.size() > 0)
        {
            propertyMapper.updateObjectChildren(children);
        }
    }

    /**
     * 删除属性对象
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deletePropertyByIds(String ids)
    {
        String[] idArray = Convert.toStrArray(ids);
        propertyMapper.deleteObjectChildrenByIds(idArray);

        return propertyMapper.deletePropertyByIds(idArray);
    }

    /**
     * 删除属性信息
     *
     * @param id 属性ID
     * @return 结果
     */
    @Override
    @Transactional
    public int deletePropertyById(Long id)
    {
        // 删除逻辑怎么可能这么简单，直接删除会导致其子节点的父节点置空！

        Property property = propertyMapper.selectPropertyById(id);
        if (property == null) {
            throw new ServiceException("所要删除的桥梁属性不存在");
        }
        // 至少也要删除其子树的数据
        propertyMapper.deleteObjectChildren(id);

        return propertyMapper.deletePropertyById(id);
    }

    /**
     * 查询属性树列表
     *
     * @return 所有属性信息
     */
    @Override
    public List<Ztree> selectPropertyTree()
    {
        List<Property> propertyList = propertyMapper.selectPropertyList(new Property());
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

    /**
     * 得到祖级对象名层次
     *
     * @param objectId
     * @return
     */
    @Override
    public String getAncestorNames(Long objectId) {
        String result = "";
        Property property = this.selectPropertyById(objectId);
        String ancestors = property.getAncestors();
        String[] ancestorIdArray = ancestors.split(",");
        for (String ancestorId : ancestorIdArray) {
            if (ancestorId.length() > 0) {
                result += this.selectPropertyById(Long.parseLong(ancestorId)).getName();
                result += "-";
            }
        }
        return result;
    }

}
