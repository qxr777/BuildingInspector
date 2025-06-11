package edu.whut.cs.bi.biz.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ruoyi.common.core.domain.Ztree;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.ShiroUtils;
import com.ruoyi.common.utils.StringUtils;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.Attachment;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.PropertyMapper;
import edu.whut.cs.bi.biz.service.AttachmentService;
import edu.whut.cs.bi.biz.service.IFileMapService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.springframework.beans.BeanUtils;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 建筑属性Service业务层处理
 *
 */
@Service
@Slf4j
public class PropertyServiceImpl implements IPropertyService {

    @Resource
    private PropertyMapper propertyMapper;

    @Resource
    private BuildingMapper buildingMapper;

    @Resource
    private IFileMapService fileMapService;

    @Resource
    private AttachmentService attachmentService;

    @Resource
    private FileMapController fileMapController;

    @Resource
    private TransactionTemplate transactionTemplate;

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

    @Override
    @Transactional
    public Boolean readJsonFile(MultipartFile file, Property property, Long buildingId) {
        if (file.isEmpty()) {
            throw new ServiceException("文件不能为空");
        }

        try {
            // 先删除原本的属性树
            Building bd = buildingMapper.selectBuildingById(buildingId);
            Long oldRootId = bd.getRootPropertyId();
            if (oldRootId != null) {
                this.deletePropertyById(oldRootId);
            }
            // 将文件内容转化成字符串
            String json = new String(file.getBytes(), "UTF-8");
            // 解析json数据
            JSONObject jsonObject = JSONUtil.parseObj(json, false);
            Long rootId = buildTree(jsonObject, property);
            // 更新建筑的root_property_id
            Building building = new Building();
            building.setId(buildingId);
            building.setRootPropertyId(rootId);
            building.setUpdateBy(ShiroUtils.getLoginName());
            buildingMapper.updateBuilding(building);

            // 同时更新属性树根节点的名称值
            Property p = new Property();
            p.setId(rootId);
            p.setName(bd.getName());
            propertyMapper.updateProperty(p);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    public Long buildTree(JSONObject jsonObject, Property oldProperty) {
        // 用于显示顺序， 只需要查找一轮数据库找到相同祖先的属性实体
        AtomicInteger orderNum = new AtomicInteger(1);
        Long oldId = oldProperty.getId();
        String ancestors;
        final Long[] rootId = new Long[1];
        if (oldId != null) {
            ancestors = (oldProperty.getAncestors() != null ? oldProperty.getAncestors() : "") + ","
                    + oldProperty.getId();
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
                rootId[0] = property.getId();
                buildTree((JSONObject) value, property);
            } else if (value instanceof JSONArray) {
                // json值，这里需要将第一个子节点的value作为父节点
                List<JSONObject> list = JSONUtil.toList((JSONArray) value, JSONObject.class);

                propertyMapper.insertProperty(property);
                // 检测评定历史
                if (property.getName().equals("检测评定历史")) {
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
                } else {
                    for (int i = 0; i < list.size(); i++) {
                        JSONObject jO = list.get(i);
                        // 设置评定时间为下一级节点
                        Property childProperty = new Property();
                        childProperty.setName(jO.getStr(jO.keySet().iterator().next()));
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
        return rootId[0];
    }

    /**
     * 查询属性列表
     *
     * @param property 属性
     * @return 属性
     */
    @Override
    public List<Property> selectPropertyList(Property property) {
        // 这里是根据属性名称查找的，应将其子属性也全都查找出来
        List<Property> properties = propertyMapper.selectPropertyList(property);
        // if (ReflectionUtils.isAllFieldsNull(property)) {
        // return properties;
        // }
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
    public int insertProperty(Property property) {
        property.setCreateTime(DateUtils.getNowDate());

        Property info = propertyMapper.selectPropertyById(property.getParentId());
        // 如果父节点不存在，则不允许新增
        if (info == null) {
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
    public int updateProperty(Property property) {
        Property newParentObject = propertyMapper.selectPropertyById(property.getParentId());
        Property oldObject = propertyMapper.selectPropertyById(property.getId());
        if (StringUtils.isNotNull(newParentObject) && StringUtils.isNotNull(oldObject)) {
            // 这里要判断一下其是否存在祖先节点, 否则存储到数据库中的值会出现 null,1 这样情况
            String newAncestors = (newParentObject.getAncestors() != null ? newParentObject.getAncestors() : "") + ","
                    + newParentObject.getId();
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
     * @param objectId     被修改的对象ID
     * @param newAncestors 新的父ID集合
     * @param oldAncestors 旧的父ID集合
     */
    public void updateObjectChildren(Long objectId, String newAncestors, String oldAncestors) {
        List<Property> children = propertyMapper.selectChildrenObjectById(objectId);
        for (Property child : children) {
            child.setAncestors(child.getAncestors().replaceFirst(oldAncestors, newAncestors));
        }
        if (children.size() > 0) {
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
    public int deletePropertyByIds(String ids) {
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
    public int deletePropertyById(Long id) {
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
    public List<Ztree> selectPropertyTree() {
        List<Property> propertyList = propertyMapper.selectPropertyList(new Property());
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

    /**
     * 获取属性树结构
     *
     * @param rootId
     * @return
     */
    @Override
    public Property selectPropertyTree(Long rootId) {
        Property root = selectPropertyById(rootId);
        if (root == null) {
            throw new ServiceException("未找到指定属性");
        }
        // 构建树结构
        buildTreeStructure(root);

        return root;
    }

    /**
     * 递归构建树结构
     */
    private void buildTreeStructure(Property node) {
        // 使用selectChildrenByParentId直接获取子节点
        List<Property> children = propertyMapper.selectChildrenByParentId(node.getId());
        if (!children.isEmpty()) {
            node.setChildren(children);
            // 递归处理每个子节点
            for (Property child : children) {
                buildTreeStructure(child);
            }
        }
    }

    /**
     * 读取word文件
     *
     * @param file
     * @param property
     * @param buildingId
     * @return
     */
    @Override
    public Boolean readWordFile(MultipartFile file, Property property, Long buildingId) {
        String jsonData = getJsonData(file);

        if (StringUtils.isEmpty(jsonData)) {
            throw new ServiceException("word解析失败！");
        }

        // 使用正则表达式去除多余的前缀和后缀
        jsonData = jsonData.replaceAll("^```json", "").replaceAll("```$", "");

        String finalJsonData = jsonData;
        final Long[] rootId = new Long[1];
        transactionTemplate.execute(status -> {
            try {
                // 先删除原本的属性树
                Building bd = buildingMapper.selectBuildingById(buildingId);
                Long oldRootId = bd.getRootPropertyId();
                if (oldRootId != null) {
                    // 预防建筑属性所有节点全被删除情况
                    this.deletePropertyById(oldRootId);
                }

                // 解析json数据
                JSONObject jsonObject = JSONUtil.parseObj(finalJsonData, false);
                rootId[0] = buildTree(jsonObject, property);
                // 更新建筑的root_property_id
                Building building = new Building();
                building.setId(buildingId);
                building.setRootPropertyId(rootId[0]);
                building.setUpdateBy(ShiroUtils.getLoginName());
                buildingMapper.updateBuilding(building);

                // 同时更新属性树根节点的名称值
                Property p = new Property();
                p.setId(rootId[0]);
                p.setName(bd.getName());
                propertyMapper.updateProperty(p);

                return true;
            } catch (Exception e) {
                status.setRollbackOnly();
                throw new ServiceException(e.getMessage());
            }
        });


        ExecutorService executorService = Executors.newFixedThreadPool(1);

        // todo 之后根据ai解析出来的结果调整name值（正立面照）
        CompletableFuture.runAsync(() -> {
            extractImagesFromWord(file, buildingId);

            Property p1 = propertyMapper.selectByRootIdAndName(rootId[0], "桥梁总体照片");
            Property p2 = propertyMapper.selectByRootIdAndName(rootId[0], "桥梁正面照片");

            List<FileMap> imageMaps = fileMapController.getImageMaps(buildingId);

            Map<String, List<String>> collect = imageMaps.stream().collect(Collectors.groupingBy(
                    image -> image.getOldName().split("_")[1],
                    Collectors.mapping(FileMap::getNewName, Collectors.toList())
            ));

            if (CollUtil.isNotEmpty(collect) && !collect.get("front").isEmpty()) {
                p1.setValue(collect.get("front").get(0));
                propertyMapper.updateProperty(p1);
            }
            if (CollUtil.isNotEmpty(collect) && !collect.get("side").isEmpty()) {
                p2.setValue(collect.get("side").get(0));
                propertyMapper.updateProperty(p2);
            }
        }, executorService)
                .whenComplete((r, ex) -> {
                    executorService.shutdown();
                    if (ex != null) {
                        log.error("添加桥梁属性正立面照失败！", ex);
                    }
                });

        return true;
    }

    /**
     * 从word中提取图片
     *
     * @param file
     * @param buildingId
     */
    public void extractImagesFromWord(MultipartFile file, Long buildingId) {
        List<MultipartFile> images = new ArrayList<>();

        // 将MultipartFile转换成InputStream
        try (InputStream inputStream = file.getInputStream()) {
            XWPFDocument document = new XWPFDocument(inputStream);

            // 遍历文档中的所有图片
            for (XWPFPictureData pictureData : document.getAllPictures()) {
                // 获取图片的二进制数据
                byte[] imageBytes = pictureData.getData();
                // imageBytes转为MultipartFile
                MultipartFile imageFile = new MockMultipartFile(
                        pictureData.getFileName(),
                        pictureData.getFileName(),
                        "image/jpeg",
                        imageBytes
                );
                images.add(imageFile);
            }
        } catch (IOException e) {
            log.error("从word中提取图片失败", e);
            throw new ServiceException("从word中提取图片失败");
        }

        List<FileMap> fileMaps = fileMapService.handleBatchFileUpload(images.toArray(new MultipartFile[0]));
        // 持久化
        String[] directions = {"front", "side"};
        for (int i = 0; i < fileMaps.size(); i++) {
            FileMap fileMap = fileMaps.get(i);
            Attachment attachment = new Attachment();
            attachment.setName(i + "_" + directions[i % 2] + "_" + fileMap.getOldName());
            attachment.setMinioId(Long.valueOf(fileMap.getId()));
            attachment.setSubjectId(buildingId);
            attachment.setType(2);
            attachmentService.insertAttachment(attachment);
        }

    }

    /**
     * 获取json数据
     *
     * @param file
     * @return
     */
    public String getJsonData(MultipartFile file) {
        String host = "47.94.205.90";
        int port = 8081;
        String url = "http://" + host + ":" + port + "/api/word2Json";

        try {
            // 构建Multipart请求体
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", file.getResource());  // 将MultipartFile转换为Resource

            // 使用WebClient发送请求
            String response = WebClient.create()
                    .post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(body))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            return response;
        } catch (Exception e) {
            log.error("Word文件转换JSON失败", e);
            throw new RuntimeException("文件处理失败: " + e.getMessage());
        }
    }
}
