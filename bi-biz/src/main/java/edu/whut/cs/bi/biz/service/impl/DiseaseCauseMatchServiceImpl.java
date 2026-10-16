package edu.whut.cs.bi.biz.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.whut.cs.bi.biz.domain.BiObject;
import edu.whut.cs.bi.biz.domain.BiTemplateObject;
import edu.whut.cs.bi.biz.domain.Disease;
import edu.whut.cs.bi.biz.domain.dto.CauseMatchQuery;
import edu.whut.cs.bi.biz.domain.vo.DiseaseCauseMatch;
import edu.whut.cs.bi.biz.mapper.BiObjectMapper;
import edu.whut.cs.bi.biz.mapper.DiseaseMapper;
import edu.whut.cs.bi.biz.service.IBiTemplateObjectService;
import edu.whut.cs.bi.biz.service.IDiseaseCauseMatchService;
import edu.whut.cs.bi.biz.utils.DiseaseCauseKnowledgeBase;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class DiseaseCauseMatchServiceImpl implements IDiseaseCauseMatchService {
    private static final String OTHER = "其他";
    private static final ExecutorService MATCH_EXECUTOR = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "disease-cause-match");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${springAi_Rag.endpoint}")
    private String springAiRagEndpoint;

    @Autowired
    private DiseaseMapper diseaseMapper;

    @Autowired
    private BiObjectMapper biObjectMapper;

    @Autowired
    private IBiTemplateObjectService biTemplateObjectService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = createRestTemplate();

    @Override
    public DiseaseCauseMatch match(CauseMatchQuery query) {
        Disease disease;
        if (query.getDiseaseId() != null) {
            disease = diseaseMapper.selectDiseaseById(query.getDiseaseId());
            if (disease == null) {
                return DiseaseCauseMatch.of(DiseaseCauseMatch.FAILED, "未找到病害，id=" + query.getDiseaseId(), 0);
            }
        } else {
            disease = new Disease();
        }
        if (query.getBiObjectId() != null) disease.setBiObjectId(query.getBiObjectId());
        if (query.getBiObjectName() != null) disease.setBiObjectName(query.getBiObjectName());
        if (query.getType() != null) disease.setType(query.getType());
        if (query.getCrackType() != null) disease.setCrackType(query.getCrackType());
        if (query.getPosition() != null) disease.setPosition(query.getPosition());
        if (query.getDescription() != null) disease.setDescription(query.getDescription());
        Integer level = query.getLevel() != null ? query.getLevel()
                : query.getDiseaseId() != null ? Integer.valueOf(disease.getLevel()) : null;
        return match(disease, level);
    }

    @Override
    public DiseaseCauseMatch match(Disease disease, Integer level) {
        BiObject object = disease.getBiObjectId() == null ? null : biObjectMapper.selectBiObjectById(disease.getBiObjectId());
        if (object == null) {
            return DiseaseCauseMatch.of(DiseaseCauseMatch.FAILED, "未找到病害所属构件，biObjectId=" + disease.getBiObjectId(), 0);
        }
        String type = diseaseTypeName(disease);
        if (type.isEmpty()) {
            return DiseaseCauseMatch.of(DiseaseCauseMatch.FAILED, "病害类型为空", 0);
        }

        List<Long> pathIds = new ArrayList<>();
        if (object.getAncestors() != null) {
            Arrays.stream(object.getAncestors().split(",")).map(String::trim)
                    .filter(id -> !id.isEmpty() && !"0".equals(id)).map(Long::valueOf).forEach(pathIds::add);
        }
        pathIds.add(object.getId());
        Map<Long, BiObject> objects = biObjectMapper.selectBiObjectsByIds(pathIds).stream()
                .collect(Collectors.toMap(BiObject::getId, Function.identity(), (left, right) -> left));
        // 组合桥（左右幅）的顶层节点无模板，桥梁根节点是路径上第一个带模板的节点。
        int rootIndex = -1;
        for (int i = 0; i < pathIds.size() && rootIndex < 0; i++) {
            BiObject node = objects.get(pathIds.get(i));
            if (node != null && node.getTemplateObjectId() != null) {
                rootIndex = i;
            }
        }
        if (rootIndex < 0 || rootIndex + 1 >= pathIds.size()) {
            return DiseaseCauseMatch.of(DiseaseCauseMatch.FAILED, "构件层级不完整，biObjectId=" + object.getId(), 0);
        }
        BiTemplateObject template = biTemplateObjectService.selectBiTemplateObjectById(
                objects.get(pathIds.get(rootIndex)).getTemplateObjectId());
        if (template == null) {
            return DiseaseCauseMatch.of(DiseaseCauseMatch.FAILED, "未找到桥梁模板，biObjectId=" + object.getId(), 0);
        }
        DiseaseCauseKnowledgeBase knowledgeBase = DiseaseCauseKnowledgeBase.getDefault();
        if (!knowledgeBase.supportsTemplate(template.getName())) {
            return DiseaseCauseMatch.of(DiseaseCauseMatch.UNSUPPORTED, "暂不支持该桥梁模板：" + template.getName(), 0);
        }

        List<String> pathNames = pathIds.subList(rootIndex + 1, pathIds.size()).stream()
                .map(id -> objects.containsKey(id) ? objects.get(id).getName() : "")
                .collect(Collectors.toList());
        String structure = pathNames.get(0);
        String part = pathNames.size() > 1 ? usableName(pathNames.get(1)) : "";
        Set<String> objectNames = new LinkedHashSet<>();
        for (int i = 2; i < pathNames.size(); i++) {
            addUsableName(objectNames, pathNames.get(i));
        }
        addUsableName(objectNames, disease.getBiObjectName());

        List<DiseaseCauseKnowledgeBase.Entry> candidates = knowledgeBase.candidates(new DiseaseCauseKnowledgeBase.Query(
                template.getName(), structure, part, new ArrayList<>(objectNames), type, disease.getCrackType()));
        if (candidates.isEmpty()) {
            return DiseaseCauseMatch.of(DiseaseCauseMatch.NO_MATCH, "知识库中没有与该部位和病害类型相近的条目", 0);
        }

        JsonNode response;
        try {
            response = requestMatch(template.getName(), String.join("/", pathNames), disease, type, level, candidates);
        } catch (Exception e) {
            log.warn("病害成因匹配请求失败 biObjectId={} type={}: {}", object.getId(), type, e.getMessage());
            return DiseaseCauseMatch.of(DiseaseCauseMatch.FAILED, "大模型匹配失败：" + e.getMessage(), candidates.size());
        }
        String reason = response.path("reason").asText("");
        JsonNode matchedId = response.path("matchedId");
        if (!matchedId.isTextual() || matchedId.asText().isBlank()) {
            return DiseaseCauseMatch.of(DiseaseCauseMatch.NO_MATCH, reason, candidates.size());
        }
        DiseaseCauseKnowledgeBase.Entry entry = candidates.stream()
                .filter(candidate -> candidate.id().equals(matchedId.asText())).findFirst().orElse(null);
        if (entry == null) {
            return DiseaseCauseMatch.of(DiseaseCauseMatch.FAILED, "大模型返回了候选之外的条目：" + matchedId.asText(), candidates.size());
        }

        DiseaseCauseMatch result = DiseaseCauseMatch.of(DiseaseCauseMatch.MATCHED, reason, candidates.size());
        result.setEntryId(entry.id());
        result.setSheet(entry.sheet());
        result.setCategory(entry.category());
        result.setComponent(entry.component());
        result.setDefectType(entry.defectType());
        result.setDefectName(entry.defectName());
        result.setCondition(entry.condition());
        result.setLevel(level);
        result.setCause(blankToNull(entry.cause()));
        result.setFullSuggestion(blankToNull(entry.suggestion()));
        result.setSuggestion(blankToNull(DiseaseCauseKnowledgeBase.suggestionForLevel(entry.suggestion(), level)));
        return result;
    }

    @Override
    public List<DiseaseCauseMatch> matchAll(List<Disease> diseases, List<Integer> levels) {
        List<CompletableFuture<DiseaseCauseMatch>> futures = new ArrayList<>();
        for (int i = 0; i < diseases.size(); i++) {
            Disease disease = diseases.get(i);
            Integer level = levels.get(i);
            futures.add(CompletableFuture.supplyAsync(() -> match(disease, level), MATCH_EXECUTOR)
                    .exceptionally(e -> {
                        log.warn("病害成因匹配异常 diseaseId={}", disease.getId(), e);
                        return DiseaseCauseMatch.of(DiseaseCauseMatch.FAILED, "匹配异常：" + e.getMessage(), 0);
                    }));
        }
        return futures.stream().map(CompletableFuture::join).collect(Collectors.toList());
    }

    private JsonNode requestMatch(String templateName, String path, Disease disease, String type, Integer level,
                                  List<DiseaseCauseKnowledgeBase.Entry> candidates) throws Exception {
        ObjectNode body = objectMapper.createObjectNode();
        ObjectNode diseaseNode = body.putObject("disease");
        diseaseNode.put("template", templateName);
        diseaseNode.put("path", path);
        diseaseNode.put("biObjectName", nullToEmpty(disease.getBiObjectName()));
        diseaseNode.put("position", nullToEmpty(disease.getPosition()));
        diseaseNode.put("type", type);
        if (type.contains("裂缝")) {
            diseaseNode.put("crackType", nullToEmpty(disease.getCrackType()));
        }
        if (level != null) {
            diseaseNode.put("level", level);
        }
        diseaseNode.put("description", nullToEmpty(disease.getDescription()));
        ArrayNode candidateNodes = body.putArray("candidates");
        for (DiseaseCauseKnowledgeBase.Entry entry : candidates) {
            ObjectNode node = candidateNodes.addObject();
            node.put("id", entry.id());
            node.put("sheet", entry.sheet());
            node.put("category", entry.category());
            node.put("component", entry.component());
            node.put("defectType", entry.defectType());
            node.put("defectName", entry.defectName());
            node.put("condition", entry.condition());
            node.put("cause", entry.cause());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String response = restTemplate.postForObject(springAiRagEndpoint + "/api-ai/disease/cause-match",
                new HttpEntity<>(objectMapper.writeValueAsString(body), headers), String.class);
        if (response == null || response.isBlank()) {
            throw new IllegalStateException("成因匹配服务返回为空");
        }
        return objectMapper.readTree(response);
    }

    private static String diseaseTypeName(Disease disease) {
        String type = disease.getType();
        if ((type == null || type.isBlank()) && disease.getDiseaseType() != null) {
            type = disease.getDiseaseType().getName();
        }
        return type == null ? "" : type.substring(type.lastIndexOf('#') + 1).trim();
    }

    private static void addUsableName(Set<String> names, String name) {
        String usable = usableName(name);
        if (!usable.isEmpty()) {
            names.add(usable);
        }
    }

    private static String usableName(String name) {
        String trimmed = name == null ? "" : name.trim();
        return OTHER.equals(trimmed) ? "" : trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(180_000);
        return new RestTemplate(factory);
    }
}
