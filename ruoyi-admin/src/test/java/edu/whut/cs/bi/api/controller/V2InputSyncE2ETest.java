package edu.whut.cs.bi.api.controller;

import com.ruoyi.RuoYiApplication;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.Building;
import edu.whut.cs.bi.biz.domain.Task;
import edu.whut.cs.bi.biz.domain.V2AbsentMark;
import edu.whut.cs.bi.biz.domain.V2ComponentInput;
import edu.whut.cs.bi.biz.domain.V2SingleControlMark;
import edu.whut.cs.bi.biz.domain.vo.SyncResultVo;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.BuildingMapper;
import edu.whut.cs.bi.biz.mapper.TaskMapper;
import edu.whut.cs.bi.biz.mapper.V2AbsentMarkMapper;
import edu.whut.cs.bi.biz.mapper.V2ComponentInputMapper;
import edu.whut.cs.bi.biz.mapper.V2SingleControlMarkMapper;
import edu.whut.cs.bi.biz.service.ISyncUploadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 5230 人工评定输入 App 同步链路 E2E：
 * Object + Component + 三张输入表一次打包上传，服务端按 offline_uuid 入库。
 *
 * <p>默认随 @Transactional 回滚，不产生残留；@Tag("E2E") 隔离，离线跑批不执行。</p>
 */
@SpringBootTest(classes = RuoYiApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "minio.url=http://localhost:9000"
})
@Tag("E2E")
@Transactional
public class V2InputSyncE2ETest {

    @Autowired
    private ISyncUploadService syncUploadService;
    @Autowired
    private BuildingMapper buildingMapper;
    @Autowired
    private BiObjectMapper biObjectMapper;
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private V2ComponentInputMapper v2ComponentInputMapper;
    @Autowired
    private V2AbsentMarkMapper v2AbsentMarkMapper;
    @Autowired
    private V2SingleControlMarkMapper v2SingleControlMarkMapper;

    private Long taskId;
    private Long partServerId;

    private String unitUuid;
    private String partUuid;
    private String compUuid;
    private String ciUuid;
    private String amUuid;
    private String scUuid;

    /**
     * 预置云端下发数据：Building + UNIT 节点(B03) + PART 节点(主梁) + 5230 Task。
     */
    @BeforeEach
    void prepareCloudData() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        unitUuid = "e2e-unit-" + suffix;
        partUuid = "e2e-part-" + suffix;
        compUuid = "e2e-comp-" + suffix;
        ciUuid = "e2e-ci-" + suffix;
        amUuid = "e2e-am-" + suffix;
        scUuid = "e2e-sc-" + suffix;

        Building building = new Building();
        building.setName("E2E-5230桥-" + suffix);
        buildingMapper.insertBuilding(building);

        BiObject unit = new BiObject();
        unit.setOfflineUuid(unitUuid);
        unit.setName("评定单元");
        unit.setNodeType("UNIT");
        unit.setBridgeType("B03");
        unit.setParentId(0L);
        biObjectMapper.insertBiObject(unit);

        BiObject part = new BiObject();
        part.setOfflineUuid(partUuid);
        part.setName("主梁");
        part.setNodeType("PART");
        part.setParentId(unit.getId());
        biObjectMapper.insertBiObject(part);
        partServerId = part.getId();

        Building update = new Building();
        update.setId(building.getId());
        update.setRootObjectId(unit.getId());
        buildingMapper.updateBuilding(update);

