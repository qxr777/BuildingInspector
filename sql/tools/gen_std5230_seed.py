#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
JTG/T 5230—2026 标准种子 SQL 生成器。

读取 bi-biz/src/main/resources/std/5230-2026/**/*.yml（与 Java 端
StandardCatalogLoader/StdSeedServiceImpl 同一权威源），生成一份
按自然键幂等的一次性种子 SQL：

  bi_disease_type / bi_disease_scale / bi_template_object（21 颗桥型树，深度只到 Part）
  + bi_leaf_object / bi_lo_disease_type（病害类型统一挂跨桥型叶子部件，
    由模板部件 + 病害 types.parts 推导）
  + bi_material_durability（第4章三类）/ bi_repair_strengthening_structure（第13章四类）

设计约定：新标准不使用 bi_template_object_disease_type（旧 H21 表，本种子不写）；
病害位置不做字典（bi_disease_position 已取消并 DROP），位置以字符串直接存于
bi_lo_disease_position.position，由人工按叶子部件配置，不在本种子内。

用法：
  python3 sql/tools/gen_std5230_seed.py > sql/20261014_std5230_seed.sql

前置：目标库已执行 test_env_all_in_one.sql（上述表与 std_version/scale_type 列存在）。
可重复执行：所有 INSERT 均按 (code/node_code, std_version) 等自然键 WHERE NOT EXISTS。
注意：本快照只 INSERT 不 UPDATE/不删除；YAML 变更后的对账/更新仍走 Java 种子接口。
"""
import json
import os
import sys

import yaml

STD_VERSION = "5230-2026"
LAYER_ORDER = ["deck", "superstructure", "substructure", "ancillary"]

# 叶子部件固定展示顺序（跨桥型去重后的 18 个末端部件）
LEAF_ORDER = [
    ("paving", "桥面铺装", "deck"),
    ("expansionJoint", "伸缩装置", "deck"),
    ("railing", "护栏及人行道", "deck"),
    ("drainage", "排水系统", "deck"),
    ("slab", "实心板或空心板", "superstructure"),
    ("hinge", "铰缝", "superstructure"),
    ("mainGirder", "主梁", "superstructure"),
    ("diaphragm", "横隔板", "superstructure"),
    ("boxGirder", "箱梁", "superstructure"),
    ("steelMainGirder", "主梁", "superstructure"),
    ("deckSlab", "桥面板", "superstructure"),
    ("lateralBracing", "横向联系", "superstructure"),
    ("bearing", "支座", "superstructure"),
    ("pierAbutment", "桥墩或桥台", "substructure"),
    ("foundation", "墩台基础", "substructure"),
    ("riverbed", "河床及调治构造物", "ancillary"),
    ("revetment", "翼墙、锥坡、护坡", "ancillary"),
    ("otherAncillary", "其他附属设施", "ancillary"),
]

# 第 4 章材料耐久性分类（code 为新定自然键，后续 YAML 需对齐）
MATERIALS = [
    ("reinforcedConcrete", "配筋混凝土构件", 1),
    ("steel", "钢构件", 2),
    ("masonry", "圬工构件", 3),
]

# 第 13 章维修加固结构四类
REPAIR_STRUCTURES = [
    ("sectionEnlargement", "增大截面加固", 1),
    ("bondedSteelPlate", "粘贴钢板加固", 2),
    ("bondedFRP", "粘贴纤维复合材料加固", 3),
    ("externalPrestressing", "增设体外预应力加固", 4),
]


def repo_root():
    return os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))


def q(value):
    """SQL 字符串字面量；None → NULL。"""
    if value is None:
        return "NULL"
    s = str(value)
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"


def num3(value):
    if value is None:
        return "NULL"
    return "%.3f" % float(value)


def num4(value):
    if value is None:
        return "NULL"
    return "%.4f" % float(value)


def compact_json(obj):
    # 对齐 hutool JSONUtil.toJsonStr：紧凑、不转义中文、布尔小写
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


def main():
    root = os.path.join(repo_root(), "bi-biz", "src", "main", "resources",
                        "std", STD_VERSION)

    # ---------- 读 YAML（复刻 StandardCatalogLoader 的装配） ----------
    meta = None
    shared_layers = {}
    templates = {}
    disease_types = []

    with open(os.path.join(root, "meta.yml"), encoding="utf-8") as f:
        meta_doc = yaml.safe_load(f)
    meta = meta_doc
    bridge_types = [(b["code"], b["name"], bool(b["structureComplete"]))
                    for b in meta_doc["bridgeTypes"]]

    with open(os.path.join(root, "templates", "shared-layers.yml"), encoding="utf-8") as f:
        for layer in yaml.safe_load(f)["layers"]:
            shared_layers[layer["key"]] = (
                layer["name"],
                [(p["key"], p["name"], int(p["omega"])) for p in layer["parts"]],
            )

    for b_code, _, _ in bridge_types:
        path = os.path.join(root, "templates", "%s.yml" % b_code)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as f:
            doc = yaml.safe_load(f)
        templates[b_code] = doc

    # 表 3.3.3 主要部件规则（仅 B01-B07；模板 PART 节点 is_main_component 来源）
    main_parts_path = os.path.join(root, "rules", "main-parts.yml")
    main_parts = {}
    if os.path.exists(main_parts_path):
        with open(main_parts_path, encoding="utf-8") as f:
            main_parts = (yaml.safe_load(f) or {}).get("mainParts") or {}

    dt_dir = os.path.join(root, "disease-types")
    seen_codes = set()
    for fname in sorted(os.listdir(dt_dir)):
        if not fname.endswith(".yml"):
            continue
        with open(os.path.join(dt_dir, fname), encoding="utf-8") as f:
            doc = yaml.safe_load(f)
        for t in doc.get("diseaseTypes", []):
            if t["code"] in seen_codes:
                sys.stderr.write("ERROR duplicate disease code %s\n" % t["code"])
                sys.exit(1)
            seen_codes.add(t["code"])
            disease_types.append(t)

    # ---------- 模板节点物化（复刻 materializeNodes） ----------
    # (node_code, name, parent_code, order, status, gamma, omega, props,
    #  node_type[ROOT/LAYER/PART], is_main_component[仅PART,1/0])
    nodes = []
    for idx, (b_code, b_name, complete_flag) in enumerate(bridge_types):
        tpl = templates.get(b_code)
        complete = complete_flag and tpl is not None
        root_props = {"stdVersion": STD_VERSION, "bridgeType": b_code,
                      "structureComplete": complete}
        rules = []
        if complete and tpl.get("rules"):
            rules = [str(r) for r in tpl["rules"]]
            root_props["rules"] = rules
        nodes.append(("%s.root" % b_code, b_name, None, idx,
                      "0" if complete else "1", None, None,
                      compact_json(root_props), "ROOT", None))
        if not complete:
            continue
        for layer_idx, layer_key in enumerate(LAYER_ORDER):
            if layer_key == "superstructure":
                layer_name = tpl.get("superstructureName") or "上部结构"
                parts = [(p["key"], p["name"], int(p["omega"]))
                         for p in tpl.get("superstructureParts", [])]
            else:
                layer_name, parts = shared_layers[layer_key]
            gamma = tpl["gamma"][layer_key]
            layer_code = "%s.layer.%s" % (b_code, layer_key)
            nodes.append((layer_code, layer_name, "%s.root" % b_code,
                          layer_idx + 1, "0", gamma, None,
                          compact_json({"stdVersion": STD_VERSION,
                                        "bridgeType": b_code, "layer": layer_key}),
                          "LAYER", None))
            for part_idx, (p_key, p_name, omega) in enumerate(parts):
                part_code = "%s.part.%s" % (b_code, p_key)
                is_main = 1 if p_key in (main_parts.get(b_code) or []) else 0
                nodes.append((part_code, p_name, layer_code, part_idx + 1,
                              "0", None, omega,
                              compact_json({"stdVersion": STD_VERSION,
                                            "bridgeType": b_code,
                                            "layer": layer_key, "part": p_key}),
                              "PART", is_main))

    # ---------- 生成 SQL ----------
    out = []
    w = out.append

    w("-- ==============================================================================")
    w("-- JTG/T 5230—2026 标准种子（一次性快照，自然键幂等，可重复执行）")
    w("-- 由 sql/tools/gen_std5230_seed.py 从 bi-biz/.../std/5230-2026/*.yml 生成，勿手改。")
    w("-- 前置：先执行 test_env_all_in_one.sql。")
    w("-- 与 POST /biz/stdSeed/run 的差异：本快照只 INSERT 缺失行，不 UPDATE/不软删对账；")
    w("--   YAML 内容变更后的更新与对账请走 Java 种子接口。")
    w("-- 规模：病害类型 %d、标度 %d、模板节点 %d；叶子部件 18；材料 3；加固结构 4。"
      % (len(disease_types),
         sum(len(t.get("scales") or []) for t in disease_types),
         len(nodes)))
    w("-- ==============================================================================")
    w("SET NAMES utf8mb4;")
    w("")

    # 1) 病害类型（threshold 缺省 10，select_column 留空；scale_type 走列默认 EDI）
    w("-- ---------- 1. bi_disease_type（%d 行） ----------" % len(disease_types))
    for t in disease_types:
        threshold = int(t["threshold"]) if t.get("threshold") is not None else 10
        select_col = (str(int(t["selectColumn"]))
                      if t.get("selectColumn") is not None else "NULL")
        w(
            "INSERT INTO `bi_disease_type` "
            "(`code`,`name`,`min_scale`,`max_scale`,`status`,`threshold`,`select_column`,"
            "`std_version`,`create_by`,`create_time`) "
            "SELECT %s,%s,%s,%s,'0',%s,%s,%s,'std5230-seed',NOW() "
            "WHERE NOT EXISTS (SELECT 1 FROM `bi_disease_type` "
            "WHERE `code`=%s AND `std_version`=%s);"
            % (q(t["code"]), q(t["name"]),
               int(t["minScale"]) if t.get("minScale") is not None else "NULL",
               int(t["maxScale"]) if t.get("maxScale") is not None else "NULL",
               threshold, select_col, q(STD_VERSION),
               q(t["code"]), q(STD_VERSION))
        )
    w("")

    # 2) 病害标度
    total_scales = sum(len(t.get("scales") or []) for t in disease_types)
    w("-- ---------- 2. bi_disease_scale（%d 行） ----------" % total_scales)
    for t in disease_types:
        for s in (t.get("scales") or []):
            w(
                "INSERT INTO `bi_disease_scale` "
                "(`type_code`,`scale`,`qualitative_description`,`quantitative_description`,"
                "`metric_key`,`value_lower`,`value_upper`,`unit`,`status`,"
                "`std_version`,`create_by`,`create_time`) "
                "SELECT %s,%s,%s,%s,%s,%s,%s,%s,'0',%s,'std5230-seed',NOW() "
                "WHERE NOT EXISTS (SELECT 1 FROM `bi_disease_scale` "
                "WHERE `type_code`=%s AND `scale`=%s AND `std_version`=%s);"
                % (q(t["code"]), int(s["scale"]),
                   q(s.get("qualitative")), q(s.get("quantitative")),
                   q(s.get("metricKey")), num3(s.get("valueLower")),
                   num3(s.get("valueUpper")), q(s.get("unit")),
                   q(STD_VERSION), q(t["code"]), int(s["scale"]), q(STD_VERSION))
            )
    w("")

    # 3) 模板节点（根 parent_id=0/ancestors='0'；其余先插入再按 node_code 回填父链）
    w("-- ---------- 3. bi_template_object（%d 节点：21 根 + B01-B07 层/部件） ----------"
      % len(nodes))
    for code, name, parent, order, status, gamma, omega, props, node_type, is_main in nodes:
        if parent is None:
            parent_sql, ancestors_sql = "0", "'0'"
        else:
            parent_sql, ancestors_sql = "0", "''"  # 占位，下面 UPDATE 回填
        is_main_sql = ("NULL" if is_main is None
                       else ("1" if is_main else "0"))
        w(
            "INSERT INTO `bi_template_object` "
            "(`node_code`,`name`,`parent_id`,`ancestors`,`order_num`,`status`,"
            "`gamma`,`omega`,`props`,`node_type`,`is_main_component`,"
            "`std_version`,`create_by`,`create_time`) "
            "SELECT %s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,'std5230-seed',NOW() "
            "WHERE NOT EXISTS (SELECT 1 FROM `bi_template_object` "
            "WHERE `node_code`=%s AND `std_version`=%s);"
            % (q(code), q(name), parent_sql, ancestors_sql, order, status,
               num4(gamma) if gamma is not None else "NULL",
               omega if omega is not None else "NULL",
               q(props), q(node_type), is_main_sql, q(STD_VERSION),
               q(code), q(STD_VERSION))
        )
    w("")
    w("-- 父链回填（幂等收敛；根固定 parent_id=0/ancestors='0'，层与部件按生成顺序）")
    for code, _, parent, _, _, _, _, _, _, _ in nodes:
        if parent is None:
            continue
        w(
            "UPDATE `bi_template_object` c "
            "JOIN `bi_template_object` p "
            "  ON p.`node_code`=%s AND p.`std_version`=%s "
            "SET c.`parent_id`=p.`id`, "
            "    c.`ancestors`=CONCAT(p.`ancestors`,',',p.`id`) "
            "WHERE c.`node_code`=%s AND c.`std_version`=%s;"
            % (q(parent), q(STD_VERSION), q(code), q(STD_VERSION))
        )
    w("")
    w("-- node_type / is_main_component 存量行回填（旧版种子已插过、INSERT 跳过的行；幂等）")
    w("UPDATE `bi_template_object` SET `node_type`='ROOT' "
      "WHERE `std_version`=%s AND `node_code` LIKE '%%.root' AND `node_type` IS NULL;"
      % q(STD_VERSION))
    w("UPDATE `bi_template_object` SET `node_type`='LAYER' "
      "WHERE `std_version`=%s AND `node_code` LIKE '%%.layer.%%' AND `node_type` IS NULL;"
      % q(STD_VERSION))
    w("UPDATE `bi_template_object` SET `node_type`='PART' "
      "WHERE `std_version`=%s AND `node_code` LIKE '%%.part.%%' AND `node_type` IS NULL;"
      % q(STD_VERSION))
    for b_code, part_keys in sorted(main_parts.items()):
        for p_key in part_keys:
            w("UPDATE `bi_template_object` SET `is_main_component`=1 "
              "WHERE `std_version`=%s AND `node_code`=%s AND `is_main_component` IS NULL;"
              % (q(STD_VERSION), q("%s.part.%s" % (b_code, p_key))))
    w("UPDATE `bi_template_object` SET `is_main_component`=0 "
      "WHERE `std_version`=%s AND `node_type`='PART' AND `is_main_component` IS NULL;"
      % q(STD_VERSION))
    w("")

    # 4) 叶子部件字典（病害类型只挂叶子部件，不挂模板树；不写旧表 bi_template_object_disease_type）
    w("-- ---------- 4. bi_leaf_object（18 个跨桥型末端部件） ----------")
    for order, (code, name, layer) in enumerate(LEAF_ORDER, start=1):
        w(
            "INSERT INTO `bi_leaf_object` "
            "(`code`,`name`,`structure_layer`,`order_num`,`std_version`,`status`,"
            "`create_by`,`create_time`) "
            "SELECT %s,%s,%s,%s,%s,'0','std5230-seed',NOW() "
            "WHERE NOT EXISTS (SELECT 1 FROM `bi_leaf_object` "
            "WHERE `code`=%s AND `std_version`=%s);"
            % (q(code), q(name), q(layer), order, q(STD_VERSION),
               q(code), q(STD_VERSION))
        )
    w("")

    # 5) 叶子部件-病害类型绑定（病害类型挂叶子部件，与桥型无关）
    lo_count = 0
    lo_sql = []
    for t in disease_types:
        for part_key in (t.get("parts") or []):
            if not any(code == part_key for code, _, _ in LEAF_ORDER):
                continue
            lo_count += 1
            lo_sql.append(
                "INSERT INTO `bi_lo_disease_type` "
                "(`leaf_object_id`,`disease_type_id`,`create_time`) "
                "SELECT (SELECT l.`id` FROM `bi_leaf_object` l "
                "        WHERE l.`code`=%s AND l.`std_version`=%s), "
                "       (SELECT d.`id` FROM `bi_disease_type` d "
                "        WHERE d.`code`=%s AND d.`std_version`=%s), NOW() "
                "WHERE NOT EXISTS (SELECT 1 FROM `bi_lo_disease_type` b "
                "  JOIN `bi_leaf_object` l ON b.`leaf_object_id`=l.`id` "
                "  JOIN `bi_disease_type` d ON b.`disease_type_id`=d.`id` "
                " WHERE l.`code`=%s AND l.`std_version`=%s "
                "   AND d.`code`=%s AND d.`std_version`=%s);"
                % (q(part_key), q(STD_VERSION), q(t["code"]), q(STD_VERSION),
                   q(part_key), q(STD_VERSION), q(t["code"]), q(STD_VERSION))
            )
    w("-- ---------- 5. bi_lo_disease_type（%d 绑定） ----------" % lo_count)
    out.extend(lo_sql)
    w("")

    # 6) 材料耐久性分类（第 4 章）
    w("-- ---------- 6. bi_material_durability（第4章三类） ----------")
    for code, name, order in MATERIALS:
        w(
            "INSERT INTO `bi_material_durability` "
            "(`code`,`name`,`order_num`,`std_version`,`status`,`create_by`,`create_time`) "
            "SELECT %s,%s,%s,%s,'0','std5230-seed',NOW() "
            "WHERE NOT EXISTS (SELECT 1 FROM `bi_material_durability` "
            "WHERE `code`=%s AND `std_version`=%s);"
            % (q(code), q(name), order, q(STD_VERSION), q(code), q(STD_VERSION))
        )
    w("")

    # 7) 维修加固结构（第 13 章）
    w("-- ---------- 7. bi_repair_strengthening_structure（第13章四类） ----------")
    for code, name, order in REPAIR_STRUCTURES:
        w(
            "INSERT INTO `bi_repair_strengthening_structure` "
            "(`code`,`name`,`order_num`,`std_version`,`status`,`create_by`,`create_time`) "
            "SELECT %s,%s,%s,%s,'0','std5230-seed',NOW() "
            "WHERE NOT EXISTS (SELECT 1 FROM `bi_repair_strengthening_structure` "
            "WHERE `code`=%s AND `std_version`=%s);"
            % (q(code), q(name), order, q(STD_VERSION), q(code), q(STD_VERSION))
        )
    w("")
    w("-- ============================== 5230 种子结束 ==============================")

    sys.stdout.write("\n".join(out) + "\n")
    sys.stderr.write(
        "generated: types=%d scales=%d nodes=%d "
        "leaf=18 leaf_bindings=%d materials=3 rss=4\n"
        % (len(disease_types), total_scales, len(nodes), lo_count)
    )


if __name__ == "__main__":
    main()
