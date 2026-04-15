package com.example.location.model;

import java.util.List;

/**
 * 行政区划历史沿革查询结果
 *
 * <p>示例（东城区 110101）：
 * <pre>
 * {
 *   "current":      { "code": "110101", "name": "北京市东城区" },
 *   "predecessors": [ { "code": "110103", "name": "北京市崇文区" } ]
 * }
 * </pre>
 */
public class HistoryResult {

    /**
     * 当前行政区划信息。
     * name 由 history.json 按"省+市+区县"规则拼接得出。
     */
    private RegionNode current;

    /**
     * 历史前身列表（可能为空）。
     * 每条代表一个曾经存在但已被撤销或合并的旧行政区划，
     * 其 code 和 name 同样来自 history.json。
     */
    private List<RegionNode> predecessors;

    public HistoryResult() {}

    public HistoryResult(RegionNode current, List<RegionNode> predecessors) {
        this.current = current;
        this.predecessors = predecessors;
    }

    public RegionNode getCurrent() { return current; }
    public void setCurrent(RegionNode current) { this.current = current; }
    public List<RegionNode> getPredecessors() { return predecessors; }
    public void setPredecessors(List<RegionNode> predecessors) { this.predecessors = predecessors; }
}
