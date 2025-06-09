package edu.whut.cs.bi.biz.service.impl;

import java.util.List;
import java.util.ArrayList;

import com.ruoyi.common.utils.ShiroUtils;
import edu.whut.cs.bi.biz.domain.dto.CodeSegment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import edu.whut.cs.bi.biz.mapper.ComponentMapper;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.domain.Component;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.service.IComponentService;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.utils.DateUtils;

/**
 * 构件管理Service业务层处理
 */
@Service
public class ComponentServiceImpl implements IComponentService {
    @Autowired
    private ComponentMapper componentMapper;

    @Autowired
    private BiObjectMapper biObjectMapper;

    /**
     * 查询构件
     *
     * @param id 构件ID
     * @return 构件
     */
    @Override
    public Component selectComponentById(Long id) {
        return componentMapper.selectComponentById(id);
    }

    /**
     * 查询构件列表
     *
     * @param component 构件
     * @return 构件
     */
    @Override
    public List<Component> selectComponentList(Component component) {
        return componentMapper.selectComponentList(component);
    }

    /**
     * 查询部件下的构件列表
     *
     * @param biObjectId 部件ID
     * @return 构件集合
     */
    @Override
    public List<Component> selectComponentsByBiObjectId(Long biObjectId) {
        return componentMapper.selectComponentsByBiObjectId(biObjectId);
    }

    /**
     * 查询部件下的构件列表
     *
     * @param biObjectId 部件ID
     * @return 构件集合
     */
    @Override
    public List<Component> selectComponentsByBiObjectIdApi(Long biObjectId) {
        return componentMapper.selectComponentsByBiObjectIdApi(biObjectId);
    }

    /**
     * 查询部件及其子部件下的构件列表
     *
     * @param biObjectId 部件ID
     * @return 构件集合
     */
    @Override
    public List<Component> selectComponentsByBiObjectIdAndChildren(Long biObjectId) {
        return componentMapper.selectComponentsByBiObjectIdAndChildren(biObjectId);
    }

    /**
     * 新增构件
     *
     * @param component 构件
     * @return 结果
     */
    @Override
    public int insertComponent(Component component) {
        component.setCreateTime(DateUtils.getNowDate());
        return componentMapper.insertComponent(component);
    }

    /**
     * 修改构件
     *
     * @param component 构件
     * @return 结果
     */
    @Override
    public int updateComponent(Component component) {
        component.setUpdateTime(DateUtils.getNowDate());
        return componentMapper.updateComponent(component);
    }

    /**
     * 删除构件对象
     *
     * @param ids 需要删除的数据ID
     * @return 结果
     */
    @Override
    public int deleteComponentByIds(String ids) {
        return componentMapper.deleteComponentByIds(Convert.toStrArray(ids));
    }

    /**
     * 删除构件信息
     *
     * @param id 构件ID
     * @return 结果
     */
    @Override
    public int deleteComponentById(Long id) {
        return componentMapper.deleteComponentById(id);
    }

    /**
     * 批量生成构件
     *
     * @param biObjectId 部件ID
     * @param segments   编号片段列表
     * @param nameSuffix 构件名称后缀
     * @return 结果
     */
    @Override
    public int generateComponents(Long biObjectId, List<CodeSegment> segments, String nameSuffix) {
        // 获取部件信息
        BiObject biObject = biObjectMapper.selectBiObjectById(biObjectId);
        if (biObject == null) {
            throw new RuntimeException("部件不存在");
        }

        // 计算需要生成的构件总数
        int totalCount = 1;
        for (CodeSegment segment : segments) {
            if (segment.getType() == 2) { // 序号类型
                totalCount *= (segment.getMaxValue() - segment.getMinValue() + 1);
            }
        }
        biObject.setCount(totalCount);
        // 生成所有可能的编号组合
        List<String> codes = generateCodes(segments, biObject.getName());

        // 批量插入构件
        int successCount = 0;
        for (int i = 0; i < codes.size(); i++) {
            Component component = new Component();
            component.setBiObjectId(biObjectId);
            // 构件名称为构件编号，如果有后缀则加上后缀
            String componentName = codes.get(i);
            if (nameSuffix != null && !nameSuffix.trim().isEmpty()) {
                componentName = componentName + nameSuffix;
            }
            component.setName(componentName);
            component.setCode(codes.get(i));
            component.setStatus("0"); // 默认正常状态
            component.setCreateTime(DateUtils.getNowDate());
            component.setCreateBy(ShiroUtils.getLoginName());

            if (componentMapper.insertComponent(component) > 0) {
                successCount++;
            }
        }
        biObjectMapper.updateBiObject(biObject);
        return successCount;
    }

