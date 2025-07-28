package edu.whut.cs.bi.biz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Disease;
import java.util.List;

public interface IReportService {
  /**
   * 导出任务报告
   * 
   * @param taskId 任务ID
   * @return Word文档
   */
  XWPFDocument exportTaskReport(Long taskId);

  /**
   * 将对象树写入Word文档
   * 
   * @param doc        Word文档
   * @param node       当前节点
   * @param allNodes   所有节点
   * @param properties 病害属性
   * @param prefix     前缀
   * @param level      层级
   */
  void writeBiObjectTreeToWord(XWPFDocument doc, BiObject node, List<BiObject> allNodes,
      List<Disease> properties, String prefix, int level);

  /**
   * 导出属性Word文档
   * 
   * @param buildingId 建筑ID
   * @return Word文档
   */
  XWPFDocument exportPropertyWord(Long buildingId);

  /**
   * 调用后台 ai 模型获取报告中的病害 小结。
   * @param diseases
   * @return
   */
  String getDiseaseSummary(List<Disease> diseases) throws JsonProcessingException;
}