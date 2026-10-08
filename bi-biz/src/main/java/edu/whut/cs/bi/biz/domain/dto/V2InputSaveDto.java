package edu.whut.cs.bi.biz.domain.dto;

import edu.whut.cs.bi.biz.domain.V2AbsentMark;
import edu.whut.cs.bi.biz.domain.V2ComponentInput;
import edu.whut.cs.bi.biz.domain.V2SingleControlMark;
import lombok.Data;

import java.util.List;

/**
 * 新标评定输入整批保存请求（JTG/T 5230—2026）。
 *
 * <p>三组评定人输入一次性提交：构件人工标度、应设未设标记、单项控制指标标记。
 * 服务端按任务全量覆盖保存（先删后插）。</p>
 */
@Data
public class V2InputSaveDto {

    /** 构件人工标度（EDDI/EFI/EAI、安全影响标记） */
    private List<V2ComponentInput> components;

    /** 应设未设部件标记 */
    private List<V2AbsentMark> absents;

    /** 单项控制指标标记 */
    private List<V2SingleControlMark> controls;
}