    /**
     * 生成所有可能的编号组合
     *
     * @param segments   编号片段列表
     * @param objectName 部件名称
     * @return 编号列表
     */
    private List<String> generateCodes(List<CodeSegment> segments, String objectName) {
        List<String> codes = new ArrayList<>();
        generateCodesRecursive(segments, 0, new ArrayList<>(), codes, objectName);
        return codes;
    }

    /**
     * 递归生成编号组合
     *
     * @param segments     编号片段列表
     * @param index        当前处理的片段索引
     * @param currentParts 当前已生成的片段
     * @param results      结果列表
     * @param objectName   部件名称
     */
    private void generateCodesRecursive(List<CodeSegment> segments, int index, List<String> currentParts,
                                        List<String> results, String objectName) {
        if (index == segments.size()) {
            // 只使用 "-" 连接片段
            String code = String.join("-", currentParts);
            results.add(code);
            return;
        }

        CodeSegment segment = segments.get(index);
        if (segment.getType() == 1) { // 固定值
            currentParts.add(segment.getValue());
            generateCodesRecursive(segments, index + 1, currentParts, results, objectName);
            currentParts.remove(currentParts.size() - 1);
        } else { // 序号
            for (int i = segment.getMinValue(); i <= segment.getMaxValue(); i++) {
                currentParts.add(String.valueOf(i));
                generateCodesRecursive(segments, index + 1, currentParts, results, objectName);
                currentParts.remove(currentParts.size() - 1);
            }
        }
    }

    /**
     * 批量插入构件
     *
     * @param components 构件集合
     * @return 结果
     */
    @Override
    public int batchInsertComponents(List<Component> components) {
        if (components == null || components.isEmpty()) {
            return 0;
        }

        // 提取第一个组件的biObjectId来更新计数
        if (!components.isEmpty() && components.get(0).getBiObjectId() != null) {
            Long biObjectId = components.get(0).getBiObjectId();
            BiObject biObject = biObjectMapper.selectBiObjectById(biObjectId);
            if (biObject != null) {
                biObject.setCount(biObject.getCount() + components.size());
                biObject.setUpdateBy(ShiroUtils.getLoginName());
                biObject.setUpdateTime(DateUtils.getNowDate());
                biObjectMapper.updateBiObject(biObject);
            }
        }

        return componentMapper.batchInsertComponents(components);
    }

    /**
     * 批量删除对应BiObjectId的构件
     *
     * @param biObjectId 部件ID
     * @return 结果
     */
    @Override
    public int deleteComponentsByBiObjectId(Long biObjectId) {
        if (biObjectId == null) {
            return 0;
        }

        // 查询构件列表
        List<Component> components = componentMapper.selectComponentsByBiObjectId(biObjectId);
        if (components.isEmpty()) {
            return 0;
        }

        // 组织ID列表
        String[] ids = components.stream()
                .map(c -> c.getId().toString())
                .toArray(String[]::new);

        // 更新部件构件计数
        BiObject biObject = biObjectMapper.selectBiObjectById(biObjectId);
        if (biObject != null) {
            biObject.setCount(0); // 重置计数
            biObject.setUpdateBy(ShiroUtils.getLoginName());
            biObject.setUpdateTime(DateUtils.getNowDate());
            biObjectMapper.updateBiObject(biObject);
        }

        // 批量删除
        return componentMapper.deleteComponentByIds(ids);
    }

    @Override
    public int batchUpdateComponents(List<Component> components) {
        return componentMapper.batchUpdateComponents(components);
    }

    @Override
    public Component selectComponent(Component component) {
        return componentMapper.selectComponent(component);
    }
}