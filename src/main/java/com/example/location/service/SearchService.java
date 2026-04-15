package com.example.location.service;

import com.example.location.data.RegionDataStore;
import com.example.location.model.SearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 行政区划模糊搜索服务
 *
 * <p>还原自原始项目的 {@code locationSearch.js}，实现步骤如下：
 * <ol>
 *   <li><b>索引构建</b>（由 {@link RegionDataStore} 在启动时完成）：对每个地名去行政后缀和民族后缀，
 *       再按省/市/区县层级拼接成可多角度搜索的字符串。</li>
 *   <li><b>查询时关键词预处理</b>：将空格、特殊符号统一转为分隔符，
 *       关键词超过3字时同样去掉行政后缀（让"西湖区"和"西湖"都能命中"西湖区"）。</li>
 *   <li><b>匹配与去重</b>：在索引中查找包含关键词的条目，
 *       若父级已出现在结果集中则子级不重复返回。</li>
 * </ol>
 */
@Service
public class SearchService {

    /**
     * 行政区划后缀列表，用于关键词预处理时去除后缀。
     * 与 {@link RegionDataStore} 中的 UNIT_LIST 保持一致，从长到短排列。
     */
    private static final List<String> UNIT_LIST = Arrays.asList(
        "特别行政区", "自治区", "自治州", "自治县", "自治旗",
        "省", "市", "盟", "县", "旗", "地区", "矿区", "林区", "特区", "区"
    );

    /**
     * 民族名称列表，用于关键词预处理时去除民族名。
     * 与 {@link RegionDataStore} 中的 PEOPLE_LIST 保持一致。
     */
    private static final List<String> PEOPLE_LIST = Arrays.asList(
        "汉族","壮族","蒙古族","回族","藏族","维吾尔族","苗族","彝族","布依族",
        "朝鲜族","满族","侗族","瑶族","白族","东乡族","锡伯族","土家族","哈尼族",
        "哈萨克族","傣族","黎族","僳僳族","佤族","畲族","拉祜族","水族","纳西族",
        "景颇族","柯尔克孜族","土族","高山族","达斡尔族","仫佬族","羌族","撒拉族",
        "德昂族","仡佬族","阿昌族","普米族","布朗族","塔吉克族","怒族","乌孜别克族",
        "俄罗斯族","鄂温克族","毛南族","保安族","裕固族","京族","塔塔尔族","独龙族",
        "鄂伦春族","赫哲族","门巴族","珞巴族","基诺族"
    );

    @Autowired
    private RegionDataStore store;

    /**
     * 执行模糊搜索。
     *
     * <p>关键词长度判断逻辑：
     * <ul>
     *   <li>超过3字：对关键词去行政/民族后缀后在预建索引中匹配，命中率更高</li>
     *   <li>不超过3字：直接在原始名称中做包含匹配，避免去后缀后关键词变得过短导致误匹配</li>
     * </ul>
     *
     * <p>去重规则：若某条目的父级（省或市）已在结果集中，则该条目不再加入结果，
     * 避免"北京市"和"北京市东城区"同时出现。
     *
     * @param keyword  用户输入的搜索关键词
     * @param limit    最多返回条数（上限由 Controller 控制，最大20）
     * @param useList2 true 时使用含管理区的 list2.json 数据，false 时使用 list.json
     * @return 搜索结果列表，每条包含代码、名称、完整路径名
     */
    public List<SearchResult> query(String keyword, int limit, boolean useList2) {
        // 将空格、#-/.等分隔符统一替换为|，方便后续处理
        keyword = keyword.trim().replaceAll("[\\s#\\-/.]+", "|");

        Map<String, String> data     = useList2 ? store.getCurrentMap2()      : store.getCurrentMap();
        Map<String, String> indexMap = useList2 ? store.getSearchIndexMap2()  : store.getSearchIndexMap();

        // 去掉多余的 | 得到纯净关键词
        String cleanKeyword = keyword.replaceAll("\\|+", "");

        // temp 保存所有命中的代码，用 LinkedHashMap 保持匹配顺序
        Map<String, Boolean> temp = new LinkedHashMap<>();

        if (cleanKeyword.length() > 3) {
            // 长关键词：对关键词去行政/民族后缀，再在搜索索引中匹配
            String stripped = stripKeyword(cleanKeyword);
            for (Map.Entry<String, String> e : indexMap.entrySet()) {
                if (e.getValue().contains(stripped)) {
                    temp.put(e.getKey(), Boolean.TRUE);
                }
            }
        } else {
            // 短关键词：直接在原始名称中做包含匹配
            for (Map.Entry<String, String> e : data.entrySet()) {
                if (e.getValue().contains(cleanKeyword)) {
                    temp.put(e.getKey(), Boolean.TRUE);
                }
            }
        }

        // 按层级去重，组装最终结果
        List<SearchResult> result = new ArrayList<>();
        for (String key : temp.keySet()) {
            if (result.size() >= limit) break;

            String provKey = key.substring(0, 2) + "0000"; // 对应省级代码
            String cityKey = key.substring(0, 4) + "00";   // 对应市级代码

            if (key.matches("\\d{2}0000")) {
                // 省级：直接加入
                result.add(buildResult(key, data));
            } else if (key.matches("\\d{4}00")) {
                // 市级：若对应省级已在结果集中则跳过（避免重复）
                if (!temp.containsKey(provKey)) {
                    result.add(buildResult(key, data));
                }
            } else {
                // 区县级：若对应省级或市级已在结果集中则跳过
                if (!temp.containsKey(provKey) && !temp.containsKey(cityKey)) {
                    result.add(buildResult(key, data));
                }
            }
        }
        return result;
    }

