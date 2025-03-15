package edu.whut.cs.bm.biz.controller;

import edu.whut.cs.bm.biz.domain.BmObject;
import edu.whut.cs.bm.biz.service.IBmObjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

/**
 * 全场地图
 * 
 * @author qixin
 */
@Controller
@RequestMapping("/biz/fullmap")
public class FullMapController {

	private String prefix = "biz/fullmap";
	@Autowired
	private IBmObjectService bmObjectService;
	
	@GetMapping()
	public String fullMap(ModelMap map)
	{
		return prefix + "/fullmap";
	}
	
	@RequestMapping("/objectList")
	@ResponseBody
	public List<BmObject> objectList(){
		List<BmObject> bmObjects = bmObjectService.selectBmObjectList(null);
		List<BmObject> result = new ArrayList<BmObject>();
		for (BmObject bmObject : bmObjects) {
			if (bmObject.getLatitude() != null && bmObject.getLongitude() != null) {
				result.add(bmObject);
			}
		}
		return result;
	}
}
