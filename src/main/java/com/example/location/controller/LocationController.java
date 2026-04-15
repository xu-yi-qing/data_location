package com.example.location.controller;

import com.example.location.model.*;
import com.example.location.service.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 行政区划与身份证 REST 接口
 *
 * <p>所有接口统一返回 {@link ApiResponse} 包装结构：
 * <pre>
 * { "code": 0, "data": ..., "message": null }
 * </pre>
 *
 * <p>接口分为两组：
 * <ul>
 *   <li>{@code /api/regions/**} — 行政区划相关（三级联动、搜索、历史沿革、街道）</li>
 *   <li>{@code /api/idcard/**}  — 身份证号码解析</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class LocationController {

    @Autowired private CascadeService cascadeService;
    @Autowired private SearchService  searchService;
    @Autowired private HistoryService historyService;
    @Autowired private IdCardService  idCardService;

    /**
     * 获取所有省级行政区列表。
     *
     * @return 34个省级区划节点（含港澳台自定义编码）
     */
    @GetMapping("/regions/provinces")
    public ApiResponse<List<RegionNode>> getProvinces() {
        return ApiResponse.ok(cascadeService.getProvinces());
    }

    /**
     * 获取指定省下的市级行政区列表。
     *
     * <p>直辖市（北京/天津/上海/重庆）无市级节点，返回空数组，
     * 前端收到空数组后应直接以省级代码请求 {@link #getDistricts}。
     *
     * @param province 6位省级代码，如 "110000"
     * @return 市级区划列表；直辖市返回空列表
     */
    @GetMapping("/regions/cities")
    public ApiResponse<List<RegionNode>> getCities(@RequestParam String province) {
        return ApiResponse.ok(cascadeService.getCities(province));
    }

    /**
     * 获取指定市下的区县级行政区列表。
     *
     * <p>对于直辖市，传入省级代码（如 "110000"）即可获取该省所有区县。
     *
     * @param city 6位市级代码（如 "130100"）或直辖市的省级代码（如 "110000"）
     * @return 区县级区划列表
     */
    @GetMapping("/regions/districts")
    public ApiResponse<List<RegionNode>> getDistricts(@RequestParam String city) {
        return ApiResponse.ok(cascadeService.getDistricts(city));
    }

    /**
     * 获取指定区县下的街道/镇/乡列表。
     *
     * <p>数据来自 {@code resources/data/code/<districtCode>.json}，首次请求时加载并缓存。
     *
     * @param district 6位区县代码，如 "110101"
     * @return 街道级区划列表（9位代码），无数据时返回空列表
     */
    @GetMapping("/regions/streets")
    public ApiResponse<List<RegionNode>> getStreets(@RequestParam String district) {
        return ApiResponse.ok(cascadeService.getStreets(district));
    }

    /**
     * 行政区划模糊搜索。
     *
     * <p>支持去除行政后缀（省/市/区/县等）和民族名称后进行智能匹配，
     * 结果按父级去重（省级已命中时不返回其下属市县）。
     *
     * @param q      搜索关键词，如 "朝阳"、"西湖区"、"浙江杭州"
     * @param limit  最多返回条数，默认10，最大20
     * @param source 数据源，"list"（默认）或 "list2"（含管理区节点）
     * @return 搜索结果列表，每条含 code、name、fullName（完整省市区路径）
     */
    @GetMapping("/regions/search")
    public ApiResponse<List<SearchResult>> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "list") String source) {
        limit = Math.min(limit, 20);
        boolean useList2 = "list2".equals(source);
        return ApiResponse.ok(searchService.query(q, limit, useList2));
    }

    /**
     * 查询指定区划代码的历史沿革。
     *
     * <p>返回当前名称（从 history.json 拼接）及所有历史前身代码和名称（从 diff.json 反向索引获取）。
     *
     * @param code 6位行政区划代码，如 "110101"
     * @return 历史沿革结果，包含 current（当前）和 predecessors（历史前身列表）
     */
    @GetMapping("/regions/history/{code}")
    public ApiResponse<HistoryResult> getHistory(@PathVariable String code) {
        return ApiResponse.ok(historyService.getHistory(code));
    }

    /**
     * 解析身份证号码（GET 方式）。
     *
     * @param id 18位身份证号码
     * @return 解析结果，含有效性、出生日期、性别、签发地、现行归属地
     */
    @GetMapping("/idcard/parse")
    public ApiResponse<IdCardResult> parseGet(@RequestParam String id) {
        return ApiResponse.ok(idCardService.parse(id));
    }

    /**
     * 解析身份证号码（POST 方式）。
     *
     * <p>请求体示例：{@code {"id": "110101199003077512"}}
     *
     * @param body 包含 "id" 字段的 JSON 请求体
     * @return 解析结果，同 GET 方式
     */
    @PostMapping("/idcard/parse")
    public ApiResponse<IdCardResult> parsePost(@RequestBody Map<String, String> body) {
        return ApiResponse.ok(idCardService.parse(body.getOrDefault("id", "")));
    }
}
