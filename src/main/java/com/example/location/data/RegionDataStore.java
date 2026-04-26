package com.example.location.data;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行政区划数据仓库
 *
 * <p>应用启动时（{@link PostConstruct}）将以下 JSON 文件一次性加载到内存：
 * <ul>
 *   <li>list.json    — 当前有效区划，6位代码→名称（3432条）</li>
 *   <li>list2.json   — 含中间汇总节点的扩展区划（3576条）</li>
 *   <li>history.json — 已废止的历史区划，6位代码→原名称（7228条）</li>
 *   <li>diff.json    — 旧代码→[新代码]变更映射</li>
 * </ul>
 *
 * <p>启动完成后还会额外构建：
 * <ul>
 *   <li>reverseDiffMap — diff 的反向映射（新代码→[旧代码]），供历史沿革查询使用</li>
 *   <li>searchIndexMap / searchIndexMap2 — 模糊搜索索引，按省/市/区县三级拼接可搜索字符串</li>
 * </ul>
 *
 * <p>街道级数据（code/*.json，共 3209 个文件）采用<b>按需加载</b>策略：
 * 首次访问某区县时读取对应文件并缓存到 {@link #streetCache}，后续请求直接走缓存。
 */
@Component
public class RegionDataStore {

    /** 当前有效区划：6位代码 → 名称，来源 list.json（不含市辖区等中间节点） */
    private Map<String, String> currentMap;

    /** 扩展区划：6位代码 → 名称，来源 list2.json（含市辖区等中间节点） */
    private Map<String, String> currentMap2;

    /**
     * 历史区划：所有曾经存在的6位代码 → 原名称，来源 history.json。
     * 同时也包含当前有效代码，因此可作为"全量代码库"使用（身份证签发地拼接名称时使用此 map）。
     */
    private Map<String, String> historyMap;

    /** 变更映射（正向）：旧代码 → [新代码列表]，来源 diff.json */
    private Map<String, List<String>> diffMap;

    /**
     * 变更映射（反向）：新代码 → [所有历史前身代码列表]。
     * 由 diffMap 在启动时自动构建，用于历史沿革查询（给定当前代码，查所有旧代码）。
     */
    private Map<String, List<String>> reverseDiffMap;

    /** 基于 list.json 构建的模糊搜索索引：6位代码 → 可搜索字符串 */
    private Map<String, String> searchIndexMap;

    /** 基于 list2.json 构建的模糊搜索索引：6位代码 → 可搜索字符串 */
    private Map<String, String> searchIndexMap2;

    /**
     * 街道级数据按需缓存：6位区县代码 → (9位街道代码 → 街道名称)。
     * 使用 ConcurrentHashMap 保证多线程并发请求同一区县时只读取一次文件。
     */
    private final ConcurrentHashMap<String, Map<String, String>> streetCache = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 15种行政区划后缀，从长到短排列，优先匹配较长的后缀。
     * 构建搜索索引时去除名称末尾的后缀，以提升模糊匹配效果。
     * 例："浙江省" → "浙江"，"西湖区" → "西湖"。
     */
    private static final List<String> UNIT_LIST = Arrays.asList(
        "特别行政区", "自治区", "自治州", "自治县", "自治旗",
        "省", "市", "盟", "县", "旗", "地区", "矿区", "林区", "特区", "区"
    );

    /**
     * 56个民族名称。
     * 构建搜索索引时从地名中去除民族名，例："延边朝鲜族自治州" → "延边自治州" → "延边"。
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

    /**
     * 应用启动后自动执行，完成所有数据的加载与索引构建。
     * 执行顺序：加载 JSON → 构建 reverseDiffMap → 构建搜索索引。
     *
     * @throws IOException JSON 文件读取失败时抛出
     */
    @PostConstruct
    public void init() throws IOException {
        currentMap  = loadJson("data/list.json");
        currentMap2 = loadJson("data/list2.json");
        historyMap  = loadJson("data/history.json");
        diffMap     = loadDiff("data/diff.json");

        // 构建反向 diff 索引：遍历 diffMap 的每条"旧→[新]"，翻转为"新→[旧]"
        reverseDiffMap = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : diffMap.entrySet()) {
            for (String newCode : entry.getValue()) {
                reverseDiffMap.computeIfAbsent(newCode, k -> new ArrayList<>()).add(entry.getKey());
            }
        }

