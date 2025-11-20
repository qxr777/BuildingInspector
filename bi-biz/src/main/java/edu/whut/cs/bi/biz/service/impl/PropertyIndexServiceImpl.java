package edu.whut.cs.bi.biz.service.impl;


import cn.hutool.core.collection.CollectionUtil;
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
import com.ruoyi.system.service.ISysDictDataService;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.PropertyMapper;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IPropertyIndexService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static com.ruoyi.common.utils.PageUtils.startPage;


/**
 * 建筑属性Service业务层处理
 */
@Slf4j
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

    @Resource
    private ISysDictDataService dictDataService;

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
                        Property property = propertyService.selectPropertyById(oldRootId);
                        if (property != null)
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

    // 线程池配置（核心线程数、最大线程数、队列容量可根据服务器配置调整）
    private final ExecutorService executorService = new ThreadPoolExecutor(
            8, // 核心线程数
            16, // 最大线程数
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000), // 任务队列容量
            new ThreadFactory() {
                private int count = 0;
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "excel-import-thread-" + (++count));
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 队列满时，主线程兜底执行（避免任务丢失）
    );

    // 线程安全的未匹配桥梁列表（CopyOnWriteArrayList适合读多写少场景）
    private final CopyOnWriteArrayList<String> unmatchedBuildings = new CopyOnWriteArrayList<>();

    @Override
    public List<String> batchImportPropertyData(MultipartFile file) {
        // 清空上次导入的未匹配数据
        unmatchedBuildings.clear();
        List<Building> allBuildings = buildingService.selectBuildingList(new Building());
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            // 读取第一个工作表（如需多工作表，可循环处理）
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new ServiceException("Excel无数据");
            }
            Row titleRow = sheet.getRow(0);
            if (titleRow == null) {
                throw new ServiceException("Excel无表头");
            }

            // 1. 收集需要处理的行数据（跳过表头，i从1开始）
            List<Row> dataRows = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            for (int i = 1; i <= lastRowNum; i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    dataRows.add(row);
                }
            }

            // 2. 多线程处理每行数据（提交任务到线程池）
            List<CompletableFuture<Void>> futures = dataRows.stream()
                    .map(row -> CompletableFuture.runAsync(
                            () -> processSingleRow(allBuildings, row, titleRow), // 每行的处理逻辑
                            executorService // 指定线程池
                    ))
                    .collect(Collectors.toList());

            // 3. 等待所有任务执行完成（阻塞主线程，直到全部处理完毕）
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } catch (IOException e) {
            throw new ServiceException("导入数据错误：" + e.getMessage());
        } finally {
            // 关闭线程池（避免资源泄漏）
            executorService.shutdown();
            try {
                // 等待60秒，若仍未关闭则强制终止
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt(); // 恢复中断状态
            }
        }

        // 返回未匹配的桥梁列表（转成ArrayList方便前端处理）
        return new ArrayList<>(unmatchedBuildings);
    }

    /**
     * 处理单行数据（独立事务，每条数据失败不影响其他数据）
     * @param row 数据行
     * @param titleRow 表头行
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void processSingleRow(List<Building> allBuildings, Row row, Row titleRow) {
        try {
            // 1. 读取行数据（与原逻辑一致）
            String buildingName = getCellValueAsString(row.getCell(1));
            String lineCode = getCellValueAsString(row.getCell(5));
            String areaName = getCellValueAsString(row.getCell(10));

            // 2. 校验行政区域
//            SysDictData dictData = new SysDictData();
//            dictData.setDictType("bi_building_area");
//            dictData.setDictLabel(areaName);
//            dictData.setStatus("0");
//            List<SysDictData> areaCodeList = dictDataService.selectDictDataListForApi(dictData);
//            if (CollectionUtil.isEmpty(areaCodeList)) {
//                unmatchedBuildings.add(buildingName);
//                return;
//            }
            // 3. 查询桥梁（确保唯一匹配）
            Building bd = allBuildings.stream().filter(b ->
                            b.getName().equals(buildingName) && b.getLine().equals(lineCode) && b.getStatus().equals("0"))
                    .findFirst().orElse(null);

//            Building queryBuilding = new Building();
//            queryBuilding.setName(buildingName);
//            queryBuilding.setLine(lineCode);
//            queryBuilding.setArea(areaCodeList.get(0).getDictValue());
//            queryBuilding.setStatus("0");
//            List<Building> buildings = buildingService.selectBuildingList(queryBuilding);
//            dictData.setDictLabel("恩施州");
//            areaCodeList = dictDataService.selectDictDataListForApi(dictData);
//            queryBuilding.setArea(areaCodeList.get(0).getDictValue());
//            buildings.addAll(buildingService.selectBuildingList(queryBuilding));
//            if (CollectionUtil.isEmpty(buildings)) {
//                unmatchedBuildings.add(buildingName);
//                return;
//            }

            if (bd == null) {
                unmatchedBuildings.add(buildingName);
                return;
            }

            // 4. 删除原有属性树（与原逻辑一致）
            Long oldRootId = bd.getRootPropertyId();
            if (oldRootId != null && oldRootId != 0) {
                Property property = propertyService.selectPropertyById(oldRootId);
                if (property != null)
                    propertyService.deletePropertyById(oldRootId);
            }

            // 5. 构造根节点
            Property rootProperty = new Property();
            rootProperty.setCreateTime(DateUtils.getNowDate());
            rootProperty.setCreateBy(ShiroUtils.getLoginName());
            rootProperty.setOrderNum(1);
            rootProperty.setName(buildingName);
            propertyMapper.insertProperty(rootProperty);

            // 6. 更新桥梁根属性ID
            bd.setRootPropertyId(rootProperty.getId());
            buildingMapper.updateBuilding(bd);

            // 7. 构造子节点（抽取通用方法，减少重复代码）
            int curCol = 3; // 起始列
            int curOrder = 1;
            // 基础数据（12个字段）
            curCol = createPropertyNode(rootProperty, titleRow, row, curCol, curOrder++, "基础数据", 12);
            // 行政识别（50个字段）
            curCol = createPropertyNode(rootProperty, titleRow, row, curCol, curOrder++, "行政识别", 50);
            // 技术指标（30个字段）
            curCol = createPropertyNode(rootProperty, titleRow, row, curCol, curOrder++, "技术指标", 30);
            // 结构信息（12个字段）
            curCol = createPropertyNode(rootProperty, titleRow, row, curCol, curOrder++, "结构信息", 12);
            // 其他数据（22个字段）
            curCol = createPropertyNode(rootProperty, titleRow, row, curCol, curOrder++, "其他数据", 22);
            // 桥牌信息（13个字段）
            createPropertyNode(rootProperty, titleRow, row, curCol, curOrder, "桥牌信息", 13);

        } catch (Exception e) {
            // 记录异常日志
            log.error("处理行数据失败（桥梁名称：{}）", getCellValueAsString(row.getCell(1)));
            log.error(e.getMessage());
            // 失败的行也加入未匹配列表（便于前端排查）
            unmatchedBuildings.add(getCellValueAsString(row.getCell(1)));
//            // 抛出异常触发事务回滚
//            throw new ServiceException("处理行数据失败：" + e.getMessage());
        }
    }

    /**
     * 通用创建属性节点方法（减少重复代码）
     * @param rootProperty 根节点
     * @param titleRow 表头行
     * @param dataRow 数据行
     * @param startCol 起始列
     * @param nodeName 节点名称
     * @param fieldCount 字段数量
     * @return 下一个节点的起始列
     */
    private int createPropertyNode(Property rootProperty, Row titleRow, Row dataRow,
                                   int startCol, int curOrder, String nodeName, int fieldCount) {
        // 创建二级节点
        Property secondNode = new Property();
        secondNode.setParentId(rootProperty.getId());
        secondNode.setAncestors("," + rootProperty.getId());
        secondNode.setCreateBy(ShiroUtils.getLoginName());
        secondNode.setCreateTime(DateUtils.getNowDate());
        secondNode.setOrderNum(curOrder); // 获取下一个排序号（需实现）
        secondNode.setName(nodeName);
        propertyMapper.insertProperty(secondNode);

        // 创建三级节点（字段节点）
        for (int k = 0; k < fieldCount; k++) {
            int colIndex = startCol + k;
            String key = getCellValueAsString(titleRow.getCell(colIndex));
            String value = getCellValueAsString(dataRow.getCell(colIndex));

            Property thirdNode = new Property();
            thirdNode.setName(key);
            thirdNode.setValue(value);
            thirdNode.setParentId(secondNode.getId());
            thirdNode.setOrderNum(k + 1);
            thirdNode.setAncestors(secondNode.getAncestors() + "," + secondNode.getId());
            thirdNode.setCreateBy(ShiroUtils.getLoginName());
            thirdNode.setCreateTime(DateUtils.getNowDate());
            propertyMapper.insertProperty(thirdNode);
        }

        // 返回下一个节点的起始列
        return startCol + fieldCount;
    }


    /**
     * 获取单元格值
     */
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
