package com.example.location.model;

/**
 * 模糊搜索单条结果
 *
 * <p>在 {@link RegionNode} 基础上增加 {@code fullName} 字段，
 * 方便前端直接展示完整的省市区路径，无需二次拼接。
 */
public class SearchResult {

    /** 6位行政区划代码 */
    private String code;

    /** 区划本级名称，如 "朝阳区" */
    private String name;

    /** 完整路径名称，如 "北京市朝阳区"、"辽宁省朝阳市" */
    private String fullName;

    public SearchResult() {}

    public SearchResult(String code, String name, String fullName) {
        this.code = code;
        this.name = name;
        this.fullName = fullName;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
}
