package com.example.deepfake_fight.data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中国省市数据（省级行政区 → 主要城市）。
 * 用于注册时的"所在地区"选取。
 */
public final class RegionData {

    private RegionData() {}

    /** 省 → 主要城市列表（保持插入顺序） */
    public static final Map<String, String[]> REGIONS = new LinkedHashMap<>();

    static {
        REGIONS.put("北京市", new String[]{"北京市"});
        REGIONS.put("天津市", new String[]{"天津市"});
        REGIONS.put("上海市", new String[]{"上海市"});
        REGIONS.put("重庆市", new String[]{"重庆市"});
        REGIONS.put("河北省", new String[]{"石家庄市", "唐山市", "秦皇岛市", "邯郸市", "保定市", "廊坊市"});
        REGIONS.put("山西省", new String[]{"太原市", "大同市", "阳泉市", "长治市", "临汾市"});
        REGIONS.put("内蒙古自治区", new String[]{"呼和浩特市", "包头市", "赤峰市", "通辽市", "鄂尔多斯市"});
        REGIONS.put("辽宁省", new String[]{"沈阳市", "大连市", "鞍山市", "抚顺市", "锦州市"});
        REGIONS.put("吉林省", new String[]{"长春市", "吉林市", "四平市", "通化市", "延边州"});
        REGIONS.put("黑龙江省", new String[]{"哈尔滨市", "齐齐哈尔市", "大庆市", "牡丹江市", "佳木斯市"});
        REGIONS.put("江苏省", new String[]{"南京市", "苏州市", "无锡市", "常州市", "南通市", "徐州市"});
        REGIONS.put("浙江省", new String[]{"杭州市", "宁波市", "温州市", "嘉兴市", "绍兴市", "金华市"});
        REGIONS.put("安徽省", new String[]{"合肥市", "芜湖市", "蚌埠市", "马鞍山市", "安庆市"});
        REGIONS.put("福建省", new String[]{"福州市", "厦门市", "泉州市", "漳州市", "莆田市"});
        REGIONS.put("江西省", new String[]{"南昌市", "九江市", "赣州市", "景德镇市", "上饶市"});
        REGIONS.put("山东省", new String[]{"济南市", "青岛市", "烟台市", "潍坊市", "临沂市", "淄博市"});
        REGIONS.put("河南省", new String[]{"郑州市", "洛阳市", "开封市", "南阳市", "新乡市", "商丘市"});
        REGIONS.put("湖北省", new String[]{"武汉市", "宜昌市", "襄阳市", "荆州市", "黄冈市"});
        REGIONS.put("湖南省", new String[]{"长沙市", "株洲市", "湘潭市", "衡阳市", "岳阳市"});
        REGIONS.put("广东省", new String[]{"广州市", "深圳市", "珠海市", "佛山市", "东莞市", "中山市"});
        REGIONS.put("广西壮族自治区", new String[]{"南宁市", "柳州市", "桂林市", "北海市", "玉林市"});
        REGIONS.put("海南省", new String[]{"海口市", "三亚市", "儋州市"});
        REGIONS.put("四川省", new String[]{"成都市", "绵阳市", "德阳市", "宜宾市", "南充市", "泸州市"});
        REGIONS.put("贵州省", new String[]{"贵阳市", "遵义市", "六盘水市", "安顺市", "毕节市"});
        REGIONS.put("云南省", new String[]{"昆明市", "曲靖市", "玉溪市", "大理州", "丽江市"});
        REGIONS.put("西藏自治区", new String[]{"拉萨市", "日喀则市", "林芝市"});
        REGIONS.put("陕西省", new String[]{"西安市", "咸阳市", "宝鸡市", "渭南市", "延安市"});
        REGIONS.put("甘肃省", new String[]{"兰州市", "天水市", "嘉峪关市", "酒泉市", "张掖市"});
        REGIONS.put("青海省", new String[]{"西宁市", "海东市"});
        REGIONS.put("宁夏回族自治区", new String[]{"银川市", "石嘴山市", "吴忠市", "固原市"});
        REGIONS.put("新疆维吾尔自治区", new String[]{"乌鲁木齐市", "克拉玛依市", "吐鲁番市", "哈密市", "喀什地区"});
        REGIONS.put("台湾省", new String[]{"台北市", "高雄市", "台中市", "台南市"});
        REGIONS.put("香港特别行政区", new String[]{"香港"});
        REGIONS.put("澳门特别行政区", new String[]{"澳门"});
    }

    /** 获取所有省级行政区名称 */
    public static String[] getProvinces() {
        return REGIONS.keySet().toArray(new String[0]);
    }

    /** 获取某省的城市列表 */
    public static String[] getCities(String province) {
        return REGIONS.get(province);
    }
}
