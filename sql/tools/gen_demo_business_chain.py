#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成 5230 种子第 12 节：5230 演示环境最小业务链（零 YAML 依赖）。

目的：让演示账号能够真实走通「下载离线包 → App 记录 → 提交」闭环——
  1. sys_user          专用演示账号 znjc_demo（dept 105 测试部门，角色 2 普通角色）
  2. sys_user_role     授予普通角色
  3. bi_project        2027 年度 5230 演示项目（code=P2027-DEMO-B01，std_version=5230-2026）
  4. bi_project_user   znjc_demo 以 inspector 入项（离线 user.db 的出包闸门）
  5. bi_building       5230演示桥，new_root_object_id 指向第 11 节单条 UNIT
  6. bi_project_building 项目×桥梁
  7. bi_task           未提交(type=0)检测任务，App 可直接记录并提交

登录口令：znjc_demo / admin123
  哈希算法与 sys_user 存量行一致：Md5Hash(loginName + password + salt) 单轮 MD5，
  salt=123456，7c56cf067f62e24e51c41c90aa443738（用 ry/admin123/222222 行交叉验证）。

幂等：全部 INSERT...SELECT...WHERE NOT EXISTS，自然键判重
  （login_name / project.code / building.offline_uuid / 成员三元组 / 项目×桥梁 / task 项目×桥梁），
  不写固定 id、不依赖自增值，可重复执行。

