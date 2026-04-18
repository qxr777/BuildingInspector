import re

with open("bi-biz/src/main/java/edu/whut/cs/bi/biz/service/impl/SyncUploadServiceImpl.java", "r") as f:
    text = f.read()

# 替换 processBuildings
text = re.sub(
    r'(Building existing = buildingMapper\.selectByOfflineUuid\(item\.getOfflineUuid\(\)\);.*?)(if \(existing != null\) \{.*?continue;\n\s*\})',
    r'''\1if (existing != null) {
                    uuidMap.put(item.getOfflineUuid(), existing.getId());
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        buildingMapper.deleteBuildingById(item.getId());
                        continue;
                    }
                    buildingMapper.updateBuilding(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }''',
    text, flags=re.DOTALL | re.MULTILINE
)

# 替换 processBiObjects
text = re.sub(
    r'(BiObject existing = biObjectMapper\.selectByOfflineUuid\(item\.getOfflineUuid\(\)\);)\s*(if \(existing != null\) \{\s*uuidMap\.put\(item\.getOfflineUuid\(\), existing\.getId\(\)\);\s*processedUuids\.add\(item\.getOfflineUuid\(\)\);\s*roundCount\+\+;\s*continue;\s*\})\s*(item\.setParentId\(parentId\);\s*if \(item\.getBuildingUuid\(\) != null\)\s*item\.setBuildingId\(uuidMap\.get\(item\.getBuildingUuid\(\)\)\);)',
    r'''\3
                    \1
                    if (existing != null) {
                        uuidMap.put(item.getOfflineUuid(), existing.getId());
                        item.setId(existing.getId());
                        item.setUpdateBy(loginName);
                        item.setUpdateTime(DateUtils.getNowDate());
                        processedUuids.add(item.getOfflineUuid());
                        roundCount++;
                        if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                            biObjectMapper.deleteBiObjectById(item.getId());
                            continue;
                        }
                        biObjectMapper.updateBiObject(item);
                        result.setSuccessCount(result.getSuccessCount() + 1);
                        continue;
                    }
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        processedUuids.add(item.getOfflineUuid());
                        roundCount++;
                        continue;
                    }''', 
    text
)

# 替换 processComponents
text = re.sub(
    r'(Component existing = componentMapper\.selectByOfflineUuid\(item\.getOfflineUuid\(\)\);)\s*(if \(existing != null\) \{\s*uuidMap\.put\(item\.getOfflineUuid\(\), existing\.getId\(\)\);\s*continue;\s*\})\s*(if \(item\.getObjectUuid\(\) != null\)\s*item\.setBiObjectId\(uuidMap\.get\(item\.getObjectUuid\(\)\)\);)',
    r'''\3
                \1
                if (existing != null) {
                    uuidMap.put(item.getOfflineUuid(), existing.getId());
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        componentMapper.deleteComponentById(item.getId());
                        continue;
                    }
                    componentMapper.updateComponent(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }''',
    text
)

# 替换 processDiseases
text = re.sub(
    r'(Disease existing = diseaseMapper\.selectByOfflineUuid\(item\.getOfflineUuid\(\)\);)\s*(if \(existing != null\) \{\s*uuidMap\.put\(item\.getOfflineUuid\(\), existing\.getId\(\)\);\s*continue;\s*\})\s*(if \(item\.getBuildingUuid\(\) != null\)\s*item\.setBuildingId\(uuidMap\.get\(item\.getBuildingUuid\(\)\)\);\s*if \(item\.getObjectUuid\(\) != null\)\s*item\.setBiObjectId\(uuidMap\.get\(item\.getObjectUuid\(\)\)\);\s*if \(item\.getComponentUuid\(\) != null\)\s*item\.setComponentId\(uuidMap\.get\(item\.getComponentUuid\(\)\)\);)',
    r'''\3
                \1
                if (existing != null) {
                    uuidMap.put(item.getOfflineUuid(), existing.getId());
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        diseaseMapper.deleteDiseaseById(item.getId());
                        continue;
                    }
                    diseaseMapper.updateDisease(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }''',
    text
)

# 替换 processDiseaseDetails
text = re.sub(
    r'(if \(item\.getOfflineUuid\(\) != null\s*&&\s*diseaseDetailMapper\.selectByOfflineUuid\(item\.getOfflineUuid\(\)\) != null\)\s*continue;)\s*(if \(item\.getDiseaseUuid\(\) != null\)\s*item\.setDiseaseId\(uuidMap\.get\(item\.getDiseaseUuid\(\)\)\);)',
    r'''\2
                DiseaseDetail existing = item.getOfflineUuid() != null ? diseaseDetailMapper.selectByOfflineUuid(item.getOfflineUuid()) : null;
                if (existing != null) {
                    item.setId(existing.getId());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        diseaseDetailMapper.deleteDiseaseDetailById(item.getId());
                        continue;
                    }
                    diseaseDetailMapper.updateDiseaseDetail(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }''',
    text
)

# 替换 processAttachments
text = re.sub(
    r'(if \(item\.getOfflineSubjectUuid\(\) != null\)\s*item\.setSubjectId\(uuidMap\.get\(item\.getOfflineSubjectUuid\(\)\)\);)\s*(item\.setIsOfflineData\(1\);)',
    r'''\1
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    // For attachments, we skip or we can delete if it already exists, assuming offline_deleted implies we skip insertion if it's new.
                    // If we need true deletion for attachments we should check if they exist first. For now skip insertion.
                    continue;
                }
                \2''',
    text
)

# 替换 processBiObjectComponents
text = re.sub(
    r'(BiObjectComponent query = new BiObjectComponent\(\);\s*query\.setOfflineUuid\(item\.getOfflineUuid\(\)\);\s*if \(!biObjectComponentMapper\.selectBiObjectComponentList\(query\)\.isEmpty\(\)\)\s*continue;)\s*(if \(item\.getComponentUuid\(\) != null\)\s*item\.setComponentId\(uuidMap\.get\(item\.getComponentUuid\(\)\)\);\s*if \(item\.getObjectUuid\(\) != null\)\s*item\.setBiObjectId\(uuidMap\.get\(item\.getObjectUuid\(\)\)\);)',
    r'''\2
                List<BiObjectComponent> existings = biObjectComponentMapper.selectBiObjectComponentList(new BiObjectComponent() {{ setOfflineUuid(item.getOfflineUuid()); }});
                if (!existings.isEmpty()) {
                    BiObjectComponent existing = existings.get(0);
                    item.setId(existing.getId());
                    item.setUpdateBy(loginName);
                    item.setUpdateTime(DateUtils.getNowDate());
                    if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                        biObjectComponentMapper.deleteBiObjectComponentById(item.getId());
                        continue;
                    }
                    biObjectComponentMapper.updateBiObjectComponent(item);
                    result.setSuccessCount(result.getSuccessCount() + 1);
                    continue;
                }
                if (Integer.valueOf(1).equals(item.getOfflineDeleted())) {
                    continue;
                }''',
    text
)

with open("bi-biz/src/main/java/edu/whut/cs/bi/biz/service/impl/SyncUploadServiceImpl.java", "w") as f:
    f.write(text)

print("Done")
