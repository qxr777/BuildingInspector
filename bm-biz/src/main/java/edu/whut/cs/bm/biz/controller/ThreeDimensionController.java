package edu.whut.cs.bm.biz.controller;

import edu.whut.cs.bm.biz.domain.BmObject;
import edu.whut.cs.bm.biz.service.IBmObjectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 3d模型
 *
 * @author qixin
 */
@Controller
@RequestMapping("/biz/3d")
public class ThreeDimensionController {

	private String prefix = "biz/3d";

	@GetMapping()
	public String fullMap(ModelMap map)
	{
		return prefix + "/index";
	}

	@GetMapping("/threejs")
	public String threejs(ModelMap map)
	{
		return prefix + "/threejs";
	}

	@GetMapping("/loadFbx")
	public String loadFbx(ModelMap map)
	{
		return prefix + "/loadFbx";
	}

}
