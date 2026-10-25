package com.example.deepfake_fight.service;

import android.graphics.Color;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.UnderlineSpan;
import android.text.style.StyleSpan;
import android.graphics.Typeface;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文本反诈检测可视化标注器
 *
 * 为检测文本生成带颜色高亮的可视化效果：
 * - 诈骗关键词用不同颜色背景高亮（红/橙/黄按风险等级）
 * - 可疑短语加下划线
 * - 生成风险色条（横向条状图显示各类别得分）
 * - 输出带标注的HTML格式文案供UI展示
 *
 * 技术方案：基于现有 LocalTextDetector 的关键词库和评分逻辑，
 * 在原文上直接做 Spannable 标注，无需额外模型。
 */
public class TextVisualAnnotator {

    // 关键词分类及其风险权重（与 LocalTextDetector 保持一致）
    private static final String[][] FRAUD_CATEGORIES = {
        {"紧急催促", "马上", "立即", "快", "紧急", "速", "迅速", "尽快"},
        {"资金操作", "转账", "汇款", "打款", "付款", "支付", "缴费"},
        {"冒充公检法", "警察", "公安", "法院", "检察院", "逮捕", "通缉", "冻结", "洗钱", "涉案"},
        {"冒充熟人", "领导", "老板", "李总", "王总", "客服", "老师"},
        {"刷单/投资", "刷单", "返利", "返现", "兼职", "赚钱", "投资", "理财", "股票"},
        {"敏感信息", "安全账户", "保证金", "验证码", "密码", "身份证号", "银行卡号"},
        {"虚假营销", "恭喜", "中奖", "幸运", "名额有限", "最后一天"},
    };

    // 各类别颜色：背景色 + 文字色
    private static final int[] CATEGORY_BG_COLORS = {
        0x44FF5252,  // 紧急催促 — 浅红
        0x44FF6D00,  // 资金操作 — 浅橙
        0x44FF1744,  // 冒充公检法 — 深红
        0x44FF9100,  // 冒充熟人 — 橙色
        0x44FFD600,  // 刷单/投资 — 黄色
        0x44FF4081,  // 敏感信息 — 粉色
        0x4490CAF9,  // 虚假营销 — 浅蓝
    };

    private static final int[] CATEGORY_TEXT_COLORS = {
        0xFFD32F2F,  // 紧急催促
        0xFFE65100,  // 资金操作
        0xFFC62828,  // 冒充公检法
        0xFFEF6C00,  // 冒充熟人
        0xFFF9A825,  // 刷单/投资
        0xFFC2185B,  // 敏感信息
        0xFF1565C0,  // 虚假营销
    };

    /**
     * 标注结果
     */
    public static class TextAnnotation {
        public SpannableString spannableText;    // 带标注的文本（用于TextView直接显示）
        public StringBuilder htmlText;            // HTML格式（备选）
        public List<MatchInfo> allMatches;        // 所有匹配项
        public Map<String, Float> categoryScores; // 各类别得分
        public float overallScore;                // 综合风险分
        public String riskBarText;                // 风险色条（Unicode字符画）
        public String summary;                    // 分析摘要

        public static class MatchInfo {
            public String keyword;
            public int categoryIndex;
            public String categoryName;
            public int startPos;
            public int endPos;
            public float weight;
        }
    }