        Task task = new Task();
        task.setBuildingId(building.getId());
        task.setStdVersion("5230-2026");
        task.setStatus("0");
        taskMapper.insertTask(task);
        taskId = task.getId();
    }

    @Test
    @DisplayName("E2E-V2-001: 一次打包上传 Component+构件输入+应设未设+单项控制，全部按 UUID 入库")
    void testSyncV2Inputs_HappyPath() {
        Map<String, Object> payload = basePayload();

        // objects：UNIT/PART 同 UUID 上行，服务端更新并填充 uuidMap
        List<Map<String, Object>> objects = new ArrayList<>();
        Map<String, Object> unitRow = new HashMap<>();
        unitRow.put("offlineUuid", unitUuid);
        unitRow.put("parentUuid", "0");
        unitRow.put("nodeType", "UNIT");
        unitRow.put("bridgeType", "B03");
        objects.add(unitRow);
        Map<String, Object> partRow = new HashMap<>();
        partRow.put("offlineUuid", partUuid);
        partRow.put("parentUuid", unitUuid);
        partRow.put("nodeType", "PART");
        partRow.put("name", "主梁");
        objects.add(partRow);
        payload.put("objects", objects);

        // components：构件先落库，componentInput 才能反查
        List<Map<String, Object>> components = new ArrayList<>();
        Map<String, Object> compRow = new HashMap<>();
        compRow.put("offlineUuid", compUuid);
        compRow.put("objectUuid", unitUuid);
        compRow.put("name", "1号主梁构件");
        compRow.put("status", "0");
        components.add(compRow);
        payload.put("components", components);

        // bi_v2_component_input
        List<Map<String, Object>> componentInputs = new ArrayList<>();
        Map<String, Object> ciRow = new HashMap<>();
        ciRow.put("offlineUuid", ciUuid);
        ciRow.put("taskId", taskId);
        ciRow.put("componentUuid", compUuid);
        ciRow.put("eddi", 1);
        ciRow.put("efi", 0);
        ciRow.put("eai", -1);
        ciRow.put("safetyAffected", 0);
        componentInputs.add(ciRow);
        payload.put("componentInputs", componentInputs);

        // bi_v2_absent_mark：spanUuid 缺省 = 全桥所有跨
        List<Map<String, Object>> absentMarks = new ArrayList<>();
        Map<String, Object> amRow = new HashMap<>();
        amRow.put("offlineUuid", amUuid);
        amRow.put("taskId", taskId);
        amRow.put("partUuid", partUuid);
        absentMarks.add(amRow);
        payload.put("absentMarks", absentMarks);

        // bi_v2_single_control_mark
        List<Map<String, Object>> singleControlMarks = new ArrayList<>();
        Map<String, Object> scRow = new HashMap<>();
        scRow.put("offlineUuid", scUuid);
        scRow.put("taskId", taskId);
        scRow.put("indicatorNo", 1);
        scRow.put("hit", 1);
        scRow.put("evidence", "现场发现落梁迹象");
        singleControlMarks.add(scRow);
        payload.put("singleControlMarks", singleControlMarks);

        SyncResultVo result = syncUploadService.syncUpload(payload);

        assertNotNull(result);
        assertTrue(result.getErrors().isEmpty(), "同步错误: " + result.getErrors());

        V2ComponentInput savedInput = v2ComponentInputMapper.selectByOfflineUuid(ciUuid);
        assertNotNull(savedInput);
        assertEquals(1, savedInput.getEddi());
        assertEquals(-1, savedInput.getEai());

        V2AbsentMark savedAbsent = v2AbsentMarkMapper.selectByOfflineUuid(amUuid);
        assertNotNull(savedAbsent);
        assertNull(savedAbsent.getSpanId());
        assertEquals(partServerId, savedAbsent.getPartId());

        V2SingleControlMark savedControl = v2SingleControlMarkMapper.selectByOfflineUuid(scUuid);
        assertNotNull(savedControl);
        assertEquals(1, savedControl.getHit());
        assertEquals(1, savedControl.getIndicatorNo());
    }

    @Test
    @DisplayName("E2E-V2-002: 反查失败的行进入 errors，不影响批次内其他实体")
    void testSyncV2Inputs_BrokenReferencesReported() {
        Map<String, Object> payload = basePayload();

        // 构件输入指向未同步构件
        List<Map<String, Object>> componentInputs = new ArrayList<>();
        Map<String, Object> badCi = new HashMap<>();
        badCi.put("offlineUuid", "e2e-bad-ci-" + ciUuid);
        badCi.put("taskId", taskId);
        badCi.put("componentUuid", "e2e-missing-comp-" + compUuid);
        componentInputs.add(badCi);
        payload.put("componentInputs", componentInputs);

        // 应设未设：part 有效但 spanUuid 解析失败
        List<Map<String, Object>> objects = new ArrayList<>();
        Map<String, Object> partRow = new HashMap<>();
        partRow.put("offlineUuid", partUuid);
        partRow.put("parentUuid", unitUuid);
        partRow.put("nodeType", "PART");
        partRow.put("name", "主梁");
        objects.add(partRow);
        Map<String, Object> unitRow = new HashMap<>();
        unitRow.put("offlineUuid", unitUuid);
        unitRow.put("parentUuid", "0");
        unitRow.put("nodeType", "UNIT");
        unitRow.put("bridgeType", "B03");
        objects.add(unitRow);
        payload.put("objects", objects);

        List<Map<String, Object>> absentMarks = new ArrayList<>();
        Map<String, Object> badAm = new HashMap<>();
        badAm.put("offlineUuid", "e2e-bad-am-" + amUuid);
        badAm.put("taskId", taskId);
        badAm.put("partUuid", partUuid);
        badAm.put("spanUuid", "e2e-missing-span");
        absentMarks.add(badAm);
        payload.put("absentMarks", absentMarks);

        // 单项控制：componentUuid 解析失败
        List<Map<String, Object>> singleControlMarks = new ArrayList<>();
        Map<String, Object> badSc = new HashMap<>();
        badSc.put("offlineUuid", "e2e-bad-sc-" + scUuid);
        badSc.put("taskId", taskId);
        badSc.put("indicatorNo", 2);
        badSc.put("hit", 1);
        badSc.put("componentUuid", "e2e-missing-comp-2");
        singleControlMarks.add(badSc);
        payload.put("singleControlMarks", singleControlMarks);

        SyncResultVo result = syncUploadService.syncUpload(payload);

        assertTrue(result.getFailureCount() >= 3, "应至少报3条反查失败: " + result.getErrors());
        assertNull(v2ComponentInputMapper.selectByOfflineUuid("e2e-bad-ci-" + ciUuid));
        assertNull(v2AbsentMarkMapper.selectByOfflineUuid("e2e-bad-am-" + amUuid));
        assertNull(v2SingleControlMarkMapper.selectByOfflineUuid("e2e-bad-sc-" + scUuid));
    }

    private Map<String, Object> basePayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("syncUuid", UUID.randomUUID().toString());
        payload.put("clientInfo", "V2InputSyncE2ETest/1.0");
        payload.put("buildings", new ArrayList<>());
        payload.put("diseases", new ArrayList<>());
        payload.put("diseaseDetails", new ArrayList<>());
        payload.put("attachments", new ArrayList<>());
        return payload;
    }
}
