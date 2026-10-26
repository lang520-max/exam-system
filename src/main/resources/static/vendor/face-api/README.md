# face-api（本地化的人脸检测 + 68 关键点库）

**库名**：`@vladmandic/face-api`
**版本**：`1.7.15`
**许可**：MIT（Copyright © Vladimir Mandic，见同目录 `LICENSE`）
**上游**：<https://github.com/vladmandic/face-api> · <https://www.npmjs.com/package/@vladmandic/face-api>

本目录内容是第三方文件，**未经修改**，只做了两件事：本地化（离线可用）、裁剪（只留两组模型）。
和 `vendor/katex/` 的约定一致：不放 CDN、不放 node_modules、不放 package.json。

为什么选它而不是原版 `face-api.js`：原版（justadudewhohacks/face-api.js）已停止维护，
`@vladmandic/face-api` 是它仍在维护的分支，接口完全兼容，且把 TensorFlow.js 一起打进
了同一个 UMD 文件 —— 于是只需要一个 `<script src>`，没有模块化 / 打包步骤。

## 文件清单与用途

| 文件 | 字节数 | 用途 |
| --- | ---: | --- |
| `face-api.js` | 1,333,943 | 库本体（IIFE/UMD，已内联 TensorFlow.js）。挂出全局 `window.faceapi`。**必须用 `<script src>` 同步引入**，且要在调用方脚本之前 |
| `LICENSE` | 1,067 | 上游 MIT 许可证原文，随库一起分发 |
| `model/tiny_face_detector_model-weights_manifest.json` | 3,219 | 人脸检测模型的权重清单（名字 / 形状 / 量化参数 + 分片文件名） |
| `model/tiny_face_detector_model.bin` | 193,321 | 人脸检测模型权重（单分片，量化 uint8） |
| `model/face_landmark_68_model-weights_manifest.json` | 8,485 | 68 关键点模型的权重清单 |
| `model/face_landmark_68_model.bin` | 356,840 | 68 关键点模型权重（单分片，量化 uint8） |
| `README.md` | 本文件 | 来源、体积、裁剪说明 |

**总体积**：6 个第三方文件合计 **1,896,875 字节（≈1.81 MB）**，加上本 README 约 1.82 MB。

模型目录的加载路径就是 `/vendor/face-api/model`（`js/face-liveness.js` 的默认值）。
`loadFromUri()` 会去取 `<modelPath>/<默认模型名>-weights_manifest.json`，清单里的
`paths` 是相对文件名，加载器会自己在中间补 `/`，所以这里保持上游的文件名与同目录布局即可。

## 为什么只保留这两组模型

上游 npm 包的 `model/` 里一共 7 组模型、约 17.2 MB。这里只留了最小的两组，因为
`js/face-liveness.js` 只做「框出人脸 + 眨眼」两件事：

- `tiny_face_detector`（190 KB）—— 给出人脸框。它比 `ssd_mobilenetv1`（5.6 MB，**约 30 倍**）
  精度略低，但侧脸/正脸的检测率对本场景完全够用，而且手机上单帧只要几十毫秒，
  这是能采到「眨眼」这种百毫秒级事件的前提。
- `face_landmark_68`（357 KB）—— 68 个关键点，眨眼判定只用其中的眼睛 12 个点（左眼 36–41、右眼 42–47）。
  没有用 `face_landmark_68_tiny`（77 KB）：它更小，但眼部点位在手机前置这种
  稍低分辨率下抖动更大，EAR 阈值会变得不可靠，省下的 280 KB 不值得。

**没有下载**的：`face_recognition_model`（6.4 MB，128 维特征向量）、
`ssd_mobilenetv1`（5.6 MB）、`age_gender_model`（430 KB）、`face_expression_model`（329 KB）。
原因：本项目的身份比对走**服务端**阿里云「视觉智能开放平台 - 人脸比对 1:1（CompareFace）」，
前端只负责产出一张通过活体的 JPEG 底片；表情、年龄性别、本地特征向量在这个项目里
一个都用不上。少下这 15 MB，既省仓库体积，也省用户第一次打开采集页的下载时间。

## 升级方式

```bash
# 在一个仓库外的临时目录里执行，别在项目里留 node_modules / package.json
npm pack @vladmandic/face-api@<新版本>
tar -xzf vladmandic-face-api-<新版本>.tgz
# 用 package/dist/face-api.js 覆盖本目录同名文件、package/LICENSE 覆盖 LICENSE，
# 再按上面的清单从 package/model/ 里挑同样这 4 个模型文件，最后更新本文件里的版本号
```

## 已知限制

- 库本体未压缩（上游 1.7.15 的 `dist/` 只提供 `face-api.js` 与 `face-api.esm.js`，没有 `.min.js`）。
  如果将来在意首屏体积，开 Spring Boot 的静态资源 gzip/brotli 即可，`face-api.js` 压缩后约 350 KB。
- 需要 WebGL 才能在手机上跑到可用的帧率；没有 WebGL 时 `face-liveness.js` 会自动退回 CPU 后端，
  功能仍在，但检测变慢、眨眼更容易漏采。