package edu.whut.cs.bi.biz.service.impl;


import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.core.domain.entity.SysDictData;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.PropertyMapper;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IPropertyIndexService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
 */
@Service
public class PropertyIndexServiceImpl implements IPropertyIndexService {
    @Resource
    private PropertyMapper propertyMapper;

    @Resource
    private IBuildingService buildingService;

    @Resource
    private IPropertyService propertyService;

    @Resource
    private BuildingMapper buildingMapper;

    /**
     * 查询属性
     *
     * @param id 属性ID
     * @return 属性
     */
    @Override
    public Property selectPropertyById(Long id) {
        return propertyMapper.selectPropertyById(id);
    }

    /**
     * 查询属性列表
     *
     * @param property 属性
     * @return 属性
     */
    @Override
    public List<Property> selectPropertyList(Property property) {
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
        for (Property property : propertyList) {
            Ztree ztree = new Ztree();
            ztree.setId(property.getId());
            ztree.setpId(property.getParentId());
            ztree.setName(property.getName());
            ztree.setTitle(property.getName());
            ztrees.add(ztree);
        }
        return ztrees;
    }

    @Override
    public List<Ztree> selectPropertyTree(Long rootPropertyId) {
        Property ps = propertyMapper.selectPropertyById(rootPropertyId);

        // 节点顺序要求
        List<Property> propertyList = new ArrayList<>();

        propertyList.add(ps);
        List<Property> properties = propertyMapper.selectChildrenObjectById(rootPropertyId);
        propertyList.addAll(properties);

        // 封装成 Ztree
        List<Ztree> ztrees = new ArrayList<>();
        for (Property property : propertyList) {
            Ztree ztree = new Ztree();
            ztree.setId(property.getId());
            ztree.setpId(property.getParentId());
            ztree.setName(property.getName());
            ztree.setTitle(property.getName());
            ztrees.add(ztree);
        }
        return ztrees;
    }

