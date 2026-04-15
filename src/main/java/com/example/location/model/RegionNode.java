package com.example.location.model;

/**
 * 通用行政区划节点，表示任意层级的一个区划条目。
 *
 * <p>在三级联动、街道查询、历史沿革等场景中均使用此类返回数据。
 */
public class RegionNode {

    /** GB/T2260 行政区划代码：省市区为6位，街道为9位 */
    private String code;

    /** 行政区划名称，如 "东城区"、"东华门街道" */
    private String name;

    public RegionNode() {}

    public RegionNode(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
