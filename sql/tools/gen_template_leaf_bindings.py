#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 5230 seed 第 13 节：桥型模板 PART 节点 -> 叶子部件多对多绑定。

YAML 驱动：读取 templates/shared-layers.yml 与各 Bxx.yml 的部件定义，
每个 PART 绑定其 part.leaves（缺省退化为 [part.key] 单叶子）。
当前多叶子 PART（正式稿依据）：
  B07 steelMainGirder -> [steelMainGirder, mainGirder]（表 6.2.15 连接部 / 表 6.2.16 牛腿）
  B07 deckSlab        -> [deckSlab, slab]                 （同上）
输出显式 (node_code, leaf_code) 配对的幂等
INSERT...SELECT...WHERE NOT EXISTS，替换到 20261014_std5230_seed.sql 第 13 节
（结束标记之前）。用法：python3 -I gen_template_leaf_bindings.py
"""
import os
import sys

import yaml

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from gen_std5230_seed import LEAF_ORDER, LAYER_ORDER, repo_root  # noqa: E402

STD = "5230-2026"
SHARED_LAYERS = ("deck", "substructure", "ancillary")


def part_leaf_codes(part):
    """part.leaves 显式优先，缺省 1:1 绑定同名叶子。"""
    leaves = part.get("leaves") or [part["key"]]
    return [str(x) for x in leaves]


def collect_pairs():
    root = os.path.join(repo_root(), "bi-biz", "src", "main", "resources",
                        "std", STD)
    with open(os.path.join(root, "meta.yml"), encoding="utf-8") as f:
        bridge_types = [b["code"] for b in yaml.safe_load(f)["bridgeTypes"]
                        if b.get("structureComplete")]
    with open(os.path.join(root, "templates", "shared-layers.yml"),
              encoding="utf-8") as f:
        shared = {layer["key"]: layer["parts"]
                  for layer in yaml.safe_load(f)["layers"]}

    pairs = set()
    multi = {}
    for b_code in bridge_types:
        path = os.path.join(root, "templates", "%s.yml" % b_code)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            tpl = yaml.safe_load(f)
        parts_by_layer = {k: shared[k] for k in SHARED_LAYERS}
        parts_by_layer["superstructure"] = tpl.get("superstructureParts", [])
        for layer_key in LAYER_ORDER:
            for part in parts_by_layer[layer_key]:
                node_code = "%s.part.%s" % (b_code, part["key"])
                leaf_codes = part_leaf_codes(part)
                if len(leaf_codes) > 1:
                    multi[node_code] = leaf_codes
                for leaf in leaf_codes:
                    pairs.add((node_code, leaf))

    valid_leaves = {code for code, _, _ in LEAF_ORDER}
    unknown = {leaf for _, leaf in pairs if leaf not in valid_leaves}
    if unknown:
        raise SystemExit("叶子 code 不在 LEAF_ORDER 中: %s" % sorted(unknown))
    return sorted(pairs), multi


def build_sql(pairs, multi):
    lines = []
    w = lines.append
    w("-- ---------- 13. 桥型模板 PART 节点-叶子部件多对多绑定（%d 行；手工维护节，"
      % len(pairs))
    w("--     生成器 sql/tools/gen_template_leaf_bindings.py，YAML 驱动） ----------")
    w("-- 规则：每个 PART 绑定 Bxx.yml 中 part.leaves（缺省 1:1 绑同名叶子 code）；")
    w("-- 一个 PART 可绑多个叶子，App 在该 PART 下录入病害时按 bi_disease.leaf_object_id")
    w("-- 显式区分归属叶子。当前多叶子 PART（正式稿表 6.2.15 组合梁连接部 STL-CONN-*、")
    w("-- 表 6.2.16 牛腿 STL-COR-* 的 parts 跨叶子）：")
    for node_code, leaves in sorted(multi.items()):
        w("--   %s -> %s" % (node_code, ", ".join(leaves)))
    w("-- 随 common.db 的 bi_template_object_leaf_object 表下发。")
    w("-- 自然键 (template_object_id, leaf_object_id) 幂等，重跑只补缺不删除。")
    w("INSERT INTO `bi_template_object_leaf_object` "
      "(`template_object_id`,`leaf_object_id`,`create_time`)")
    w("SELECT t.`id`, l.`id`, NOW()")
    w("FROM (")
    for idx, (node_code, leaf) in enumerate(pairs):
        suffix = " UNION ALL" if idx < len(pairs) - 1 else ""
        w("  SELECT '%s' AS node_code, '%s' AS leaf_code%s"
          % (node_code, leaf, suffix))
    w(") m")
    w("JOIN `bi_template_object` t")
    w("  ON t.`node_code` = m.node_code AND t.`std_version` = '%s'" % STD)
    w("JOIN `bi_leaf_object` l")
    w("  ON l.`code` = m.leaf_code AND l.`std_version` = '%s'" % STD)
    w("WHERE NOT EXISTS (")
    w("  SELECT 1 FROM `bi_template_object_leaf_object` b")
    w("  WHERE b.`template_object_id` = t.`id`")
    w("    AND b.`leaf_object_id` = l.`id`);")
    return "\n".join(lines)


if __name__ == "__main__":
    pairs, multi = collect_pairs()
    print(build_sql(pairs, multi))
    print("-- 校验：配对 %d 行，多叶子 PART %d 个" % (len(pairs), len(multi)),
          file=sys.stderr)