        // 分别为 list.json 和 list2.json 构建搜索索引
        searchIndexMap  = buildSearchIndex(currentMap);
        searchIndexMap2 = buildSearchIndex(currentMap2);
    }

    /**
     * 从 classpath 加载 key→value 格式的 JSON 文件，保持原始顺序（LinkedHashMap）。
     *
     * @param path classpath 相对路径，如 "data/list.json"
     * @return 有序的代码→名称 Map
     */
    private Map<String, String> loadJson(String path) throws IOException {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<LinkedHashMap<String, String>>() {});
        }
    }

    /**
     * 从 classpath 加载 key→[value] 格式的 JSON 文件（diff.json 专用）。
     *
     * @param path classpath 相对路径
     * @return 旧代码 → 新代码列表 的 Map
     */
    private Map<String, List<String>> loadDiff(String path) throws IOException {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<LinkedHashMap<String, List<String>>>() {});
        }
    }

    /**
     * 为给定的区划数据构建模糊搜索索引。
     *
     * <p>分两步：
     * <ol>
     *   <li>对每个名称去行政后缀和民族后缀，得到精简名（hash）</li>
     *   <li>按行政层级拼接可搜索字符串（index）：
     *     <ul>
     *       <li>省级：直接存精简名</li>
     *       <li>市级：省名 + 市名 + 省名（支持"浙杭"、"杭浙"等不同输入顺序）</li>
     *       <li>区县级：省名+区名+市名+省名+区名 | 市名+区名+省名+市名+区名（多种排列组合）</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param data 区划代码→名称 Map（来自 list.json 或 list2.json）
     * @return 代码→可搜索字符串 的索引 Map
     */
    private Map<String, String> buildSearchIndex(Map<String, String> data) {
        // 第一步：对每个名称去行政后缀和民族后缀，生成精简名
        Map<String, String> hash = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : data.entrySet()) {
            hash.put(e.getKey(), stripName(e.getValue()));
        }

        // 第二步：按层级构建可搜索字符串
        Map<String, String> index = new LinkedHashMap<>();
        for (String key : hash.keySet()) {
            String provKey = key.substring(0, 2) + "0000"; // 对应省级代码
            String cityKey = key.substring(0, 4) + "00";   // 对应市级代码
            String h = hash.getOrDefault(key, "");          // 当前代码的精简名

            if (key.matches("\\d{2}0000")) {
                // 省级：直接存精简名
                index.put(key, h);
            } else if (key.matches("\\d{4}00")) {
                // 市级：省名+市名+省名，支持"浙江杭州"、"杭州浙江"两种输入顺序
                String p = hash.getOrDefault(provKey, "");
                index.put(key, p + h + p);
            } else if (key.matches("\\d{2}[89]0\\d{2}")) {
                // 省直管县级单位：省名+区县名+省名+区县名（跳过不存在的市级）
                String p = hash.getOrDefault(provKey, "");
                index.put(key, p + h + p + h + "|" + h + p + h);
            } else {
                // 区县级：拼接多种排列，支持"北京朝阳"、"朝阳北京"、"朝阳"等各种输入
                String p = hash.getOrDefault(provKey, "");
                String c = hash.getOrDefault(cityKey, "");
                index.put(key, p + h + c + p + h + "|" + c + h + p + c + h);
            }
        }
        return index;
    }

    /**
     * 对单个地名去行政后缀和民族后缀，用于构建搜索索引。
     *
     * <p>规则：
     * <ul>
     *   <li>名称含"新区"时不做任何处理（保护"浦东新区"、"滨海新区"等）</li>
     *   <li>按后缀列表从长到短匹配，末尾匹配成功且去除后长度≥2时，去掉后缀，只去一次</li>
     *   <li>再去除民族名称（支持去除后长度≥2）</li>
     * </ul>
     *
     * @param name 原始地名
     * @return 精简后的地名
     */
    private String stripName(String name) {
        // 含"新区"的地名不去除任何后缀
        if (name.contains("新区")) {
            return name;
        }
        // 去行政后缀（从长到短，只去一次，去除后须剩余至少2个字符）
        for (String unit : UNIT_LIST) {
            if (name.endsWith(unit) && name.length() - unit.length() >= 2) {
                name = name.substring(0, name.length() - unit.length());
                break;
            }
        }
        // 去民族名称（可能出现在名称中间，去除后须剩余至少2个字符）
        for (String people : PEOPLE_LIST) {
            if (name.contains(people) && name.length() - people.length() >= 2) {
                name = name.replace(people, "");
            }
        }
        return name;
    }

    /**
     * 获取指定区县的街道数据，首次访问时从文件加载并缓存。
     *
     * <p>使用 {@link ConcurrentHashMap#computeIfAbsent} 保证并发场景下同一区县代码只读取一次文件。
     * 若对应文件不存在或解析失败，返回空 Map 而非抛出异常。
     *
     * @param districtCode 6位区县代码，如 "110101"
     * @return 9位街道代码 → 街道名称 的 Map，无数据时返回空 Map
     */
    public Map<String, String> getStreetData(String districtCode) {
        return streetCache.computeIfAbsent(districtCode, code -> {
            String path = "data/code/" + code + ".json";
            ClassPathResource resource = new ClassPathResource(path);
            if (!resource.exists()) {
                return Collections.emptyMap();
            }
            try (InputStream is = resource.getInputStream()) {
                return objectMapper.readValue(is, new TypeReference<LinkedHashMap<String, String>>() {});
            } catch (IOException e) {
                return Collections.emptyMap();
            }
        });
    }

    public Map<String, String> getCurrentMap() { return currentMap; }
    public Map<String, String> getCurrentMap2() { return currentMap2; }
    public Map<String, String> getHistoryMap() { return historyMap; }
    public Map<String, List<String>> getDiffMap() { return diffMap; }
    public Map<String, List<String>> getReverseDiffMap() { return reverseDiffMap; }
    public Map<String, String> getSearchIndexMap() { return searchIndexMap; }
    public Map<String, String> getSearchIndexMap2() { return searchIndexMap2; }
}