用法：python3 -I sql/tools/gen_demo_business_chain.py
"""
import sys

STD_VERSION = "5230-2026"
LOGIN_NAME = "znjc_demo"
USER_NAME = "5230演示账号"
PASSWORD_HASH = "7c56cf067f62e24e51c41c90aa443738"
SALT = "123456"
DEPT_ID = 105
ROLE_ID = 2
PROJECT_CODE = "P2027-DEMO-B01"
PROJECT_NAME = "5230演示项目"
PROJECT_YEAR = 2027
PROJECT_ROLE = "inspector"
BUILDING_NAME = "5230演示桥"
BUILDING_UUID = "std5230demo-b01-building"
UNIT_UUID = "std5230demo-b01-unit"
OPERATOR = "std5230-seed"


def q(s):
    if s is None:
        return "NULL"
    return "'" + str(s).replace("\\", "\\\\").replace("'", "''") + "'"


def user_id():
    return ("(SELECT `user_id` FROM `sys_user` WHERE `login_name`=%s)"
            % q(LOGIN_NAME))


def project_id():
    return ("(SELECT `id` FROM `bi_project` WHERE `code`=%s)"
            % q(PROJECT_CODE))


def building_id():
    return ("(SELECT `id` FROM `bi_building` WHERE `offline_uuid`=%s)"
            % q(BUILDING_UUID))


def main():
    out = []
    w = out.append

    w("")
    w("-- ---------- 12. 5230 演示环境最小业务链（账号/项目/入项/桥梁/项目桥梁/任务；")
    w("--     手工维护，生成器 sql/tools/gen_demo_business_chain.py，零 YAML 依赖） ----------")
    w("-- 目的：为演示账号 %s 打通 user.db 出包与 App 记录/提交流程。" % LOGIN_NAME)
    w("-- 登录口令：%s / admin123（Md5Hash(loginName+password+salt)，salt=%s）。"
      % (LOGIN_NAME, SALT))
    w("-- 幂等：自然键 WHERE NOT EXISTS，无固定 id，可重复执行。")

    # 12.1 演示账号
    w("")
    w("-- 12.1 演示账号 sys_user（dept_id=105 测试部门）")
    w("INSERT INTO `sys_user`")
    w("  (`dept_id`,`login_name`,`user_name`,`user_type`,`email`,`phonenumber`,`sex`,")
    w("   `password`,`salt`,`status`,`del_flag`,`create_by`,`create_time`,`remark`)")
    w("SELECT %s,%s,%s,'00','','','0'," % (DEPT_ID, q(LOGIN_NAME), q(USER_NAME)))
    w("       %s,%s,'0','0',%s,NOW(),%s"
      % (q(PASSWORD_HASH), q(SALT), q(OPERATOR),
         q("5230演示环境专用账号(登录口令 admin123)")))
    w("WHERE NOT EXISTS (SELECT 1 FROM `sys_user` u WHERE u.`login_name`=%s);"
      % q(LOGIN_NAME))

    # 12.2 授予普通角色
    w("")
    w("-- 12.2 授予普通角色 sys_user_role（role_id=2）")
    w("INSERT INTO `sys_user_role` (`user_id`,`role_id`)")
    w("SELECT %s, %s" % (user_id(), ROLE_ID))
    w("WHERE NOT EXISTS (SELECT 1 FROM `sys_user_role` ur")
    w("   WHERE ur.`user_id`=%s AND ur.`role_id`=%s);" % (user_id(), ROLE_ID))

    # 12.3 2027 年度演示项目
    w("")
    w("-- 12.3 2027 年度演示项目 bi_project")
    w("INSERT INTO `bi_project`")
    w("  (`name`,`year`,`status`,`del_flag`,`dept_id`,`code`,`std_version`,")
    w("   `create_by`,`create_time`,`remark`)")
    w("SELECT %s,%s,'0','0',%s,%s,%s,%s,NOW(),%s"
      % (q(PROJECT_NAME), PROJECT_YEAR, DEPT_ID, q(PROJECT_CODE), q(STD_VERSION),
         q(OPERATOR), q("5230演示环境专用项目")))
    w("WHERE NOT EXISTS (SELECT 1 FROM `bi_project` p WHERE p.`code`=%s);"
      % q(PROJECT_CODE))

    # 12.4 入项（出包闸门：ProjectMapper.selectProjectList 的 EXISTS 条件）
    w("")
    w("-- 12.4 演示账号以 inspector 入项 bi_project_user（离线 user.db 出包闸门）")
    w("INSERT INTO `bi_project_user` (`project_id`,`user_id`,`role`,`create_time`)")
    w("SELECT %s, %s, %s, NOW()" % (project_id(), user_id(), q(PROJECT_ROLE)))
    w("WHERE NOT EXISTS (SELECT 1 FROM `bi_project_user` pu")
    w("   WHERE pu.`project_id`=%s AND pu.`user_id`=%s AND pu.`role`=%s);"
      % (project_id(), user_id(), q(PROJECT_ROLE)))

    # 12.5 演示桥（新标树只挂 UNIT；旧标 root_object_id 留空）
    w("")
    w("-- 12.5 演示桥 bi_building（is_leaf=1，new_root_object_id 指向第 11 节 UNIT）")
    w("INSERT INTO `bi_building`")
    w("  (`name`,`status`,`del_flag`,`is_leaf`,`root_object_id`,`new_root_object_id`,")
    w("   `offline_uuid`,`create_by`,`create_time`,`remark`)")
    w("SELECT %s,'0','0','1',NULL," % q(BUILDING_NAME))
    w("       (SELECT `id` FROM `bi_object` WHERE `offline_uuid`=%s)," % q(UNIT_UUID))
    w("       %s,%s,NOW(),%s"
      % (q(BUILDING_UUID), q(OPERATOR), q("5230演示环境专用桥(B01 整体式板梁桥)")))
    w("WHERE NOT EXISTS (SELECT 1 FROM `bi_building` b WHERE b.`offline_uuid`=%s);"
      % q(BUILDING_UUID))

    # 12.6 项目×桥梁
    w("")
    w("-- 12.6 项目-桥梁关联 bi_project_building")
    w("INSERT INTO `bi_project_building` (`project_id`,`building_id`,`create_time`)")
    w("SELECT %s, %s, NOW()" % (project_id(), building_id()))
    w("WHERE NOT EXISTS (SELECT 1 FROM `bi_project_building` pb")
    w("   WHERE pb.`project_id`=%s AND pb.`building_id`=%s);"
      % (project_id(), building_id()))

    # 12.7 未提交检测任务（App 可直接记录病害并提交）
    w("")
    w("-- 12.7 未提交检测任务 bi_task（type=0 未提交，status/del_flag=0）")
    w("INSERT INTO `bi_task`")
    w("  (`building_id`,`project_id`,`type`,`del_flag`,`status`,`std_version`,")
    w("   `create_by`,`create_time`,`remark`)")
    w("SELECT %s, %s, 0, '0', '0', %s, %s, NOW(), %s"
      % (building_id(), project_id(), q(STD_VERSION), q(OPERATOR),
         q("5230演示环境专用检测任务")))
    w("WHERE NOT EXISTS (SELECT 1 FROM `bi_task` t")
    w("   WHERE t.`project_id`=%s AND t.`building_id`=%s);"
      % (project_id(), building_id()))

    sys.stdout.write("\n".join(out) + "\n")
    sys.stderr.write("generated: business chain 7 inserts "
                     "(user/role/project/member/building/link/task)\n")


if __name__ == "__main__":
    main()
