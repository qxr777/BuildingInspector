#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 5230 种子第 11 节：由 bi_template_object 的 B01 模板树实例化一棵 bi_object 演示树。

形态依据 doc/20260930/方案A_桥跨节点层建模详细设计：
  UNIT(桥型绑定 B01)
    └─ SPAN(span_no=1..N, bridge_type 继承)
         ├─ LAYER 桥面系/上部结构/下部结构/附属设施（γ，每跨各一套，含附属设施）
         │    └─ PART（ω、leaf_object_id、is_main_component）

说明：
- 后端 BuildingServiceImpl.generateSpanRootNode() 新标准只自动生成 UNIT 一条，
  SPAN/LAYER/PART 由 App 端创建；本种子为演示环境把整棵子树一次性补齐（2 跨 × 33 节点）。
- 无项目/桥梁业务行（演示环境只放 5230 字典/参考数据），故 UNIT 为 parent_id=0 的孤树根，
  命名沿用后端规则「桥名(模板桥型名)」。
- 幂等：固定 offline_uuid 自然键（std5230demo-b01-*），WHERE NOT EXISTS，可重复执行。
- 输出手工拼接到 20261014_std5230_seed.sql 第 10 节之后（同第 8/9/10 节，不进主生成器）。

用法：python3 -I sql/tools/gen_demo_object_tree.py
"""
import json
import os
import sys

import yaml

STD_VERSION = "5230-2026"
B_CODE = "B01"
N_SPANS = 2
DEMO_BRIDGE_NAME = "5230演示桥"
UUID_PREFIX = "std5230demo-b01"
LAYER_ORDER = ["deck", "superstructure", "substructure", "ancillary"]


def q(s):
    if s is None:
        return "NULL"
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def compact_json(obj):
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


def main():
    repo = os.path.dirname(os.path.dirname(os.path.dirname(
        os.path.abspath(__file__))))
    root = os.path.join(repo, "bi-biz", "src", "main", "resources",
                        "std", STD_VERSION)

    with open(os.path.join(root, "meta.yml"), encoding="utf-8") as f:
        b_name = next(b["name"] for b in yaml.safe_load(f)["bridgeTypes"]
                      if b["code"] == B_CODE)
    with open(os.path.join(root, "templates", "shared-layers.yml"),
              encoding="utf-8") as f:
        shared = {l["key"]: (l["name"],
                             [(p["key"], p["name"], int(p["omega"]))
                              for p in l["parts"]])
                  for l in yaml.safe_load(f)["layers"]}
    with open(os.path.join(root, "templates", "%s.yml" % B_CODE),
              encoding="utf-8") as f:
        tpl = yaml.safe_load(f)
    with open(os.path.join(root, "rules", "main-parts.yml"),
              encoding="utf-8") as f:
        main_parts = (yaml.safe_load(f) or {}).get("mainParts") or {}
    mains = set(main_parts.get(B_CODE) or [])

    # 每层: (key, name, gamma, [(part_key, part_name, omega)])
    layers = []
    for layer_key in LAYER_ORDER:
        if layer_key == "superstructure":
            layer_name = tpl.get("superstructureName") or "上部结构"
            parts = [(p["key"], p["name"], int(p["omega"]))
                     for p in tpl.get("superstructureParts", [])]
        else:
            layer_name, parts = shared[layer_key]
        layers.append((layer_key, layer_name,
                       float(tpl["gamma"][layer_key]), parts))

    out = []
    w = out.append

    total = 1 + N_SPANS + N_SPANS * (
        len(layers) + sum(len(ps) for _, _, _, ps in layers))

    w("")
    w("-- ---------- 11. bi_object（B01 模板树实例化演示树：1 UNIT + %d SPAN "
      "+ 每跨 4 LAYER/11 PART，共 %d 节点；手工维护，生成器见 "
      "sql/tools/gen_demo_object_tree.py） ----------" % (N_SPANS, total))
    w("-- 形态：UNIT→SPAN(span_no)→LAYER(γ)→PART(ω,leaf_object_id,"
      "is_main_component)，附属设施同挂每跨；")
    w("-- 后端新标准仅自动生成 UNIT，跨下子树由 App 端建，本种子为演示环境补齐整棵子树。")
    w("-- 幂等：固定 offline_uuid（%s-*）自然键 WHERE NOT EXISTS。" % UUID_PREFIX)

    def tpl_id(node_code):
        return ("(SELECT t.`id` FROM `bi_template_object` t "
                "WHERE t.`node_code`=%s AND t.`std_version`=%s)"
                % (q(node_code), q(STD_VERSION)))

    def leaf_id(leaf_code):
        return ("(SELECT l.`id` FROM `bi_leaf_object` l "
                "WHERE l.`code`=%s AND l.`std_version`=%s)"
                % (q(leaf_code), q(STD_VERSION)))

    def obj_id(uuid):
        return ("(SELECT o.`id` FROM `bi_object` o "
                "WHERE o.`offline_uuid`=%s)" % q(uuid))

    def ancestors_of(uuid):
        return ("(SELECT CONCAT(o.`ancestors`,',',o.`id`) FROM `bi_object` o "
                "WHERE o.`offline_uuid`=%s)" % q(uuid))

    cols = ("(`name`,`parent_id`,`ancestors`,`order_num`,`template_object_id`,"
            "`count`,`props_json`,`node_type`,`span_no`,`bridge_type`,"
            "`gamma`,`omega`,`leaf_object_id`,`is_main_component`,"
            "`offline_uuid`,`parent_uuid`,`is_offline_data`,`offline_deleted`,"
            "`create_by`,`create_time`)")

    def insert(name, parent_uuid, order, tpl_node, props, node_type,
               span_no, bridge_type, gamma, omega, leaf_code, is_main,
               uuid):
        if parent_uuid is None:
            parent_sql, ancestors_sql, p_uuid = "0", q("0"), "NULL"
        else:
            parent_sql = obj_id(parent_uuid)
            ancestors_sql = ancestors_of(parent_uuid)
            p_uuid = q(parent_uuid)
        values = [
            q(name), parent_sql, ancestors_sql, str(order),
            tpl_id(tpl_node) if tpl_node else "NULL",
            "0", q(props) if props is not None else "NULL", q(node_type),
            str(span_no) if span_no is not None else "NULL",
            q(bridge_type),
            str(gamma) if gamma is not None else "NULL",
            str(omega) if omega is not None else "NULL",
            leaf_id(leaf_code) if leaf_code else "NULL",
            ("NULL" if is_main is None else ("1" if is_main else "0")),
            q(uuid), p_uuid, "0", "0",
            q("std5230-seed"), "NOW()",
        ]
        w("INSERT INTO `bi_object` %s SELECT %s "
          "WHERE NOT EXISTS (SELECT 1 FROM `bi_object` o "
          "WHERE o.`offline_uuid`=%s);"
          % (cols, ",".join(values), q(uuid)))

    # UNIT（对应 B01.root；props 与模板根一致）
    unit_uuid = "%s-unit" % UUID_PREFIX
    unit_props = {"stdVersion": STD_VERSION, "bridgeType": B_CODE,
                  "structureComplete": True}
    if tpl.get("rules"):
        unit_props["rules"] = [str(r) for r in tpl["rules"]]
    insert("%s(%s)" % (DEMO_BRIDGE_NAME, b_name), None, 0,
           "%s.root" % B_CODE, compact_json(unit_props), "UNIT",
           None, B_CODE, None, None, None, None, unit_uuid)

    for span_i in range(1, N_SPANS + 1):
        span_uuid = "%s-s%d" % (UUID_PREFIX, span_i)
        span_props = {"stdVersion": STD_VERSION, "bridgeType": B_CODE,
                      "spanNo": span_i}
        insert("第%d跨" % span_i, unit_uuid, span_i, None,
               compact_json(span_props), "SPAN", span_i, B_CODE,
               None, None, None, None, span_uuid)
        for layer_idx, (layer_key, layer_name, gamma, parts) in enumerate(
                layers, start=1):
            layer_uuid = "%s-s%d-%s" % (UUID_PREFIX, span_i, layer_key)
            layer_props = {"stdVersion": STD_VERSION,
                           "bridgeType": B_CODE, "layer": layer_key}
            insert(layer_name, span_uuid, layer_idx,
                   "%s.layer.%s" % (B_CODE, layer_key),
                   compact_json(layer_props), "LAYER", span_i, None,
                   gamma, None, None, None, layer_uuid)
            for part_idx, (p_key, p_name, omega) in enumerate(parts,
                                                               start=1):
                part_uuid = "%s-s%d-%s-%s" % (
                    UUID_PREFIX, span_i, layer_key, p_key)
                part_props = {"stdVersion": STD_VERSION,
                              "bridgeType": B_CODE,
                              "layer": layer_key, "part": p_key}
                is_main = 1 if p_key in mains else 0
                insert(p_name, layer_uuid, part_idx,
                       "%s.part.%s" % (B_CODE, p_key),
                       compact_json(part_props), "PART", span_i, None,
                       None, omega, p_key, is_main, part_uuid)

    sys.stdout.write("\n".join(out) + "\n")
    sys.stderr.write("generated: bi_object tree nodes=%d (%d spans)\n"
                     % (total, N_SPANS))


if __name__ == "__main__":
    main()