    /**
     * 对文本进行可视化标注分析
     *
     * @param text       原始文本
     * @param riskScore  模型/规则给出的综合风险分 (0~1)
     * @return 包含标注文本和详细分析结果的 TextAnnotation
     */
    public TextAnnotation analyze(String text, float riskScore) {
        TextAnnotation annotation = new TextAnnotation();
        annotation.allMatches = new ArrayList<>();
        annotation.categoryScores = new HashMap<>();
        annotation.overallScore = riskScore;

        if (text == null || text.trim().isEmpty()) {
            annotation.spannableText = new SpannableString("(空文本)");
            annotation.htmlText = new StringBuilder("<p>(空文本)</p>");
            annotation.riskBarText = "";
            annotation.summary = "输入文本为空";
            return annotation;
        }

        // 第一步：扫描所有关键词匹配
        SpannableString spannable = new SpannableString(text);

        for (int catIdx = 0; catIdx < FRAUD_CATEGORIES.length; catIdx++) {
            String[] keywords = FRAUD_CATEGORIES[catIdx];
            // 跳过类别名（第一个元素是类别名）
            for (int kwIdx = 1; kwIdx < keywords.length; kwIdx++) {
                String keyword = keywords[kwIdx];
                int searchFrom = 0;
                while (searchFrom < text.length()) {
                    int pos = text.indexOf(keyword, searchFrom);
                    if (pos < 0) break;

                    float weight = getKeywordWeight(catIdx);

                    // 记录匹配
                    TextAnnotation.MatchInfo match = new TextAnnotation.MatchInfo();
                    match.keyword = keyword;
                    match.categoryIndex = catIdx;
                    match.categoryName = keywords[0];  // 类别名
                    match.startPos = pos;
                    match.endPos = pos + keyword.length();
                    match.weight = weight;
                    annotation.allMatches.add(match);

                    // 应用颜色标注
                    spannable.setSpan(
                            new BackgroundColorSpan(CATEGORY_BG_COLORS[catIdx]),
                            pos, pos + keyword.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    spannable.setSpan(
                            new ForegroundColorSpan(CATEGORY_TEXT_COLORS[catIdx]),
                            pos, pos + keyword.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    spannable.setSpan(
                            new StyleSpan(Typeface.BOLD),
                            pos, pos + keyword.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    );

                    searchFrom = pos + keyword.length();
                }
            }

            // 计算各类别得分
            float catScore = 0;
            for (TextAnnotation.MatchInfo m : annotation.allMatches) {
                if (m.categoryIndex == catIdx) {
                    catScore += m.weight;
                }
            }
            catScore = Math.min(catScore, 1.0f);
            annotation.categoryScores.put(keywords[0], catScore);
        }

        annotation.spannableText = spannable;

        // 第二步：生成HTML格式（备选）
        annotation.htmlText = buildHtml(text, annotation);

        // 第三步：生成风险色条
        annotation.riskBarText = buildRiskBar(annotation.categoryScores, riskScore);

        // 第四步：生成分析摘要
        annotation.summary = buildSummary(text, annotation, riskScore);

        return annotation;
    }

    /**
     * 获取某类关键词的权重（与 LocalTextDetector.KEYWORD_WEIGHTS 一致）
     */
    private float getKeywordWeight(int categoryIndex) {
        float[] weights = {0.15f, 0.25f, 0.25f, 0.15f, 0.2f, 0.3f, 0.1f};
        return categoryIndex < weights.length ? weights[categoryIndex] : 0.1f;
    }

    /**
     * 生成HTML格式标注文本
     */
    private StringBuilder buildHtml(String text, TextAnnotation annotation) {
        // 按位置排序
        List<TextAnnotation.MatchInfo> sorted = new ArrayList<>(annotation.allMatches);
        sorted.sort((a, b) -> Integer.compare(a.startPos, b.startPos));

        StringBuilder html = new StringBuilder();
        html.append("<div style='font-size:14px; line-height:1.8;'>");

        int lastEnd = 0;
        for (TextAnnotation.MatchInfo m : sorted) {
            // 添加匹配前的正常文本
            if (m.startPos > lastEnd) {
                html.append(escapeHtml(text.substring(lastEnd, m.startPos)));
            }
            // 添加高亮关键词
            String bgColor = String.format("#%06X", CATEGORY_BG_COLORS[m.categoryIndex] | 0xFF000000);
            bgColor = bgColor.substring(0, 7); // 取RGB部分
            html.append(String.format(
                    "<mark style='background-color:%s; font-weight:bold;' title='%s: %s'>%s</mark>",
                    bgColor.substring(0, 7),
                    m.categoryName,
                    String.format("%.0f%%", m.weight * 100),
                    escapeHtml(m.keyword)
            ));
            lastEnd = m.endPos;
        }
        // 剩余文本
        if (lastEnd < text.length()) {
            html.append(escapeHtml(text.substring(lastEnd)));
        }

        html.append("</div>");
        return html;
    }

    /**
     * 生成Unicode风险色条
     */
    private String buildRiskBar(Map<String, Float> categoryScores, float overallScore) {
        StringBuilder bar = new StringBuilder();

        // 整体风险条
        bar.append("📊 综合风险: ");
        int filledBlocks = Math.round(overallScore * 10);
        for (int i = 0; i < 10; i++) {
            bar.append(i < filledBlocks ? "█" : "░");
        }
        bar.append(String.format(" %.0f%%\n\n", overallScore * 100));

        // 各类别风险条
        String[] icons = {"⏰", "💸", "👮", "👤", "📈", "🔑", "📢"};
        for (int i = 0; i < FRAUD_CATEGORIES.length; i++) {
            String catName = FRAUD_CATEGORIES[i][0];
            Float score = categoryScores.get(catName);
            if (score == null) score = 0f;

            bar.append(icons[i]).append(" ").append(catName).append(": ");
            filledBlocks = Math.round(score * 10);
            for (int j = 0; j < 10; j++) {
                bar.append(j < filledBlocks ? "▓" : "░");
            }
            bar.append(String.format(" %.0f%%\n", score * 100));
        }

        return bar.toString();
    }

    /**
     * 生成分析摘要
     */
    private String buildSummary(String text, TextAnnotation annotation, float riskScore) {
        StringBuilder sb = new StringBuilder();

        int totalMatches = annotation.allMatches.size();
        sb.append(String.format("📝 文本长度: %d字 | 命中关键词: %d个\n\n", text.length(), totalMatches));

        // TOP 3 高风险类别
        List<Map.Entry<String, Float>> sorted = new ArrayList<>(annotation.categoryScores.entrySet());
        sorted.sort((a, b) -> Float.compare(b.getValue(), a.getValue()));

        sb.append("⚠ 风险类别 TOP 3:\n");
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            String icon = sorted.get(i).getValue() > 0.5f ? "🔴" :
                    sorted.get(i).getValue() > 0.2f ? "🟡" : "🟢";
            sb.append(String.format("  %s %s: %.0f%%\n",
                    icon, sorted.get(i).getKey(), sorted.get(i).getValue() * 100));
        }

        if (riskScore > 0.6f) {
            sb.append("\n⚠ 文本包含明显诈骗特征，请高度警惕！");
        } else if (riskScore > 0.3f) {
            sb.append("\n🔍 文本存在可疑关键词，建议进一步核实");
        } else {
            sb.append("\n✅ 未发现明显诈骗特征");
        }

        return sb.toString();
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\n", "<br>");
    }
}
