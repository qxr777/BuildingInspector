package edu.whut.cs.bi.api.controller;

import com.alibaba.fastjson.JSONObject;
import com.ruoyi.common.core.domain.AjaxResult;
import edu.whut.cs.bi.api.vo.PropertyTreeVo;
import edu.whut.cs.bi.biz.controller.FileMapController;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.FileMap;
import edu.whut.cs.bi.biz.domain.Property;
import edu.whut.cs.bi.biz.service.IBiObjectService;
import edu.whut.cs.bi.biz.service.IBuildingService;
import edu.whut.cs.bi.biz.service.IPropertyService;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @author Junbo
 * @date 2025/6/11
 */
@Controller
@RequestMapping("/toolCallingApi")
public class ToolCallingController {
    @Resource
    private FileMapController fileMapController;
    @Resource
    private IBuildingService buildingService;
    @Resource
    private IPropertyService propertyService;
    @Resource
    private IBiObjectService biObjectService;

    @GetMapping("/building/{bName}/property")
    @ResponseBody
    public AjaxResult getProperty(@PathVariable("bName") String bName) {
        if (bName == null) {
            return AjaxResult.error("参数错误");
        }
        Building building_name = new Building();
        building_name.setName(bName);
        Long buildingId = buildingService.selectBuildingList(building_name).get(0).getId();
        Building building = buildingService.selectBuildingById(buildingId);
        List<FileMap> imageMaps = fileMapController.getImageMaps(buildingId,"newfront","newside");
        Map<String, List<String>> collect = imageMaps.stream().collect(Collectors.groupingBy(
                image -> image.getOldName().split("_")[1],
                Collectors.mapping(FileMap::getNewName, Collectors.toList())
        ));
        Property property = propertyService.selectPropertyTree(building.getRootPropertyId());

        // 封装返回结果
        PropertyTreeVo propertyTreeVo = new PropertyTreeVo();
        propertyTreeVo.setProperty(property);
        propertyTreeVo.setImages(collect);
        return AjaxResult.success("查询成功", propertyTreeVo);
    }


    @GetMapping("/building/{bName}/object")
    @ResponseBody
    public AjaxResult getObjectTree(@PathVariable("bName") String bName) {
        try {
            // 查询建筑物的root_object_id
            Building building_query = new Building();
            building_query.setName(bName);
            Building building = buildingService.selectBuildingList(building_query).get(0);
            if (building == null || building.getRootObjectId() == null) {
                return AjaxResult.error("未找到指定的建筑物或其结构信息");
            }

            // 获取对象树的JSON结构
            String jsonTree = biObjectService.bridgeStructureJson(building.getRootObjectId());
            JSONObject jsonObject = JSONObject.parseObject(jsonTree);
            return AjaxResult.success("ObjectTree success", jsonObject);
        } catch (Exception e) {
            return AjaxResult.error(e.getMessage());
        }
    }



}
