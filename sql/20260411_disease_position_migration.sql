-- =============================================
-- 数据迁移脚本：bi_template_object 第五级节点 → bi_disease_position
--
-- 说明：
--   RuoYi TreeEntity 的 ancestors 格式为 "0,rootId,L2id,L3id,L4id"
--   第五级节点判定条件：
--     LENGTH(ancestors) - LENGTH(REPLACE(ancestors, ',', '')) = 4
--   即 ancestors 中有 4 个逗号，代表该节点处于第 5 层
--
--   ref1、ref2 存储在 bi_template_object.props JSON 字段中，
--   迁移时通过 JSON_EXTRACT 拆分到独立字段。
--
-- 执行前提：先执行 20260411_disease_position_ddl.sql 建表
-- 回滚方式：见脚本末尾的回滚语句
--
-- @author QiXin
-- @date 2026-04-11
-- =============================================

-- -----------------------------------------------
-- 第一步：将第五级节点去重后插入 bi_disease_position
-- -----------------------------------------------
-- 去重策略：按 name 去重（同名位置视为同一个病害位置）
-- ref1、ref2 从 props JSON 中提取

INSERT INTO bi_disease_position (name, code, props, ref1, ref2, sort_order, status, del_flag, remark, create_by, create_time, update_by, update_time)
SELECT
    sub.name,
    NULL                                                       AS code,
    sub.props,
    CASE WHEN sub.props IS NOT NULL AND sub.props LIKE '%ref1:=%'
         THEN SUBSTRING_INDEX(SUBSTRING_INDEX(sub.props, 'ref1:=', -1), '&&', 1)
         ELSE NULL END                                         AS ref1,
    CASE WHEN sub.props IS NOT NULL AND sub.props LIKE '%ref2:=%'
         THEN SUBSTRING_INDEX(SUBSTRING_INDEX(sub.props, 'ref2:=', -1), '&&', 1)
         ELSE NULL END                                         AS ref2,
    sub.order_num                                              AS sort_order,
    IFNULL(sub.status, '0')                                    AS status,
    '0'                                                        AS del_flag,
    sub.remark,
    sub.create_by,
    sub.create_time,
    sub.update_by,
    sub.update_time
FROM (
    -- 按 name 分组，取每组中 id 最小的那一条作为代表
    SELECT t.*
    FROM bi_template_object t
    INNER JOIN (
        SELECT
            name,
            MIN(id) AS min_id
        FROM bi_template_object
        WHERE del_flag = '0'
          AND (LENGTH(ancestors) - LENGTH(REPLACE(ancestors, ',', ''))) = 4
        GROUP BY name
    ) g ON t.id = g.min_id
) sub;


-- -----------------------------------------------
-- 第二步：建立 bi_template_object_disease_position 关联
-- -----------------------------------------------
-- 对于每个第五级节点：
--   template_object_id = 该节点的 parent_id（即第四级模板构件）
--   disease_position_id = bi_disease_position 中同名的记录 id

INSERT INTO bi_template_object_disease_position (template_object_id, disease_position_id)
SELECT DISTINCT
    t5.parent_id           AS template_object_id,
    dp.id                  AS disease_position_id
FROM bi_template_object t5
INNER JOIN bi_disease_position dp
    ON dp.name = t5.name
WHERE t5.del_flag = '0'
  AND (LENGTH(t5.ancestors) - LENGTH(REPLACE(t5.ancestors, ',', ''))) = 4;


-- -----------------------------------------------
-- 第三步（可选）：标记已迁移的第五级节点为逻辑删除
-- -----------------------------------------------
-- 迁移完成并验证无误后，可取消注释执行以下语句清理原始数据

-- UPDATE bi_template_object
-- SET del_flag = '2', remark = CONCAT(IFNULL(remark,''), ' [已迁移至bi_disease_position]')
-- WHERE del_flag = '0'
--   AND (LENGTH(ancestors) - LENGTH(REPLACE(ancestors, ',', ''))) = 4;


-- ===============================================
-- 验证查询
-- ===============================================

-- 查看迁移后的病害位置字典（含 ref1、ref2 提取结果）
-- SELECT id, name, props, ref1, ref2, sort_order FROM bi_disease_position ORDER BY sort_order;

-- 查看关联关系（显示构件名称 + 位置名称）
-- SELECT
--     t4.id AS template_object_id,
--     t4.name AS component_name,
--     dp.id AS disease_position_id,
--     dp.name AS position_name,
--     dp.ref1,
--     dp.ref2
-- FROM bi_template_object_disease_position rel
-- JOIN bi_template_object t4 ON t4.id = rel.template_object_id
-- JOIN bi_disease_position dp ON dp.id = rel.disease_position_id
-- ORDER BY t4.id, dp.sort_order;

-- 统计：原始第五级节点数 vs 去重后病害位置数 vs 关联映射数
-- SELECT '原始第五级节点数' AS metric, COUNT(*) AS cnt
-- FROM bi_template_object
-- WHERE del_flag = '0'
--   AND (LENGTH(ancestors) - LENGTH(REPLACE(ancestors, ',', ''))) = 4
-- UNION ALL
-- SELECT '去重后病害位置数', COUNT(*) FROM bi_disease_position
-- UNION ALL
-- SELECT '关联映射数', COUNT(*) FROM bi_template_object_disease_position;


-- ===============================================
-- 回滚脚本（如需撤销迁移）
-- ===============================================
-- DELETE FROM bi_template_object_disease_position;
-- DELETE FROM bi_disease_position;
