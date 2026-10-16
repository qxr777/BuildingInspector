#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 5230 种子第 11 节：由 bi_template_object 的 B01 桥型在 bi_object 实例化
【单条评定单元 UNIT 根节点】。

2026-10-15 口径变更：演示业务链需要的是「一座桥 = 一个 UNIT」，与后端
BuildingServiceImpl.generateSpanRootNode() 新标准建桥语义一致——只自动生成 UNIT，
SPAN/LAYER/PART 子树由 App 端在检测作业中创建（演示环境也不预置）。
因此本生成器不再产出 2 跨 33 节点演示树，仅保留 UNIT 一条；它被第 12 节
gen_demo_business_chain.py 产出的 bi_building.new_root_object_id 引用。

说明：
- 无项目/桥梁业务行写在本节；业务链（用户/项目/桥梁/任务）在第 12 节。
- 幂等：固定 offline_uuid=std5230demo-b01-unit 自然键，WHERE NOT EXISTS，可重复执行。
- 输出手工拼接到 20261014_std5230_seed.sql 第 10 节之后（同第 8/9/10/12 节，不进主生成器）。

用法：python3 -I sql/tools/gen_demo_object_tree.py
"""
import json
import os
import sys

import yaml

STD_VERSION = "5230-2026"
B_CODE = "B01"
DEMO_BRIDGE_NAME = "5230演示桥"
UNIT_UUID = "std5230demo-b01-unit"


def q(s):
    if s is None:
        return "NULL"
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def main():
    repo = os.path.dirname(os.path.dirname(os.path.dirname(
        os.path.abspath(__file__))))
    meta_path = os.path.join(repo, "bi-biz", "src", "main", "resources",
                             "std", STD_VERSION, "meta.yml")

    with open(meta_path, encoding="utf-8") as f:
        b_name = next(b["name"] for b in yaml.safe_load(f)["bridgeTypes"]
                      if b["code"] == B_CODE)

    out = []
    w = out.append

    w("")
    w("-- ---------- 11. bi_object（B01 桥型实例化评定单元：仅 1 条 UNIT 根节点；")
    w("--     手工维护，生成器 sql/tools/gen_demo_object_tree.py） ----------")
    w("-- 与后端 BuildingServiceImpl.generateSpanRootNode() 语义一致：新标准建桥只生成")
    w("-- UNIT，SPAN/LAYER/PART 由 App 端作业时创建；本节点被第 12 节演示桥")
    w("-- bi_building.new_root_object_id 引用，支撑 user.db 出包。")
    w("-- 幂等：固定 offline_uuid=%s 自然键 WHERE NOT EXISTS。" % UNIT_UUID)

    tpl_id = ("(SELECT t.`id` FROM `bi_template_object` t "
              "WHERE t.`node_code`=%s AND t.`std_version`=%s)"
              % (q("%s.root" % B_CODE), q(STD_VERSION)))
    unit_props = json.dumps(
        {"stdVersion": STD_VERSION, "bridgeType": B_CODE,
         "structureComplete": True},
        ensure_ascii=False, separators=(",", ":"))

    cols = ("(`name`,`parent_id`,`ancestors`,`order_num`,`template_object_id`,"
            "`count`,`props_json`,`node_type`,`span_no`,`bridge_type`,"
            "`gamma`,`omega`,`leaf_object_id`,`is_main_component`,"
            "`offline_uuid`,`parent_uuid`,`is_offline_data`,`offline_deleted`,"
            "`create_by`,`create_time`)")
    values = [
        q("%s(%s)" % (DEMO_BRIDGE_NAME, b_name)),
        "0", q("0"), "0",
        tpl_id,
        "0", q(unit_props), q("UNIT"),
        "NULL", q(B_CODE),
        "NULL", "NULL", "NULL", "NULL",
        q(UNIT_UUID), q("0"), "0", "0",
        q("std5230-seed"), "NOW()",
    ]
    w("INSERT INTO `bi_object` %s SELECT %s "
      "WHERE NOT EXISTS (SELECT 1 FROM `bi_object` o "
      "WHERE o.`offline_uuid`=%s);"
      % (cols, ",".join(values), q(UNIT_UUID)))

    sys.stdout.write("\n".join(out) + "\n")
    sys.stderr.write("generated: bi_object tree nodes=1 (UNIT only)\n")


if __name__ == "__main__":
    main()
