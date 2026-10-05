-- ================================================================================
-- 新标准 21 类桥型模板种子数据（JTG/T 5230—2026 附录 B 表 B.1.1）
-- 脚本名：20261004_p0_template_5230_seed.sql
-- 目标库：bi (MySQL 8.0.42, 60.205.13.156:3306)
-- 依据：doc/20260930/报批稿与正式稿差异分析_JTGT5230-2026.html 差异 8
--       + 规范正式稿表 B.1.1「公路桥梁评定桥型」原文（p.92 / PDF 98）
--
-- 设计决策（2026-10-04 用户拍板）：
--   1. 高位 id 段：900001~900021，与历史 18 个模板（id 1~17/15970/15987）物理隔离；
--   2. props 显式标记：{"stdVersion":"5230-2026","bridgeType":"B01"}，
--      后端 isNewStandardTemplate() 据此分叉，parseBridgeType() 据此回填 bi_object.bridge_type；
--   3. 只建顶层模板节点，不预置子树 —— 新标准建桥时后端只生成一条 UNIT 根节点
--      （generateSpanRootNode），完整 SPAN/LAYER/PART 建桥在 App 端按附录 B 动态生成；
--   4. id 与 bridgeType 一一对应：900001↔B01 ... 900021↔B21（按表 B.1.1 行序）。
--
-- 幂等性：INSERT ... SELECT ... WHERE NOT EXISTS，可重复执行。
-- ================================================================================

-- 21 类桥型：梁桥 7 + 拱桥 7 + 组合体系拱桥 3 + 悬索桥 2 + 斜拉桥 2
-- 名称以规范正式稿表 B.1.1 原文为准

INSERT INTO bi_template_object (id, name, parent_id, ancestors, order_num, status, del_flag, remark, create_by, create_time, weight, props)
SELECT t.id, t.name, 0, '0', t.ord, '0', '0', t.rmk, 'admin', NOW(), NULL, t.props_json
FROM (
  -- ── 梁桥（7 类）──
  SELECT 900001 AS id, '整体式实心/空心板' AS name, 1 AS ord, 'JTG/T 5230-2026 附录B 表B.1.1' AS rmk, '{"stdVersion":"5230-2026","bridgeType":"B01"}' AS props_json UNION ALL
  SELECT 900002, '装配式实心/空心板', 2, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B02"}' UNION ALL
  SELECT 900003, '装配式组合箱梁', 3, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B03"}' UNION ALL
  SELECT 900004, '装配式肋梁', 4, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B04"}' UNION ALL
  SELECT 900005, '整体现浇箱梁', 5, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B05"}' UNION ALL
  SELECT 900006, '悬臂浇筑箱梁', 6, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B06"}' UNION ALL
  SELECT 900007, '钢及钢-混凝土组合梁', 7, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B07"}' UNION ALL
  -- ── 拱桥（7 类）──
  SELECT 900008, '实腹式板拱', 8, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B08"}' UNION ALL
  SELECT 900009, '空腹式板拱', 9, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B09"}' UNION ALL
  SELECT 900010, '肋拱', 10, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B10"}' UNION ALL
  SELECT 900011, '箱形拱', 11, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B11"}' UNION ALL
  SELECT 900012, '双曲拱', 12, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B12"}' UNION ALL
  SELECT 900013, '桁架拱', 13, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B13"}' UNION ALL
  SELECT 900014, '刚架拱', 14, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B14"}' UNION ALL
  -- ── 组合体系拱桥（3 类）──
  SELECT 900015, '无系杆组合体系拱', 15, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B15"}' UNION ALL
  SELECT 900016, '柔性系杆组合体系拱', 16, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B16"}' UNION ALL
  SELECT 900017, '刚性系杆组合体系拱', 17, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B17"}' UNION ALL
  -- ── 悬索桥（2 类）──
  SELECT 900018, '自锚式悬索桥', 18, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B18"}' UNION ALL
  SELECT 900019, '地锚式悬索桥', 19, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B19"}' UNION ALL
  -- ── 斜拉桥（2 类）──
  SELECT 900020, '斜拉桥', 20, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B20"}' UNION ALL
  SELECT 900021, '部分斜拉桥', 21, 'JTG/T 5230-2026 附录B 表B.1.1', '{"stdVersion":"5230-2026","bridgeType":"B21"}'
) t
WHERE NOT EXISTS (
  SELECT 1 FROM bi_template_object b
  WHERE b.id = t.id
     OR (b.parent_id = 0 AND b.props LIKE CONCAT('%"bridgeType":"', JSON_UNQUOTE(JSON_EXTRACT(t.props_json, '$.bridgeType')), '"%'))
);

-- ================================================================================
-- 校验：应返回 21 行
-- ================================================================================
-- SELECT id, name,
--        JSON_UNQUOTE(JSON_EXTRACT(props, '$.bridgeType')) AS bridge_type,
--        JSON_UNQUOTE(JSON_EXTRACT(props, '$.stdVersion')) AS std_version
-- FROM bi_template_object
-- WHERE id BETWEEN 900001 AND 900021
-- ORDER BY id;

-- 校验2：确认 id 段无冲突（应返回 0 行）
-- SELECT id FROM bi_template_object WHERE id BETWEEN 900001 AND 900021 AND create_time < '2026-10-04';
