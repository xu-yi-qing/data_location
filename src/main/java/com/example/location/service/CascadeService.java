package com.example.location.service;

import com.example.location.data.RegionDataStore;
import com.example.location.model.RegionNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 三级联动服务
 *
 * <p>提供省→市→区县→街道四级行政区划的逐级查询，数据来源于 {@link RegionDataStore#getCurrentMap()}。
 *
 * <p>直辖市（北京11、天津12、上海31、重庆50）特殊处理：
 * GB/T2260 中直辖市没有独立的地级市节点，省级代码直接对应区县级，
 * 因此 {@link #getCities} 对直辖市返回空列表，
 * 前端收到空列表后应直接以省级代码请求 {@link #getDistricts}。
 */
@Service
public class CascadeService {

    /**
     * 直辖市的省级代码前两位集合。
     * 这些省份在 list.json 中没有 XXXX00 形式的市级节点。
     */
    private static final Set<String> DIRECT_PROV = new HashSet<>(Arrays.asList("11", "12", "31", "50"));

    @Autowired
    private RegionDataStore store;

    /**
     * 获取所有省级行政区列表（6位代码末4位为0000的条目）。
     *
     * @return 省级 RegionNode 列表，按 list.json 中的原始顺序排列
     */
    public List<RegionNode> getProvinces() {
        return store.getCurrentMap().entrySet().stream()
            .filter(e -> e.getKey().matches("\\d{2}0000"))
            .map(e -> new RegionNode(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * 获取指定省下的市级行政区列表。
     *
     * <p>对直辖市（11/12/31/50）返回空列表，前端应直接调用 {@link #getDistricts} 并传入省级代码。
     *
     * @param provinceCode 6位省级代码，如 "130000"
     * @return 市级 RegionNode 列表；直辖市返回空列表
     */
    public List<RegionNode> getCities(String provinceCode) {
        String prefix = provinceCode.substring(0, 2);
        // 直辖市无市级节点，直接返回空列表
        if (DIRECT_PROV.contains(prefix)) {
            return Collections.emptyList();
        }
        // 过滤出该省前缀 + 末2位为00 + 末4位非0000 的条目（即市级）
        return store.getCurrentMap().entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix)
                      && e.getKey().endsWith("00")
                      && !e.getKey().endsWith("0000"))
            .map(e -> new RegionNode(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * 获取指定市下的区县级行政区列表。
     *
     * <p>兼容直辖市：若传入省级代码（末4位为0000），则取该省前两位作为前缀，
     * 返回该省下所有区县（不以"00"结尾的6位代码）。
     *
     * @param cityCode 6位市级代码（如 "130100"）或直辖市的省级代码（如 "110000"）
     * @return 区县级 RegionNode 列表
     */
    public List<RegionNode> getDistricts(String cityCode) {
        String prefix;
        if (cityCode.endsWith("0000")) {
            // 直辖市场景：传入的是省级代码，取前两位作为过滤前缀
            prefix = cityCode.substring(0, 2);
        } else {
            // 普通市：取前四位作为过滤前缀
            prefix = cityCode.substring(0, 4);
        }
        // 过滤出该前缀下不以"00"结尾的条目（即区县级）
        return store.getCurrentMap().entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix) && !e.getKey().endsWith("00"))
            .map(e -> new RegionNode(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }

    /**
     * 获取指定区县下的街道/镇/乡列表，数据来自 code/ 目录下对应的 JSON 文件。
     *
     * @param districtCode 6位区县代码，如 "110101"
     * @return 街道级 RegionNode 列表（9位代码 + 街道名），无数据时返回空列表
     */
    public List<RegionNode> getStreets(String districtCode) {
        return store.getStreetData(districtCode).entrySet().stream()
            .map(e -> new RegionNode(e.getKey(), e.getValue()))
            .collect(Collectors.toList());
    }
}