    @Override
    @Transactional
    public Boolean readExcelPropertyData(MultipartFile file, Long buildingId) {

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            for (int j = 0; j < 1; j++) {
                Sheet sheet = workbook.getSheetAt(j); // 获取第一个工作表
                Row title = sheet.getRow(0);
                for (int i = 1; i <= 1; i++) {
                    Row row = sheet.getRow(i);
                    if (row == null) continue;
                        // 批量导入准备
                    String BuildingName = getCellValueAsString(row.getCell(1));
//                    String lineCode = getCellValueAsString(row.getCell(5));
//                    String areaName = getCellValueAsString(row.getCell(10));
//                    // 这里是政区label
//                    SysDictData dictData = new SysDictData();
//                    dictDataService.selectDictDataListForApi(dictData)

                    // 先删除原本的属性树
                    Building bd = buildingService.selectBuildingById(buildingId);
                    Long oldRootId = bd.getRootPropertyId();
                    if (oldRootId != null) {
                        propertyService.deletePropertyById(oldRootId);
                    }

                    // 构造根节点
                    Property property = new Property();
                    property.setCreateTime(DateUtils.getNowDate());
                    property.setCreateBy(ShiroUtils.getLoginName());
                    property.setOrderNum(1);
                    property.setName(BuildingName);
                    propertyMapper.insertProperty(property);

                    // 更新桥梁根属性树节点
                    bd.setRootPropertyId(property.getId());
                    buildingMapper.updateBuilding(bd);

                    int cur = 3;
                    int num = 1;
                    // 构造基础数据
                    property.setParentId(property.getId());
                    property.setAncestors("," + property.getId());
                    property.setId(null);
                    property.setName("基础数据");
                    property.setOrderNum(num++);
                    propertyMapper.insertProperty(property);
                    // 填补第三层节点
                    for (int k = 0;k < 12;k++) {
                        String key = getCellValueAsString(title.getCell(cur + k));
                        String value = getCellValueAsString(row.getCell(cur + k));
                        Property child = new Property();
                        child.setName(key);
                        child.setValue(value);
                        child.setParentId(property.getId());
                        child.setOrderNum(k + 1);
                        child.setAncestors(property.getAncestors() + "," + property.getId());
                        child.setCreateBy(ShiroUtils.getLoginName());
                        child.setCreateTime(DateUtils.getNowDate());
                        propertyMapper.insertProperty(child);
                    }
                    cur += 12;

                    // 构造行政识别
                    property.setId(null);
                    property.setOrderNum(num++);
                    property.setName("行政识别");
                    propertyMapper.insertProperty(property);
                    // 填补第三层节点
                    for (int k = 0;k < 50;k++) {
                        String key = getCellValueAsString(title.getCell(cur + k));
                        String value = getCellValueAsString(row.getCell(cur + k));
                        Property child = new Property();
                        child.setName(key);
                        child.setValue(value);
                        child.setParentId(property.getId());
                        child.setOrderNum(k + 1);
                        child.setAncestors(property.getAncestors() + "," + property.getId());
                        child.setCreateBy(ShiroUtils.getLoginName());
                        child.setCreateTime(DateUtils.getNowDate());
                        propertyMapper.insertProperty(child);
                    }
                    cur += 50;

                    // 构造技术指标
                    property.setId(null);
                    property.setOrderNum(num++);
                    property.setName("技术指标");
                    propertyMapper.insertProperty(property);
                    // 填补第三层节点
                    for (int k = 0;k < 30;k++) {
                        String key = getCellValueAsString(title.getCell(cur + k));
                        String value = getCellValueAsString(row.getCell(cur + k));
                        Property child = new Property();
                        child.setName(key);
                        child.setValue(value);
                        child.setParentId(property.getId());
                        child.setOrderNum(k + 1);
                        child.setAncestors(property.getAncestors() + "," + property.getId());
                        child.setCreateBy(ShiroUtils.getLoginName());
                        child.setCreateTime(DateUtils.getNowDate());
                        propertyMapper.insertProperty(child);
                    }
                    cur += 30;

                    // 构造结构信息
                    property.setId(null);
                    property.setOrderNum(num++);
                    property.setName("结构信息");
                    propertyMapper.insertProperty(property);
                    // 填补第三层节点
                    for (int k = 0;k < 12;k++) {
                        String key = getCellValueAsString(title.getCell(cur + k));
                        String value = getCellValueAsString(row.getCell(cur + k));
                        Property child = new Property();
                        child.setName(key);
                        child.setValue(value);
                        child.setParentId(property.getId());
                        child.setOrderNum(k + 1);
                        child.setAncestors(property.getAncestors() + "," + property.getId());
                        child.setCreateBy(ShiroUtils.getLoginName());
                        child.setCreateTime(DateUtils.getNowDate());
                        propertyMapper.insertProperty(child);
                    }
                    cur += 12;

                    // 构造其他数据
                    property.setId(null);
                    property.setOrderNum(num++);
                    property.setName("其他数据");
                    propertyMapper.insertProperty(property);
                    // 填补第三层节点
                    for (int k = 0;k < 22;k++) {
                        String key = getCellValueAsString(title.getCell(cur + k));
                        String value = getCellValueAsString(row.getCell(cur + k));
                        Property child = new Property();
                        child.setName(key);
                        child.setValue(value);
                        child.setParentId(property.getId());
                        child.setOrderNum(k + 1);
                        child.setAncestors(property.getAncestors() + "," + property.getId());
                        child.setCreateBy(ShiroUtils.getLoginName());
                        child.setCreateTime(DateUtils.getNowDate());
                        propertyMapper.insertProperty(child);
                    }
                    cur += 22;

                    // 构造桥牌信息
                    property.setId(null);
                    property.setOrderNum(num++);
                    property.setName("桥牌信息");
                    propertyMapper.insertProperty(property);
                    // 填补第三层节点
                    for (int k = 0;k < 13;k++) {
                        String key = getCellValueAsString(title.getCell(cur + k));
                        String value = getCellValueAsString(row.getCell(cur + k));
                        Property child = new Property();
                        child.setName(key);
                        child.setValue(value);
                        child.setParentId(property.getId());
                        child.setOrderNum(k + 1);
                        child.setAncestors(property.getAncestors() + "," + property.getId());
                        child.setCreateBy(ShiroUtils.getLoginName());
                        child.setCreateTime(DateUtils.getNowDate());
                        propertyMapper.insertProperty(child);
                    }
                }
            }
        } catch (IOException e) {
            throw new ServiceException("导入数据错误：" + e.getMessage());
        }

        return true;
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    double numericValue = cell.getNumericCellValue();
                    if (numericValue == (long) numericValue) {
                        return String.valueOf((long) numericValue); // 去掉 .0
                    } else {
                        return String.valueOf(numericValue); // 保留小数
                    }
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

}
