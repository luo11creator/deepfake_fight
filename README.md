# FlareGuard｜多维盾卫 — 端侧轻量化多模态深度伪造反诈监测系统

> **Lightweight On‑device Multimodal Deepfake Anti‑Fraud Monitoring System** AI + 软件赛道｜端云协同多模态反诈工具，以 AI 识破 AI 诈骗

<p align="center"> <img src="https://img.shields.io/badge/Python-3.10+-blue" /> <img src="https://img.shields.io/badge/Android-Java‑PyTorchMobile‑orange" /> <img src="https://img.shields.io/badge/Framework‑FastAPI‑green" /> <img src="https://img.shields.io/badge/Model‑EfficientNet‑AASIST‑TextCNN‑GuardFusion‑red" /> <img src="https://img.shields.io/badge/On‑device‑Inference‑success" /> </p>

>  仓库分为两部分：
>
> - `deepfake_fight`：Android 移动端 App 源码
> - `backend`：FastAPI 后端服务源码

------

##  目录

- [项目简介](#项目简介)
- [研究背景与痛点](#研究背景与痛点)
- [系统总体设计](#系统总体设计)
- 核心算法与关键技术
  - [音频 AI 合成语音检测算法](#音频ai合成语音检测算法)
  - [视频换脸检测算法](#视频换脸检测算法)
  - [文本诈骗识别算法](#文本诈骗识别算法)
  - [GuardFusion 多模态融合检测](#guardfusion多模态融合检测算法)
  - [换脸教育演示技术](#换脸教育演示技术)
- 系统实现
  - [后端服务实现](#后端服务实现)
  - [移动端实现](#移动端实现)
  - [实时监测能力](#实时监测能力)
  - [反诈智能体](#反诈智能体)
  - [3D 反诈数据地图](#3d反诈数据地图)
  - [可视化分析引擎](#可视化分析引擎)
- [项目文件说明](#项目文件说明)
- [快速启动](#快速启动)
- [引用参考](#引用参考)

------

## 项目简介

近年来深度伪造（Deepfake）技术快速普及，AI 换脸、声音克隆、大模型诈骗话术带来新型电信网络诈骗威胁。**多维盾卫（FlareGuard）** 是一套**端云协同多模态深度伪造反诈检测系统**。


<img width="700" height="416" alt="图片1" src="https://github.com/user-attachments/assets/e25202d6-81a7-44a1-8c48-186ad89a96b8" />

项目将**视频换脸检测、音频 AI 拟声鉴伪、文本诈骗识别**三类能力集成，核心检测在 Android 端本地离线推理，保护用户人脸、声纹隐私；自研多模态融合模型`GuardFusion`实现跨模态矛盾识别；同时提供可视化可解释报告、通话实时检测、录屏检测、反诈智能体、换脸科普演示等功能。

> 核心亮点

1.  **端侧离线推理**：音视频检测本地完成，不上传原始敏感数据，保护隐私，弱网可用
2. **多模态融合 GuardFusion**：视觉 + 音频 + 文本联合研判，识别跨模态复合型诈骗
3.  **三类实时监测**：通话麦克风实时鉴伪、录屏视频通话换脸检测、摄像头对准即检测
4. **可解释检测**：人脸区域热力遮挡分析、音频分段分析、诈骗关键词高亮
5. **反诈智能体**：基于大模型 Function‑Calling，调用检测工具完成诈骗研判
6.  **科普演示**：换脸演示功能，提升大众反诈意识

赛题定位：**AI + 软件赛道，人工智能算法面向公共安全与社会治理创新应用，算法创新 + 完整软件落地**。

------

## 研究背景与痛点

### 1. 深度伪造技术快速迭代，AI 诈骗威胁严峻

以 Deepfake 为代表生成式 AI 可以生成肉眼难以分辨伪造图像、克隆语音、批量生成诈骗话术，催生：AI 换脸视频通话诈骗、AI 拟声冒充亲友、伪造公检法语音、AI 钓鱼短信等新型诈骗。

- 换脸诈骗：人脸特征替换，视频通话伪造熟人
- 拟声诈骗：TTS 声音克隆，数秒录音复刻音色
- 文本诈骗：LLM 生成个性化诈骗话术
- 复合型诈骗：换脸视频 + 克隆语音 + 伪造话术叠加，防范难度极大

### 2. 反深伪检测技术研究现状

学术界工业界三大技术路线：

1. **视觉换脸检测**：基于伪影、频域、卷积网络；多数只输出真假概率，可解释性弱
2. **语音反欺骗**：ASVspoof 竞赛基线，AASIST 为当前主流鉴伪模型
3. **文本诈骗识别**：关键词规则、TextCNN/LSTM；大模型推理成本高，难端侧部署

整体趋势：**多模态融合、端侧轻量化、可解释化，为本项目立足点**。

### 3. 当前现有方案四大痛点

1. **检测能力不足**：多为单模态研究原型，缺少面向普通用户完整工具，无法覆盖多形态诈骗
2. **隐私时效矛盾**：云端方案需要上传人脸声纹，存在隐私泄露；网络差无法实时，诈骗多发生实时通话
3. **结果不可解释**：仅输出真假，用户不知道 “哪里可疑”，缺少反诈教育
4. **产品事后检测**：市面产品多需要手动录制上传，无法做到通话过程事中实时预警

<img width="1007" height="557" alt="Snipaste_2026-10-25_23-33-30" src="https://github.com/user-attachments/assets/90f1767a-ea7e-4df3-a37b-adb89b149c51" />


👉 本项目目标：打造端侧优先、多模态协同、可解释、支持实时监测的随身反诈系统。

------

## 系统总体设计

### 设计目标

1. **多模态覆盖**：支持图片、视频、音频、文本多类输入，覆盖主流深伪诈骗形态
2. **隐私优先**：核心检测能力移动端离线推理，原始敏感数据不上云端
3. **实时高效**：模型轻量化转换内置 App，秒级推理，适配通话实时场景
4. **可解释可视化**：热力图、关键词高亮、分段分析，展示可疑位置与原因
5. **端云协同**：端侧高频轻量检测；云端承担换脸演示、报告生成、管理面板重型任务。

### 系统总体架构

两层架构：**移动端 Android App + Python FastAPI 后端**

表格

| 架构层级 | 移动端 App（Android + Java）                                 | 后端服务（Python + FastAPI）                                |
| -------- | ------------------------------------------------------------ | ----------------------------------------------------------- |
| 表现层   | 引导 / 首页 / 检测 / 结果 / 历史 / 社区 / 知识库             | Web 仪表盘、管理面板、Swagger 交互式文档                    |
| 服务层   | LocalVideoDetector / LocalAudioDetector / LocalTextDetector / FaceRegionAnalyzer / TextVisualAnnotator / ApiService(OkHttp) | 音频 / 视频 / 文本 / 换脸接口；健康检查；结果查询；统计计数 |
| 算法层   | PyTorch‑Mobile：EfficientNet‑B0 / AASIST / TextCNN；ML‑Kit 人脸检测 | AASIST 鉴伪 + Whisper 转写 + InsightFace                    |
| 数据层   | Room 数据库 5 张表 + SharedPreferences                       | 模型权重、可视化报告、样例音频、任务存储                    |

> 移动端：用户入口，主要离线检测引擎，模型打包进 APK assets 目录。 后端：云端扩展能力，内网穿透对外提供服务。

### 端云协同运行模式

1. **离线端侧模式（默认核心）**：视频、音频、文本全部手机本地推理，**不需要网络，保护隐私**。
2. **云端扩展模式**：执行换脸科普演示、生成高清可视化报告，App 上传文件调用后端接口。

### 各模态检测配置

表格

| 模态        | 检测任务        | 端侧模型        | 云端能力                | 关键技术                         |
| ----------- | --------------- | --------------- | ----------------------- | -------------------------------- |
| 视频 / 图像 | 换脸检测        | EfficientNet‑B0 | —                       | 人脸七区域遮挡分析、热力图可视化 |
| 音频        | AI 合成语音检测 | AASIST          | AASIST + Whisper        | 分段聚合、双模型融合、报告可视化 |
| 文本        | 诈骗话术识别    | TextCNN         | 关键词规则引擎          | 字级嵌入、关键词高亮标注         |
| 演示        | 换脸科普        | —               | InsightFace + inswapper | 人脸检测、人脸特征替换           |

### 整体技术路线

`数据输入 → 模态检测 → 特征提取 → GuardFusion多模态融合 → 风险评估分级 → 可视化报告 → 反诈联动处置`

------

## 核心算法与关键技术

### 音频 AI 合成语音检测算法

流水线：`音频预处理(16kHz、去静音、4s分段) → Whisper语音转写 → AASIST逐段鉴伪推理 → 文本诈骗关键词风险分析 → 融合决策 → 输出风险分数、等级、逐段可疑片段`

1. **AASIST‑Lite 语音鉴伪** 基于 SincConv 从原始波形直接提取频谱时序图，利用图注意力捕捉合成语音物理层面细微缺陷。
2. **Whisper 语音识别** 将音频转写文本，从语义层面识别诈骗话术，与声学鉴伪形成双路互补。
3. **逐段聚合策略** 取片段最大伪造分数保证不漏检；通过分数方差识别拼接伪造；内置 7 大类诈骗关键词，文本风险作为加成项。

### 视频换脸检测算法

1. **EfficientNet‑B0 换脸检测** 在换脸数据集微调，导出 TorchScript 脚本模型部署 Android 端。
2. **人脸七区域遮挡分析（创新可解释模块）** 对人脸分区做遮挡消融，定位图像中**哪一块区域最像伪造**，生成热力图，回答 “哪里可疑”。
3. **视频帧采样与时序分析** 视频按帧率采样关键帧，帧级风险聚合；移动端侧重单帧图像检测。

### 文本诈骗识别算法

采用**TextCNN 字符级嵌入**中文诈骗文本分类模型。

> 字符级建模优势：抗诈骗分子变体拆字、错别字，不需要分词，对未登录诈骗词汇鲁棒。

配套`TextVisualAnnotator`模块：Android Spannable 实现诈骗关键词多颜色高亮，输出风险色条、分析摘要，支持 HTML Mark 标记输出。

### GuardFusion 多模态融合检测算法（自研）

> 端侧可部署多模态融合网络，参数量约**2.8M**，支持 INT8 量化。

1. **设计思路**：分别提取视觉、声学、文本特征，通过`ModalityProjector`投影到统一 256 维共享空间；采用**瓶颈融合 Transformer (MBT)**做跨模态交互。
2. **创新跨模态一致性判别头**：输入模态差向量，专门识别 “人脸是 A，声音却是 B 克隆音” 这类单模态无法发现的矛盾。
3. **多输出头**：联合真伪判别头 + 三个单模态辅助监督头 + 跨模态一致性判别头。
4. **损失函数**：联合分类损失 + 单模态辅助损失 + 一致性损失 + InfoNCE 跨模态对比损失，Kendall 不确定性自动加权平衡。
5. **样本构造**：模态级配对合成训练样本，解决缺少真实三模态配对数据集的问题。
6. **风险分级（1‑5 级）**

表格

| 风险分      | 等级 | 判定   |
| ----------- | ---- | ------ |
| 0.00 ~ 0.19 | 1    | 真实   |
| 0.20 ~ 0.39 | 2    | 低风险 |
| 0.40 ~ 0.59 | 3    | 可疑   |
| 0.60 ~ 0.79 | 4    | 高风险 |
| 0.80 ~ 1.00 | 5    | 伪造   |

> 高风险等级给出建议：停止通讯、禁止转账、保存报告、拨打 96110 反诈专线。

### 换脸教育演示技术

后端基于 InsightFace buffalo_l + inswapper_128.onnx 实现换脸演示。

> 定位是**反诈科普演示工具，不面向娱乐换脸**，让用户直观感受深度伪造的逼真危害。

流程：人脸检测关键点对齐 → 人脸特征提取 → 特征替换融合输出图片。

------

## 系统实现

### 后端服务实现（FastAPI + Uvicorn）

- 特性：CORS 跨域、Pydantic 参数校验、自动 Swagger 文档、模型懒加载、请求大小限制、内存任务存储、统计计数器。

主要 API 接口列表：

表格

| 方法 | 路径                              | 用途                           |
| ---- | --------------------------------- | ------------------------------ |
| GET  | `/`                               | 服务首页仪表盘                 |
| GET  | `/admin`                          | 深色主题管理面板               |
| GET  | `/docs`                           | Swagger 交互式接口文档         |
| POST | `/api/v1/detect/audio`            | 音频 AI 伪造检测               |
| POST | `/api/v1/detect/video`            | 视频换脸检测                   |
| POST | `/api/v1/detect/text`             | 诈骗文本分析                   |
| POST | `/api/v1/faceswap`                | 换脸科普演示                   |
| GET  | `/api/v1/faceswap/status`         | 换脸模型加载状态               |
| GET  | `/api/v1/result/{task_id}`        | 查询历史检测结果               |
| GET  | `/api/v1/report-image/{filename}` | 获取可视化报告图片             |
| GET  | `/api/health`                     | 服务健康检查，返回模型加载状态 |
| POST | `/api/v1/report`                  | 端侧检测统计上报               |
| POST | `/api/v1/agent/chat`              | 反诈智能体对话接口             |

> 实现说明：后端音频检测完整接入 AASIST+Whisper；换脸演示完整接入 InsightFace；**视频、文本后端为占位实现，真实模型部署于移动端**，遵循本项目 “端侧优先” 设计理念。

### 移动端实现（Android Java + PyTorch‑Mobile）

assets 目录内置脚本化模型文件清单：

表格

| 模型文件                                                     | 大小   | 用途                         | 检测器类                |
| ------------------------------------------------------------ | ------ | ---------------------------- | ----------------------- |
| deepfake_model_scripted.pt                                   | 18 MB  | 图像换脸检测 EfficientNet‑B0 | LocalVideoDetector      |
| audio_spoof_model.pt                                         | 665 KB | AI 合成语音鉴伪 AASIST       | LocalAudioDetector      |
| textcnn_model_scripted.pt + textcnn_vocab.txt                | 4 MB   | 文本诈骗识别 TextCNN         | LocalTextDetector       |
| video_feature_scripted.pt / audio_feature_scripted.pt / text_feature_scripted.pt / guardfusion_scripted.pt | ~26MB  | GuardFusion 多模态融合       | LocalMultimodalDetector |

第三方组件：

- PyTorch‑Android：端侧推理
- Google ML‑Kit：人脸检测、关键点
- CameraX：相机预览拍照
- OkHttp：网络请求访问后端
- Room：本地持久化数据库
- Material / Navigation：UI 导航框架

### 实时监测能力（三大核心实时功能）

1. **通话实时检测 CallDetectFragment** 开启免提，AudioRecord 采集麦克风音频，每 4 秒做一次 AASIST 推理，实时刷新伪造风险；**所有数据仅本机内存，不保存不上传**，针对 AI 拟声电话诈骗。
2. **录屏检测 ScreenDetectActivity + MediaProjectionService** Android 前台录屏服务，抓取屏幕帧，定时抽取画面送入换脸检测器；针对微信等第三方 App 视频通话场景。
3. **摄像头实时检测 CameraActivity** CameraX 预览，检测到人脸自动采样推理，对准即测，输出人脸框关键点。

### 反诈智能体

以 DeepSeek 大模型为大脑，基于 Function‑Calling、ReAct 推理‑行动循环。 注册工具：

- `detect_text`：本地文本诈骗关键词检测
- `search_cases`：相似诈骗案例检索
- `detect_multimodal`：调用多模态融合检测

> 异常降级：大模型 API 不可用时，自动切换本地规则引擎，保证系统可用，抑制大模型幻觉。

### 3D 反诈数据地图 MapFragment

WebView 加载本地 ECharts‑GL，渲染 3D 中国柱状地图；展示用户分布、诈骗案例分布；**离线可用**；当前数据为演示样例，可对接后端统计接口。

### 可视化分析引擎

后端`audio_visualizer.py`，matplotlib 生成竖版长图体检报告：

- 标题时间、Whisper 转写文本、风险仪表盘
- 音频波形图、高风险片段标红
- 逐段检测柱状图
- 异常分析卡片 + 安全建议

配色：绿色安全、黄色可疑、红色高风险。

------

## 项目文件说明

```
FlareGuard‑多维盾卫
├── deepfake_fight                # Android移动端项目
│   ├── app/src/main/assets       # 存放pt脚本模型、词表
│   ├── app/src/main/java         # Java业务代码，各类Detector
│   └── app/src/main/res          # UI资源布局
├── backend                       # Python FastAPI后端
│   ├── audio_detector.py        # AASIST音频鉴伪实现
│   ├── server.py                 # FastAPI主服务入口
│   ├── audio_visualizer.py       # 可视化报告绘图
│   └── requirements.txt          # python依赖
└── README.md                     # 本说明文档
```

## 快速启动

### 后端启动

```
cd backend
pip install -r requirements.txt
uvicorn server:app --host 0.0.0.0 --port 8000
# 访问 http://127.0.0.1:8000/docs 打开接口文档
```

### Android App

1. 使用 Android Studio 打开`deepfake_fight`项目
2. 将脚本模型放入 `app/src/main/assets`
3. 修改网络请求 baseUrl 指向后端地址
4. 编译 APK 安装至 Android 设备

> 注意：不要将 API Key 硬编码提交 Git；密钥使用`.env`本地配置，`.env`加入`.gitignore`。
