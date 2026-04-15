package com.example.location.service;

import com.example.location.data.RegionDataStore;
import com.example.location.model.HistoryResult;
import com.example.location.model.RegionNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 行政区划历史沿革查询服务
 *
 * <p>给定一个区县代码，返回：
 * <ul>
 *   <li><b>当前名称</b>：从 history.json 中拼接该代码对应的完整地址（省+市+区县）</li>
 *   <li><b>历史前身</b>：从 reverseDiffMap 中查出所有曾被合并/撤销的旧代码，
 *       对每个旧代码同样拼接完整历史地址</li>
 * </ul>
 *
 * <p>例：东城区（110101）的历史前身是崇文区（110103），
 * 因为 diff.json 中有 "110103": ["110101"] 这条记录。
 */
@Service
public class HistoryService {

    @Autowired
    private RegionDataStore store;

    /**
     * 查询指定区划代码的历史沿革。
     *
     * @param code 6位行政区划代码
     * @return 包含当前名称和所有历史前身的 {@link HistoryResult}
     */
    public HistoryResult getHistory(String code) {
        Map<String, String> historyMap       = store.getHistoryMap();
        Map<String, List<String>> reverseDiffMap = store.getReverseDiffMap();

        // 拼接当前代码对应的完整地址名称
        RegionNode current = new RegionNode(code, getName(code, historyMap));

        // 从反向 diff 索引取出所有历史前身代码，逐一拼接历史地址
        List<RegionNode> predecessors = new ArrayList<>();
        for (String oldCode : reverseDiffMap.getOrDefault(code, Collections.emptyList())) {
            predecessors.add(new RegionNode(oldCode, getName(oldCode, historyMap)));
        }

        return new HistoryResult(current, predecessors);
    }

    /**
     * 根据行政区划代码从 historyMap 拼接完整地址名称，还原自原始 JS 中的 {@code getName(code)} 函数。
     *
     * <p>拼接规则（省 + 市 + 区县）：
     * <ul>
     *   <li>直辖市（11北京、12天津、31上海、50重庆）：跳过市级，直接拼接 省+区县</li>
     *   <li>省直管县（代码第3-4位为"90"，如 "419001" 济源）：同样跳过市级</li>
     *   <li>其他普通地区：拼接 省+市+区县</li>
     * </ul>
     *
     * <p>此方法声明为 {@code static} 以便 {@link IdCardService} 直接调用，无需注入本服务。
     *
     * @param code       6位行政区划代码（可以是现行代码或历史代码）
     * @param historyMap 历史区划代码→名称 Map（来自 history.json）
     * @return 完整地址字符串，如 "北京市东城区"、"浙江省杭州市西湖区"；代码不存在时可能返回空字符串
     */
    public static String getName(String code, Map<String, String> historyMap) {
        String provKey   = code.substring(0, 2) + "0000"; // 省级代码
        String cityKey   = code.substring(0, 4) + "00";   // 市级代码
        String provPrefix = code.substring(0, 2);          // 省级前缀，如 "11"
        String cityInfix  = code.substring(2, 4);          // 市级中缀，如 "90" 表示省直管

        // 判断是否为直辖市或省直管县（这两类跳过市级拼接）
        boolean isDirect = "11".equals(provPrefix) || "12".equals(provPrefix)
                        || "31".equals(provPrefix) || "50".equals(provPrefix)
                        || "90".equals(cityInfix);

        StringBuilder sb = new StringBuilder();
        // 第一段：省级名称
        sb.append(historyMap.getOrDefault(provKey, ""));
        // 第二段：市级名称（直辖市/省直管县跳过，且避免与省级代码重复）
        if (!isDirect && !code.equals(cityKey)) {
            sb.append(historyMap.getOrDefault(cityKey, ""));
        }
        // 第三段：区县名称（避免与省级或市级代码重复拼接）
        if (!code.equals(provKey) && !code.equals(cityKey)) {
            sb.append(historyMap.getOrDefault(code, ""));
        } else if (code.equals(cityKey) && !code.equals(provKey)) {
            // 传入的是市级代码本身：拼接市名
            sb.append(historyMap.getOrDefault(code, ""));
        }
        return sb.toString();
    }
}
