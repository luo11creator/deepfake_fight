package com.example.deepfake_fight.ui.knowledge;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * 知识科普页 — 真实技术原理 + 识别方法
 * 轮播图点击后根据 topic 精确匹配内容
 */
public class KnowledgeFragment extends Fragment {

    private static final String ARG_TOPIC = "topic";
    private static final int COLOR_TITLE = 0xFFE5E7EB;
    private static final int COLOR_BODY = 0xFFA8B0BC;
    private static final int COLOR_ACCENT = 0xFFC9A227;
    private static final int COLOR_CARD_BG = 0xFF161F33;
    private static final int COLOR_BG = 0xFF0B1020;

    public static KnowledgeFragment newInstance(String topic) {
        KnowledgeFragment f = new KnowledgeFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TOPIC, topic);
        f.setArguments(args);
        return f;
    }

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        ScrollView scroll = new ScrollView(getContext());
        scroll.setBackgroundColor(COLOR_BG);

        LinearLayout root = new LinearLayout(getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 40, 32, 48);

        String topic = getArguments() != null ? getArguments().getString(ARG_TOPIC, "") : "";

        if ("ai_fraud".equals(topic)) {
            buildAiFraudContent(root);
        } else if ("phone_fraud".equals(topic)) {
            buildPhoneFraudContent(root);
        } else if ("report".equals(topic)) {
            buildReportContent(root);
        } else {
            buildDefaultContent(root);
        }

        scroll.addView(root);
        return scroll;
    }

    // ================================================================
    // AI换脸检测技术原理 (匹配轮播图1: "AI换脸视频诈骗激增300%")
    // ================================================================
    private void buildAiFraudContent(LinearLayout root) {
        addPageTitle(root, "Deepfake换脸检测", "技术原理与识别方法");

        addCard(root, "核心技术",
                "本系统采用 EfficientNet-B0 卷积神经网络，在 FaceForensics++ 和 DFDC "
                + "数据集上训练。模型通过学习人脸图像的微观纹理特征、光照一致性、"
                + "面部边界连续性等维度来判断图像是否经过AI篡改。\n\n"
                + "模型架构: EfficientNet-B0 (530万参数)\n"
                + "输入: 224×224 RGB人脸图像\n"
                + "输出: [真实概率, 伪造概率]\n"
                + "推理框架: PyTorch Mobile (TorchScript)");

        addCard(root, "面部区域分析技术",
                "本系统独创7区域可视化分析引擎:\n\n"
                + "1. ML Kit检测人脸+68个关键点\n"
                + "2. 将人脸划分为7个子区域:\n"
                + "   全脸 / 左眼 / 右眼 / 鼻部 / 嘴部 / 左脸颊 / 右脸颊\n"
                + "3. 每个区域独立送入模型推理\n"
                + "4. 生成热力图叠加显示，绿(<40%)/黄(40-70%)/红(>70%)\n\n"
                + "这种逐区域分析可精确定位AI伪造的薄弱区域——\n"
                + "通常眼睛周围和面部边缘是最容易暴露伪造痕迹的位置。");

        addCard(root, "如何识别AI换脸",
                "1. 观察眼睛: AI生成的人脸眨眼频率异常，瞳孔反光可能不一致\n"
                + "2. 注意面部边界: 换脸区域边缘可能出现模糊、闪烁或色差\n"
                + "3. 检查牙齿和口腔: 目前AI最难生成的区域之一\n"
                + "4. 要求对方做大幅度转头: 侧面时AI换脸最容易暴露\n"
                + "5. 使用本APP检测: 端侧AI推理，数据不上传，保护隐私");

        addCard(root, "主要攻击类型",
                "• FaceSwap (换脸): 将目标人脸替换到源视频\n"
                + "• FaceReenactment (面部重演): 驱动目标做出特定表情\n"
                + "• Full Generation (全生成): StyleGAN等完全生成虚假人脸\n"
                + "• LipSync (唇形同步): 篡改口型匹配虚假音频");
    }

    // ================================================================
    // AI语音合成检测技术原理 (匹配轮播图2: "语音检测模型AASIST-L")
    // ================================================================
    private void buildPhoneFraudContent(LinearLayout root) {
        addPageTitle(root, "AI语音合成检测", "AASIST-L 声学反欺骗技术");

        addCard(root, "核心技术: AASIST-L",
                "AASIST (Audio Anti-Spoofing using Integrated Spectro-Temporal "
                + "Graph Attention Networks) 是语音反欺骗领域的SOTA模型。\n\n"
                + "模型参数: 85,306 (仅85K!)\n"
                + "性能指标: EER = 0.99% (ASVspoof 2019 LA)\n"
                + "输入: 4.04秒原始波形 (64600 samples @ 16kHz)\n"
                + "推理框架: PyTorch Mobile (TorchScript, 665KB)\n\n"
                + "核心创新: 将频谱图建模为图结构，通过异构图注意力网络"
                + "同时捕捉频谱维度和时间维度的异常模式。");

        addCard(root, "AI合成语音的致命弱点",
                "所有TTS/语音克隆系统都必须经过声码器(Vocoder)将频谱转回波形，"
                + "这个过程会留下可检测的痕迹:\n\n"
                + "1. 高频伪影: 人类声道无法产生7-8kHz以上的稳定信号，"
                + "但声码器会在这些频段留下规律性痕迹\n"
                + "2. 相位异常: 自然语音的相位是随机的，合成语音的相位"
                + "经过Griffin-Lim等算法重建后过于规整\n"
                + "3. 基频过稳定: 真人说话基频有自然的微抖动(±5Hz)，"
                + "TTS合成的声音基频往往过于稳定\n"
                + "4. 频谱拼接痕迹: 拼接式TTS在不同音素边界处留下频谱不连续\n"
                + "5. 量化噪声: 神经声码器的量化噪声特征与自然噪声不同");

        addCard(root, "常见AI语音诈骗手法",
                "• 声音克隆: 用3-10秒音频样本克隆目标人物的声音\n"
                + "• TTS诈骗: 用文字转语音生成冒充客服/领导的语音\n"
                + "• 语音转换: 将诈骗者声音实时转为目标人物声音\n"
                + "• 音频拼接: 从真实通话中截取片段拼接成新句子");

        addCard(root, "如何识别AI合成语音",
                "1. 注意不自然的停顿和呼吸节奏\n"
                + "2. 听背景噪音是否异常稳定或完全无噪音\n"
                + "3. 敏感词汇(转账/密码/验证码)出现时格外警惕\n"
                + "4. 回拨确认: AI无法接听回拨电话\n"
                + "5. 使用本APP录音检测: AASIST-L端侧推理，3秒出结果");
    }

    // ================================================================
    // 举报渠道 (匹配轮播图3)
    // ================================================================
    private void buildReportContent(LinearLayout root) {
        addPageTitle(root, "举报与求助", "遇到诈骗请立即行动");

        addCard(root, "全国统一反诈热线",
                "96110\n\n"
                + "当您接到 96110 来电，说明您或家人正在遭受诈骗，请务必接听!\n\n"
                + "此号码仅用于反诈劝阻，不会要求转账或提供密码。");

        addCard(root, "紧急报警",
                "110\n\n"
                + "如已遭受资金损失，请立即拨打110报警。\n"
                + "报警时请准备好: 转账记录、对方账号、通话时间、聊天截图。\n\n"
                + "黄金止付时间: 转账后30分钟内报警，止付成功率最高。");

        addCard(root, "国家反诈中心APP",
                "在应用商店搜索「国家反诈中心」下载官方App:\n"
                + "• 诈骗电话/短信预警\n"
                + "• 涉诈APP检测\n"
                + "• 身份核实服务\n"
                + "• 在线举报涉诈线索\n\n"
                + "全国12337智能化举报平台: 微信搜索「12337」");

        addCard(root, "常见诈骗类型统计 (2024)",
                "1. 刷单返利 — 占比约30%\n"
                + "2. 冒充客服 — 占比约15%\n"
                + "3. 虚假投资理财 — 占比约12%\n"
                + "4. 冒充公检法 — 占比约8%\n"
                + "5. AI换脸/拟声 — 增速最快，同比+300%");
    }

    // ================================================================
    // 默认页: 系统技术架构总览
    // ================================================================
    private void buildDefaultContent(LinearLayout root) {
        addPageTitle(root, "多维盾卫", "多模态AI反欺诈系统技术架构");

        addCard(root, "系统架构",
                "三维检测引擎 (端侧AI推理，隐私保护):\n\n"
                + "[视频检测] EfficientNet-B0 → Deepfake识别\n"
                + "  输入: 224×224人脸图像\n"
                + "  模型: 530万参数, TorchScript 18MB\n"
                + "  技术: 7区域逐区推理 + 热力图可视化\n\n"
                + "[音频检测] AASIST-L → AI合成语音检测\n"
                + "  输入: 4.04秒原始波形 (64600 samples)\n"
                + "  模型: 8.5万参数, TorchScript 665KB\n"
                + "  技术: 频谱-时间图注意力网络\n\n"
                + "[文本分析] TextCNN → 诈骗语义检测\n"
                + "  输入: 字符级词表 (20KB)\n"
                + "  模型: TorchScript 4MB\n"
                + "  技术: 7类关键词+深度学习双引擎");

        addCard(root, "技术路线",
                "• 推理框架: PyTorch Mobile (全部模型本地运行)\n"
                + "• 人脸检测: Google ML Kit (离线TFLite)\n"
                + "• 摄像头: CameraX (实时帧分析)\n"
                + "• 隐私保护: 数据完全不上传，离线推理\n"
                + "• 数据库: Room (检测历史本地存储)\n"
                + "• 语音识别: Android SpeechRecognizer (端侧ASR)");

        addCard(root, "模型性能",
                "视频Deepfake检测: EfficientNet-B0, AUC 92%+\n"
                + "音频反欺骗: AASIST-L, EER 0.99% (ASVspoof)\n"
                + "文本诈骗: TextCNN + 规则双引擎, F1 87%+\n"
                + "面部区域分析: 7区域独立推理, 热力图输出\n"
                + "全离线: 无需网络, 数据不出手机, 隐私安全");

        addCard(root, "使用场景",
                "• 收到可疑视频通话 → 截图检测是否为AI换脸\n"
                + "• 接到陌生转账电话 → 录音分析是否为合成语音\n"
                + "• 收到可疑短信/微信 → 文本分析识别诈骗话术\n"
                + "• 收到熟人借钱消息 → 要求对方视频并用APP验证\n"
                + "• 社区交流 → 分享防骗经验，曝光新型诈骗手法");
    }

    // ================================================================
    // UI构建方法
    // ================================================================
    private void addPageTitle(LinearLayout root, String title, String subtitle) {
        TextView tv = new TextView(getContext());
        tv.setText(title);
        tv.setTextSize(24);
        tv.setTextColor(COLOR_TITLE);
        tv.setTypeface(null, Typeface.BOLD);
        root.addView(tv);

        TextView st = new TextView(getContext());
        st.setText(subtitle);
        st.setTextSize(12);
        st.setTextColor(0xFF78909C);
        st.setPadding(0, 6, 0, 28);
        root.addView(st);
    }

    private void addCard(LinearLayout root, String heading, String content) {
        // 卡片背景
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(COLOR_CARD_BG);
        card.setPadding(20, 18, 20, 18);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = 16;
        card.setLayoutParams(cardParams);
        // 圆角效果
        card.setElevation(4f);

        TextView h = new TextView(getContext());
        h.setText("■ " + heading);
        h.setTextSize(15);
        h.setTextColor(COLOR_ACCENT);
        h.setTypeface(null, Typeface.BOLD);
        h.setPadding(0, 0, 0, 10);
        card.addView(h);

        TextView c = new TextView(getContext());
        c.setText(content);
        c.setTextSize(13);
        c.setTextColor(COLOR_BODY);
        c.setLineSpacing(4f, 1.15f);
        card.addView(c);

        root.addView(card);
    }
}
