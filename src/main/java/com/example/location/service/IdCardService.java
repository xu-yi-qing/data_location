package com.example.location.service;

import com.example.location.data.RegionDataStore;
import com.example.location.model.IdCardResult;
import com.example.location.model.RegionNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 身份证号码解析服务
 *
 * <p>支持第二代居民身份证（18位），解析内容包括：
 * <ul>
 *   <li>格式校验：是否符合18位格式（前17位数字 + 末位数字或X）</li>
 *   <li>校验码验证：使用 GB/T 18149 标准的加权求和算法</li>
 *   <li>出生日期：第7-14位，格式化为 yyyy-MM-dd</li>
 *   <li>性别：第17位奇数为男，偶数为女</li>
 *   <li>签发地：前6位行政区划代码，从 history.json 拼接完整地址名称</li>
 *   <li>现行归属地：若签发地代码已被撤销，通过 diff.json 追溯对应的现行区划</li>
 * </ul>
 */
@Service
public class IdCardService {

    /**
     * 身份证校验码加权因子（对应第1-17位，共17个）。
     * 来源：GB/T 18149《居民身份证号码》标准。
     */
    private static final int[] FACTOR = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    /**
     * 校验码对照表（加权和模11的余数 0-10 对应的校验码字符）。
     * index:  0    1    2    3    4    5    6    7    8    9    10
     * 余数:   0    1    2    3    4    5    6    7    8    9    10
     * 校验码: '1' '0' 'X' '9' '8' '7' '6' '5' '4' '3' '2'
     */
    private static final char[] CHECK_DIGIT = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    @Autowired
    private RegionDataStore store;

    /**
     * 解析身份证号码。
     *
     * <p>执行流程：
     * <ol>
     *   <li>格式校验（正则）</li>
     *   <li>校验码验证（加权求和）</li>
     *   <li>提取出生日期（第7-14位）</li>
     *   <li>提取性别（第17位奇偶）</li>
     *   <li>解析签发地（前6位代码 → 历史地址名称）</li>
     *   <li>若签发地为历史代码，查询现行归属地</li>
     * </ol>
     *
     * @param id 18位身份证号码（不区分大小写）
     * @return 解析结果，格式校验不通过时仅返回 valid=false
     */
    public IdCardResult parse(String id) {
        IdCardResult result = new IdCardResult();
        id = id.trim().toUpperCase(); // 统一转大写，兼容小写 x
        result.setIdNumber(id);

        // 格式校验：前17位必须是数字，第18位是数字或X
        if (!id.matches("\\d{17}[\\dX]")) {
            result.setValid(false);
            return result;
        }

        // 校验码验证
        result.setValid(validateCheckDigit(id));

        // 出生日期：第7-14位（yyyyMMdd），格式化为 yyyy-MM-dd
        String bd = id.substring(6, 14);
        result.setBirthday(bd.substring(0, 4) + "-" + bd.substring(4, 6) + "-" + bd.substring(6, 8));

        // 性别：第17位（index 16）奇数为男，偶数为女
        int genderDigit = Character.getNumericValue(id.charAt(16));
        result.setGender(genderDigit % 2 == 1 ? "男" : "女");

        // 签发地：前6位行政区划代码
        String code = id.substring(0, 6);
        Map<String, String> historyMap = store.getHistoryMap();
        String address  = HistoryService.getName(code, historyMap);
        boolean isCurrent = store.getCurrentMap().containsKey(code); // 是否仍为现行有效代码
        result.setIssuePlace(new IdCardResult.IssuePlaceInfo(code, address, isCurrent));

        // 若签发地代码已被撤销，通过 diffMap 追溯现行归属地
        List<RegionNode> currentPlaces = new ArrayList<>();
        if (!isCurrent) {
            List<String> newCodes = store.getDiffMap().getOrDefault(code, null);
            if (newCodes != null) {
                for (String newCode : newCodes) {
                    currentPlaces.add(new RegionNode(newCode, HistoryService.getName(newCode, historyMap)));
                }
            }
        }
        result.setCurrentPlaces(currentPlaces);

        return result;
    }

    /**
     * 验证身份证校验码（第18位）。
     *
     * <p>算法：
     * <ol>
     *   <li>将前17位每位数字乘以对应加权因子（{@link #FACTOR}）并求和</li>
     *   <li>求和结果对11取余，得到 0-10 的余数</li>
     *   <li>从校验码对照表（{@link #CHECK_DIGIT}）按余数取出期望校验码</li>
     *   <li>与身份证第18位字符比较，相等则有效</li>
     * </ol>
     *
     * @param id 已转大写的18位身份证号码
     * @return true 表示校验码正确，false 表示号码无效
     */
    private boolean validateCheckDigit(String id) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += Character.getNumericValue(id.charAt(i)) * FACTOR[i];
        }
        return CHECK_DIGIT[sum % 11] == id.charAt(17);
    }
}
