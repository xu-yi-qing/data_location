package com.example.location.model;

import java.util.List;

/**
 * 身份证号码解析结果
 *
 * <p>示例（签发地为历史代码）：
 * <pre>
 * {
 *   "valid":     true,
 *   "idNumber":  "110103198001010010",
 *   "birthday":  "1980-01-01",
 *   "gender":    "男",
 *   "issuePlace": {
 *     "code":    "110103",
 *     "address": "北京市崇文区",
 *     "current": false
 *   },
 *   "currentPlaces": [
 *     { "code": "110101", "name": "北京市东城区" }
 *   ]
 * }
 * </pre>
 */
public class IdCardResult {

    /** 校验码是否有效（格式不合规时直接为 false，不解析其余字段） */
    private boolean valid;

    /** 原始身份证号码（已转大写） */
    private String idNumber;

    /** 出生日期，格式 yyyy-MM-dd，如 "1990-03-07" */
    private String birthday;

    /** 性别："男" 或 "女" */
    private String gender;

    /** 签发地信息（前6位行政区划代码对应的区划） */
    private IssuePlaceInfo issuePlace;

    /**
     * 现行归属地列表。
     * 仅当签发地代码已被撤销（{@code issuePlace.current == false}）时有值，
     * 表示该历史代码现在归属于哪些现行区划。
     */
    private List<RegionNode> currentPlaces;

    /**
     * 签发地详情，包含代码、地址名称和是否为现行有效代码。
     */
    public static class IssuePlaceInfo {

        /** 前6位行政区划代码 */
        private String code;

        /** 从 history.json 拼接的完整地址，如 "北京市东城区" */
        private String address;

        /** 该代码是否为当前有效代码（false 表示已被撤销或合并） */
        private boolean current;

        public IssuePlaceInfo() {}

        public IssuePlaceInfo(String code, String address, boolean current) {
            this.code = code;
            this.address = address;
            this.current = current;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }
        public boolean isCurrent() { return current; }
        public void setCurrent(boolean current) { this.current = current; }
    }

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public IssuePlaceInfo getIssuePlace() { return issuePlace; }
    public void setIssuePlace(IssuePlaceInfo issuePlace) { this.issuePlace = issuePlace; }
    public List<RegionNode> getCurrentPlaces() { return currentPlaces; }
    public void setCurrentPlaces(List<RegionNode> currentPlaces) { this.currentPlaces = currentPlaces; }
}
