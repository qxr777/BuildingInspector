package edu.whut.cs.bi.biz.service.std;

/**
 * 标准种子执行范围。
 */
public enum StdSeedScope {

    /** 仅补 schema（列/索引） */
    SCHEMA,

    /** 病害类型与标度 */
    TYPES,

    /** 模板结构树节点 */
    TEMPLATES,

    /** 模板节点与病害类型的绑定 */
    BINDINGS,

    /** 以上全部 */
    ALL
}
