#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 5230 seed 第 13 节：桥型模板 PART 节点 -> 叶子部件多对多绑定。

规则（零 YAML 依赖）：5230 模板 PART 的 node_code 形如 'B01.part.paving'，
末段即 bi_leaf_object.code；一个 PART 可绑多个叶子（当前 1:1，扩展时改 RULES）。
输出幂等 INSERT...SELECT...WHERE NOT EXISTS，拼接到 20261014_std5230_seed.sql 末尾
（结束标记之前）。用法：python3 -I gen_template_leaf_bindings.py
"""

STD = "5230-2026"

HEADER = """-- ---------- 13. 桥型模板 PART 节点-叶子部件多对多绑定（82 行；手工维护节，
--     生成器 sql/tools/gen_template_leaf_bindings.py，零 YAML 依赖） ----------
-- 规则：5230 模板 PART 的 node_code 形如 'B01.part.paving'，末段即叶子部件 code；
-- 一个 PART 可绑定多个叶子（当前每 PART 一个，后续多叶子直接加行即可）。
-- 随 common.db 的 bi_template_object_leaf_object 表下发，App 据此取部件全部叶子的
-- 病害类型/位置/材料/加固绑定。自然键 (template_object_id, leaf_object_id) 幂等。
INSERT INTO `bi_template_object_leaf_object` (`template_object_id`,`leaf_object_id`,`create_time`)
SELECT t.`id`, l.`id`, NOW()
FROM `bi_template_object` t
JOIN `bi_leaf_object` l
  ON l.`code` = SUBSTRING_INDEX(t.`node_code`, '.', -1)
 AND l.`std_version` = '5230-2026'
WHERE t.`std_version` = '5230-2026'
  AND t.`node_code` LIKE '%.part.%'
  AND NOT EXISTS (
    SELECT 1 FROM `bi_template_object_leaf_object` b
    WHERE b.`template_object_id` = t.`id` AND b.`leaf_object_id` = l.`id`);
"""

if __name__ == "__main__":
    print(HEADER.rstrip("\n"))