    /**
     * 对搜索关键词去行政后缀和民族后缀，还原自 {@code locationSearch.js} 的关键词处理逻辑。
     *
     * <p>JS 原逻辑：对每个后缀最多尝试两轮替换，替换后若产生过短片段（|X|，X≤1字符）则放弃本次替换。
     * 此保护机制防止将"区"等单字替换后产生空片段，导致匹配失效。
     *
     * @param keyword 已清洗的关键词（无|符号）
     * @return 去除后缀后的关键词
     */
    private String stripKeyword(String keyword) {
        for (String unit : UNIT_LIST) {
            for (int i = 0; i < 2; i++) {
                String candidate = "|" + keyword.replace(unit, "|");
                // 若替换后出现 |X|（X为0或1个字符）的过短片段，放弃本次替换
                if (!candidate.matches(".*\\|\\S{0,1}\\|.*")) {
                    keyword = keyword.replace(unit, "|");
                }
            }
        }
        for (String people : PEOPLE_LIST) {
            for (int i = 0; i < 2; i++) {
                String candidate = "|" + keyword.replace(people, "|");
                if (!candidate.matches(".*\\|\\S{0,1}\\|.*")) {
                    keyword = keyword.replace(people, "|");
                }
            }
        }
        // 去掉所有 | 分隔符，得到最终搜索词
        return keyword.replaceAll("\\|+", "");
    }

    /**
     * 根据代码和数据 Map 构建单条搜索结果，拼接完整路径名（省+市+区县）。
     *
     * @param code 6位行政区划代码
     * @param data 代码→名称 Map
     * @return 包含 code、name、fullName 的搜索结果
     */
    private SearchResult buildResult(String code, Map<String, String> data) {
        String provKey = key2Prov(code);
        String cityKey = key2City(code);
        String name = data.getOrDefault(code, "");
        String fullName;
        if (code.matches("\\d{2}0000")) {
            // 省级：fullName 即本身
            fullName = name;
        } else if (code.matches("\\d{4}00")) {
            // 市级：省名 + 市名
            fullName = data.getOrDefault(provKey, "") + name;
        } else {
            // 区县级：省名 + 市名 + 区县名
            fullName = data.getOrDefault(provKey, "") + data.getOrDefault(cityKey, "") + name;
        }
        return new SearchResult(code, name, fullName);
    }

    /** 由6位代码取其省级代码（前2位 + "0000"） */
    private String key2Prov(String code) { return code.substring(0, 2) + "0000"; }

    /** 由6位代码取其市级代码（前4位 + "00"） */
    private String key2City(String code) { return code.substring(0, 4) + "00"; }
}
