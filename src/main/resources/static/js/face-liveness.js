/* ============================================================================
 *  face-liveness.js —— 人脸采集 + 眨眼活体检测（纯前端、无构建、离线可用）
 *
 *  做三件事：
 *    1. 打开摄像头，把画面接进调用方给的 <video>（预览怎么排版是页面的事）
 *    2. 用本地人脸库（/vendor/face-api/）检测人脸 + 68 关键点，按 EAR
 *       （眼睛纵横比）判定「睁 → 闭 → 睁」为一次眨眼
 *    3. 眨眼通过后抓当前帧、按最长边 640 压成 JPEG data URL 交回调用方
 *       抓拍结果就是服务端要的 imageBase64（后端 FaceEnrollForm 里那个字段）
 *
 *  为什么活体选「眨眼」而不是「摇头 / 张嘴」：
 *  眨眼是被动动作，用户不需要理解规则，一次眨眼就足以证明画面里是个活人；
 *  摇头这类动作中途很容易把脸转出取景框，手机前置摄像头上失败率明显更高。
 *
 *  为什么用 EAR 而不是「眼睛像素高度 / 面积」：
 *  EAR 的分母是两个眼角的距离，它随人脸远近、脸的大小一起缩放，
 *  所以 EAR 只反映「眼睛睁开的程度」，跟离镜头多远无关 —— 阈值因此可以写成
 *  固定的 0.28 / 0.20，不必按人、按设备重新标定。
 *
 *  为什么不用现成 SDK / 云服务：项目约定「无构建、零 CDN」。
 *  这里唯一的依赖是一个本地 <script src="/vendor/face-api/face-api.js">，
 *  没有打包步骤，运行时也不向外网发任何请求（模型文件同样在本地）。
 *
 *  用法：
 *    FaceLiveness.capture({ video: el, modelPath: '/vendor/face-api/model',
 *                           onHint: fn, onProgress: fn })
 *      .then(function (r) { 提交 r.imageBase64; })
 *      .catch(function (e) { 按 e.code 给提示; });
 *    取消 / 离开页面时调 FaceLiveness.stop(video)。
 * ========================================================================== */

(function (global) {
  'use strict';

  /* ------------------------------------------------------------ 默认参数 */

  var DEFAULT_MODEL_PATH = '/vendor/face-api/model';
  var DEFAULT_TIMEOUT_MS = 45000;        // 检测阶段的超时（不含开摄像头、下模型）
  var DEFAULT_EAR_OPEN = 0.28;           // ≥ 这个值算「睁眼」
  var DEFAULT_EAR_CLOSED = 0.20;         // ≤ 这个值算「闭眼」
  var DEFAULT_WARMUP_FRAMES = 12;        // 热身帧数：跑够这么多帧才开始收眨眼
  var DEFAULT_DETECT_INTERVAL_MS = 40;   // 两次检测之间的最小间隔
  var DEFAULT_TURN_RATIO = 0.12;        // 转头活体：横向位移达到脸宽的这个比例算一次转头（真机实测几十像素级，比眨眼鲁棒）
var DEFAULT_INPUT_SIZE = 160;          // tinyFaceDetector 的输入边长（32 的倍数）。160 比 224 快约 2 倍 —— 手机上一帧推理几百毫秒时，眨眼（100~400ms）会被整帧漏掉，采样率比精度更重要
  var DEFAULT_SCORE_THRESHOLD = 0.5;
  var CAPTURE_MAX_EDGE = 640;            // 抓拍图最长边
  var JPEG_QUALITY = 0.7;
  var MAX_CONSECUTIVE_ERRORS = 20;       // 连续这么多帧检测异常就认输（≈2 秒），别干等到超时

  /* ------------------------------------------------------------ 模块状态 */

  // 同一页面同时只允许一路采集在跑。存当前会话，stop() 靠它找到要关掉的流。
  var SESSION = null;

  // 模型是全局单例（挂在 window.faceapi 上），加载一次就够。
  // 为什么要自己记这个标志：NeuralNetwork.loadFromUri() 不会判断「已加载」，
  // 重复调用会重新下载并再建一遍权重张量，旧张量不会自动释放 —— 多采集几次
  // 就是稳定的内存泄漏（手机上表现为越来越卡直到崩标签页）。
  var MODELS_READY = false;

  /* ------------------------------------------------------------ 错误类型 */

  /**
   * 采集失败。带 code，页面可以据此分流提示：
   *   NO_CAMERA        —— 拿不到摄像头（不支持 / 没有设备 / 被占用 / 画面放不出来）
   *   PERMISSION_DENIED—— 用户拒绝或浏览器策略拒绝了摄像头权限
   *   NO_FACE          —— 超时了都没在取景框里看到脸
   *   NO_BLINK         —— 看到脸了，但超时都没眨一次眼
   *   LIB_MISSING      —— 人脸库或模型没到位（少 <script>、模型 404）
   *   UNKNOWN          —— 其它（含调用方参数写错）
   *   CANCELLED        —— 调用方主动 stop() 或页面离开（本模块额外补充的一种，
   *                       与「失败」区分开，页面可以静默忽略而不是弹红字）
   */
  function FaceError(code, message) {
    this.name = 'FaceError';
    this.code = code;
    this.message = message || '人脸采集失败';
  }
  FaceError.prototype = Object.create(Error.prototype);
  FaceError.prototype.constructor = FaceError;

  /* ------------------------------------------------------------ 小工具 */

  function isSupported() {
    return !!(global.navigator
      && global.navigator.mediaDevices
      && typeof global.navigator.mediaDevices.getUserMedia === 'function');
  }

  /**
   * 回调一律包一层 try/catch。
   * 页面给的 onHint/onProgress 里抛一个异常，不该把整个采集流程带崩 ——
   * 那样用户看到的是「摄像头突然黑掉」，而真正的原因只是画面上少了个元素。
   */
  function emit(opts, name, payload) {
    var fn = opts[name];
    if (typeof fn !== 'function') { return; }
    try { fn(payload); } catch (e) { /* 页面自己的锅，不影响采集 */ }
  }

  function hint(opts, text) { emit(opts, 'onHint', text); }

  /** 进度回调的载荷固定是这四个字段，页面不需要做兼容判断 */
  /**
 * 把进度抛给页面。extra 里带当前生效的眨眼阈值与基线 ——
 * 真机上「明明眨了却过不去」时，页面把这两个数打出来就能一眼看出是阈值问题
 * 还是压根没检测到眼睛（见 face-test.html 的进度行）。
 */
  function progress(opts, stage, ear, hasFace, blinks, extra) {
    var payload = { stage: stage, ear: ear, hasFace: hasFace, blinks: blinks };
    if (extra) {
      payload.armThreshold = extra.arm;
      payload.closeThreshold = extra.close;
      payload.baseline = extra.baseline;
      // minEar 是排查「明明眨了却过不去」最有用的一项：
      // 它说明采样窗口里 EAR 到底跌到过多少 ——
      // 若 minEar 贴着 close（差一点点）→ 是阈值太紧；
      // 若 minEar 几乎等于基线 → 那一瞬根本没被采到（采样率不够）。
      payload.minEar = extra.minEar;
      payload.frames = extra.frames;
    }
    emit(opts, 'onProgress', payload);
  }

  /**
   * 进度条百分比（0-100）。抽成纯函数是为了能单测（simulate-turn.js 会验它）。
   *
   * 组成（取三者最大，保证"看得见在动"）：
   *   1) 人脸底：一旦检测到人脸就给 6% —— 用户点开始后立刻有反馈，不是干等
   *   2) 时间底：热身后随等待时间爬到 30%（每 20 秒 +6%，上限 30%）——
   *      这是「假的也无所谓，反正要有提示」那部分：还没转头时进度条也在缓慢前进
   *   3) 真进度：转头段 = ratio/turnRatio × 60；回正段 = 60 + 回到中性位置的比例 × 40
   *
   * 为什么要假的垫底：用户盯着进度条时如果它一直是 0，会以为"坏了/没生效"
   * （真实反馈：'下面进度条还是不会变动' —— 因为当时只在转头判定里赋值）。
   */
  function computePercent(s) {
    var faceFloor = s.faceFrames > 0 ? 6 : 0;
    var seconds = Math.max(0, (s.elapsedMs || 0) / 1000);
    var timeFloor = s.faceFrames > s.warmupFrames
      ? Math.min(30, Math.floor(Math.max(0, seconds - 3) / 20 * 6) + 6)
      : 0;
    var real;
    if (s.turnedAway) {
      // 回正段要**按「转回了多少」平滑上升**：以峰值（本次转头达到的最大幅度）为基准，
      // 而不是以"完成阈值"为基准 —— 后者在还没回到位时一直是 60%，最后一下直接跳到
      // 100%，"正在转回来"的过程完全看不见（simulate-percent.js 抓到的体验问题）。
      var returnAt = s.turnRatio * 0.25;
      var peak = Math.max(s.peakRatio || 0, s.ratio, returnAt + 0.001);
      var back = Math.min(1, Math.max(0, 1 - (s.ratio - returnAt) / (peak - returnAt)));
      real = 60 + Math.round(back * 40);
    } else {
      real = Math.round(Math.min(1, s.ratio / s.turnRatio) * 60);
    }
    return Math.max(faceFloor, timeFloor, real);
  }

  function numberOr(v, fallback) {
    if (v === null || typeof v === 'undefined' || v === '') { return fallback; }
    var n = Number(v);
    return isNaN(n) ? fallback : n;
  }

  /**
   * tinyFaceDetector 的 inputSize 必须是 32 的倍数，否则构造 Options 时直接抛错。
   * 这里收敛一次：非法值退回默认，而不是把人脸库内部的异常抛给页面。
   * 默认取 224 是「够用且快」的档位：取景框里只有一张脸，416 会让手机每帧慢一倍多。
   */
  function safeInputSize(v) {
    var n = numberOr(v, DEFAULT_INPUT_SIZE);
    if (n < 128 || n > 608 || n % 32 !== 0) { return DEFAULT_INPUT_SIZE; }
    return n;
  }

  function safeScoreThreshold(v) {
    var n = numberOr(v, DEFAULT_SCORE_THRESHOLD);
    if (!(n > 0 && n < 1)) { return DEFAULT_SCORE_THRESHOLD; }
    return n;
  }

  function round3(n) { return Math.round(n * 1000) / 1000; }

  /** 中位数：比平均值抗离群点（热身期难免有几帧关键点抖出来的怪值） */
  function median(arr) {
    if (!arr || !arr.length) { return null; }
    var s = arr.slice().sort(function (a, b) { return a - b; });
    var mid = Math.floor(s.length / 2);
    return s.length % 2 ? s[mid] : (s[mid - 1] + s[mid]) / 2;
  }

  /**
   * 由热身期的静息 EAR 推导「睁眼 / 闭眼」两个阈值。
   *
   * 为什么要自适应：绝对阈值（0.28 / 0.20）是拿常见人脸估的，但**天生眼睛细长、
   * 或戴厚镜片反光的人静息 EAR 可能只有 0.18** —— 那样 `ear >= 0.28` 永远不成立，
   * 状态机永远不 armed，用户明明眨了也会一路超时成 NO_BLINK（实测样例里就有一张
   * 真人照片静息 EAR 只有 0.1777）。用本人的静息值当基线就没有这个问题。
   *
   * 取值：睁眼 = min(0.28, 0.85 × 基线)，闭眼 = min(0.24, 0.72 × 基线, 睁眼 - 0.03)。
   * 都取 min 是刻意的 —— 自适应只负责「放宽到本人够得着」，不负责放宽标准：
   * 基线本来就高的人仍按绝对阈值判。
   *
   * 闭眼系数从 0.65 放宽到 0.72 的实测依据：真机反馈
   * 「基线 0.299 / 阈值 0.254-0.195 / blinks=0」——手机摄像头 + MJPEG 压缩下，
   * 真实眨眼测到的 EAR 往往只跌到基线的 0.72 左右（不像实验室数据那样跌到 0.1），
   * 0.65 就会一路判不出来。放宽不会削弱防照片能力：**静止照片的 EAR 是恒定的**，
   * 必须「先睁(≥arm) → 再闭(≤close) → 再睁」这个完整往返才算一次眨眼。
   *
   * @param {number[]} warmSamples 热身期的 EAR 采样
   * @param {number}   absOpen     绝对睁眼阈值
   * @param {number}   absClosed   绝对闭眼阈值
   * @returns {{baseline:number, arm:number, close:number}|null} 样本不足时返回 null（回退绝对阈值）
   */
  function deriveThresholds(warmSamples, absOpen, absClosed) {
    // 样本太少就不敢推：2 帧的中位数可能正好落在一次眨眼的中间，
    // 那样推出来的「基线」本身就偏低，阈值会松到把眯眼当眨眼
    if (!warmSamples || warmSamples.length < 3) { return null; }
    var baseline = median(warmSamples);
    if (baseline === null || !isFinite(baseline) || baseline <= 0.05) { return null; }
    var arm = Math.max(0.10, Math.min(absOpen, baseline * 0.85));
    var close = Math.max(0.05, Math.min(0.24, baseline * 0.72, arm - 0.03));
    return { baseline: round3(baseline), arm: round3(arm), close: round3(close) };
  }

  function sleep(ms) {
    return new Promise(function (resolve) { setTimeout(resolve, ms); });
  }

  function distance(a, b) {
    var dx = a.x - b.x, dy = a.y - b.y;
    return Math.sqrt(dx * dx + dy * dy);
  }

  /* ------------------------------------------------------------ 摄像头 */

  /**
   * 眼睛纵横比 EAR（Soukupová & Čech, 2016）。
   *
   * 68 关键点里左眼是 36–41、右眼是 42–47，各 6 个点按「外眼角 → 上眼睑两点
   * → 内眼角 → 下眼睑两点」排成一圈，于是标准公式是：
   *     EAR = (‖p2-p6‖ + ‖p3-p5‖) / (2 · ‖p1-p4‖)
   * 对应到数组下标就是 (‖e1-e5‖ + ‖e2-e4‖) / (2 · ‖e0-e3‖)。
   *
   * 睁眼约 0.30 上下，闭眼会掉到 0.15 以下，中间这段落差足够大，
   * 用固定阈值就能稳定区分，不需要训练。
   * 单只眼会因头部转动而不准，所以左右眼各算一次再取平均。
   */
  function eyeAspectRatio(eye) {
    if (!eye || eye.length < 6) { return null; }
    var width = distance(eye[0], eye[3]);
    if (!(width > 0)) { return null; }   // 关键点退化（脸太小/太侧）时没法算，交给调用方跳过这帧
    return (distance(eye[1], eye[5]) + distance(eye[2], eye[4])) / (2 * width);
  }

  function averageEar(landmarks) {
    var left = eyeAspectRatio(landmarks.getLeftEye());
    var right = eyeAspectRatio(landmarks.getRightEye());
    if (left === null && right === null) { return null; }
    if (left === null) { return right; }
    if (right === null) { return left; }
    return (left + right) / 2;
  }

  /**
   * 把 getUserMedia 抛出来的 DOMException 翻译成本模块的错误码。
   * 驼峰式的旧名字（PermissionDeniedError 等）是 Chrome/Safari 早期的写法，
   * 一并认掉，免得在老一点的 Edge/WebView 上掉进 UNKNOWN 分支。
   */
  function mapCameraError(e) {
    var name = (e && e.name) ? String(e.name) : '';
    if (name === 'NotAllowedError' || name === 'PermissionDeniedError'
      || name === 'SecurityError' || name === 'PermissionDismissedError') {
      return new FaceError('PERMISSION_DENIED',
        '摄像头权限被拒绝：请点地址栏左侧的权限图标允许使用摄像头，然后重试');
    }
    if (name === 'NotFoundError' || name === 'DevicesNotFoundError') {
      return new FaceError('NO_CAMERA', '这台设备上没有找到可用的摄像头');
    }
    if (name === 'OverconstrainedError' || name === 'ConstraintNotSatisfiedError') {
      return new FaceError('NO_CAMERA', '摄像头不支持所需的分辨率或朝向');
    }
    if (name === 'NotReadableError' || name === 'TrackStartError' || name === 'AbortError') {
      return new FaceError('NO_CAMERA', '摄像头被其它程序占用了，请关掉正在使用摄像头的软件后重试');
    }
    return new FaceError('NO_CAMERA', '无法打开摄像头：' + ((e && e.message) || name || String(e)));
  }

  /**
   * 停掉一个流的所有轨道。
   *
   * 这里是「摄像头指示灯」的唯一开关：只把 video.srcObject 置空并不会让
   * 摄像头停下来，必须逐个 track.stop()，否则灯一直亮着 —— 用户会以为
   * 被偷拍了，这是这类功能最容易踩、也最招投诉的坑。
   */
  function stopTracks(stream) {
    var tracks = (stream && stream.getTracks) ? stream.getTracks() : [];
    for (var i = 0; i < tracks.length; i++) {
      try { tracks[i].stop(); } catch (e) { /* 已经停了就算了 */ }
    }
  }

  /**
   * 无条件把画面从 <video> 上摘掉：页面自己开的摄像头也一起关。
   * 只给 stop(video) 的兜底分支用。
   */
  function detachAny(video) {
    if (!video) { return; }
    var current = null;
    try { current = video.srcObject || null; } catch (e) { return; }
    try { video.pause(); } catch (e) { /* 没在播就不管 */ }
    try {
      if (current) {
        stopTracks(current);
        video.srcObject = null;
      }
    } catch (e) { /* 某些老实现不允许写 srcObject，忽略 */ }
  }

  /**
   * 收掉某一路采集自己的画面 —— 只动「确实是这一路放上去的流」。
   *
   * 为什么要认流不认元素：同一个 <video> 上完全可能已经起了新的一路采集
   * （用户连点两次「重拍」就是这样），而上一路的收尾是在异步流程的 finally
   * 里跑的，会晚于新一路的启动。这时候无差别地 pause + 清空 srcObject，
   * 就会把新那一路的预览一起关掉 —— 表现出来是「第二次点重拍打不开摄像头」，
   * 而且现象飘忽，很难查。
   *
   * session.stream 在整个生命周期里都不置空（track.stop() 本身幂等，
   * 重复停没有副作用），就是为了让这个身份判断在收尾时依然成立。
   */
  function detachSession(session) {
    if (!session || !session.stream) { return; }   // 从头到尾没拿到过流，就没往画面上放过东西
    var video = session.video;
    if (!video) { return; }
    var current = null;
    try { current = video.srcObject || null; } catch (e) { return; }
    if (current !== session.stream) { return; }    // 画面已经换成别人的流了，别碰
    try { video.pause(); } catch (e) { /* 没在播就不管 */ }
    try { video.srcObject = null; } catch (e) { /* 某些老实现不允许写 srcObject，忽略 */ }
  }

  function stopSession(session) {
    if (!session) { return; }
    session.cancelled = true;      // 让正在 await 的流程在下一个检查点立刻退出
    // 不把 session.stream 置空：它是这一路的身份，收尾时要靠它判断画面上
    // 现在放的是不是自己那一路（见 detachSession）。重复 stop 是幂等的。
    if (session.stream) { stopTracks(session.stream); }
    detachSession(session);
    if (SESSION === session) { SESSION = null; }
  }

  /**
   * 关闭摄像头。页面离开、用户点「取消」、流程跑完，都必须调它。
   *
   * 传入的 video 与当前会话不匹配时也照样清理：多清一次没有代价（本来就
   * 没流可停），漏清一次却会让指示灯一直亮着。所以这里选择「宁可多做」。
   */
  function stop(video) {
    if (SESSION && (!video || SESSION.video === video)) {
      stopSession(SESSION);
      return;
    }
    detachAny(video);   // 页面自己开的摄像头，也顺手关掉
  }

  // 页面被关闭 / 跳转时兜底：页面作者忘了调 stop() 也不会留下亮着的摄像头。
  // 用 pagehide 而不是 unload —— 手机上 unload 经常根本不触发。
  if (global.addEventListener) {
    global.addEventListener('pagehide', function () {
      if (SESSION) { stopSession(SESSION); }
    });
  }

  /** 会话被 stop() 取消后，流程里的每个 await 检查点都要走这里退出 */
  function ensureAlive(session) {
    if (session.cancelled) {
      stopSession(session);   // 双保险：getUserMedia 迟到返回的流也要被关掉
      throw new FaceError('CANCELLED', '人脸采集已取消');
    }
  }

  /**
   * 等 <video> 拿到真实画面尺寸。
   * 不是所有浏览器在 play() resolve 时就已经有 videoWidth，而后面抓帧要靠它
   * 算缩放比例；拿不到就说明摄像头其实没出画，直接判 NO_CAMERA 比让用户
   * 对着黑屏等 45 秒强。
   */
  function waitForVideoSize(video, timeoutMs) {
    return new Promise(function (resolve) {
      var deadline = Date.now() + timeoutMs;
      (function tick() {
        if (video.videoWidth > 0 && video.videoHeight > 0) { resolve(true); return; }
        if (Date.now() >= deadline) { resolve(false); return; }
        setTimeout(tick, 50);
      })();
    });
  }

  async function openCamera(video, session, opts) {
    hint(opts, '正在打开摄像头…');
    progress(opts, 'camera', null, false, 0);

    // muted + playsinline 是手机浏览器能自动播放预览的前提：
    // 不静音会被自动播放策略拦下；不加 playsinline，iOS/部分安卓会把视频
    // 拉成全屏播放，取景框就跑出屏幕了。这两个属性由 JS 设，页面不用管。
    video.muted = true;
    video.setAttribute('muted', 'muted');
    video.setAttribute('playsinline', 'playsinline');
    video.setAttribute('webkit-playsinline', 'webkit-playsinline');
    video.autoplay = true;

    // 用 ideal 而不是 exact：写死 640×480 时，只有 720p 档位的摄像头会直接
    // 抛 OverconstrainedError，而用户其实只是想要一张够用的脸照。
    var constraints = {
      audio: false,
      video: {
        facingMode: 'user',
        width: { ideal: 640 },
        height: { ideal: 480 }
      }
    };

    var stream;
    try {
      stream = await global.navigator.mediaDevices.getUserMedia(constraints);
    } catch (e) {
      throw mapCameraError(e);
    }

    // 权限弹窗可能挂很久，期间用户完全可能已经点了「取消」。
    // 这时流是刚拿到的：必须马上关掉再抛错，否则指示灯就亮着没人管了。
    session.stream = stream;
    ensureAlive(session);

    video.srcObject = stream;
    try {
      await video.play();
    } catch (e) {
      throw new FaceError('NO_CAMERA',
        '摄像头已打开但画面无法播放：' + ((e && e.message) || String(e)));
    }

    var sized = await waitForVideoSize(video, 5000);
    if (!sized) {
      throw new FaceError('NO_CAMERA', '摄像头没有返回画面，请检查设备是否被占用');
    }
  }

  /* -------------------------------------------------------------- 模型 */

  /**
   * 初始化计算后端。
   * 默认走 WebGL：CPU 后端在手机上跑一次检测要几百毫秒，眨眼这么短的事件
   * 根本采不到几帧，等于功能不可用。没有 WebGL（老设备 / 被策略禁用）时
   * 退回 CPU —— 慢，但功能还在，不该因此直接失败。
   */
  async function initBackend(faceapi) {
    var tf = faceapi.tf;
    if (!tf || !tf.setBackend) { return; }   // 理论上不会：dist/face-api.js 已内联 tfjs
    try {
      await tf.setBackend('webgl');
      await tf.ready();
    } catch (e) {
      try {
        await tf.setBackend('cpu');
        await tf.ready();
      } catch (e2) { /* 后端都起不来的话，让后面的检测帧去报错，信息更具体 */ }
    }
    try {
      // 两个纯性能开关，flag 名在各版本间会变，存在才设，不存在就算了
      var flags = tf.env && tf.env().flagRegistry;
      if (flags && flags.WEBGL_EXP_CONV) { tf.env().set('WEBGL_EXP_CONV', true); }
      if (flags && flags.CANVAS2D_WILL_READ_FREQUENTLY) {
        tf.env().set('CANVAS2D_WILL_READ_FREQUENTLY', true);
      }
    } catch (e3) { /* 优化开关设不上不影响正确性 */ }
  }

  /**
   * 加载本模块需要的两组模型。
   *
   * 只要两组，是因为这里只做「框出人脸 + 眨眼」：
   *   tiny_face_detector     —— 人脸框（约 190 KB）
   *   face_landmark_68       —— 68 关键点（约 350 KB），眨眼要用眼睛那 12 个点
   * 身份比对是服务端阿里云 CompareFace 的事，所以 128 维特征模型、表情、
   * 年龄性别这些一个都不下 —— 它们加起来比上面两组大一个数量级。
   */
  async function ensureModels(faceapi, opts, session) {
    if (MODELS_READY) { return; }
    var modelPath = opts.modelPath || DEFAULT_MODEL_PATH;

    hint(opts, '正在加载人脸模型…');
    progress(opts, 'model', null, false, 0);

    await initBackend(faceapi);
    ensureAlive(session);

    try {
      if (!faceapi.nets.tinyFaceDetector.isLoaded) {
        await faceapi.nets.tinyFaceDetector.loadFromUri(modelPath);
      }
      if (!faceapi.nets.faceLandmark68Net.isLoaded) {
        await faceapi.nets.faceLandmark68Net.loadFromUri(modelPath);
      }
    } catch (e) {
      throw new FaceError('LIB_MISSING',
        '人脸模型加载失败（' + modelPath + '）：' + ((e && e.message) || String(e)));
    }

    MODELS_READY = true;
    ensureAlive(session);
  }

  /* ---------------------------------------------------- 检测 + 眨眼 */

  /**
   * 循环检测，直到抓到一次眨眼（或者超时、或者被取消）。
   *
   * 眨眼判定把 EAR 串成一个三态状态机：
   *   armed（见过睁眼）→ closedSeen（见到闭眼）→ 再次 ≥ earOpen ⇒ 一次眨眼
   * 每一态只置一次标志、不要求连续帧，因为检测是节流的（默认 60 ms 一帧，
   * 手机上实际更慢），要求连续反而会漏掉真实的眨眼。
   *
   * 热身帧的作用是「先确认眼睛本来就是睁着的」：如果用户一进来就眯着眼，
   * 或者模型刚起来那几帧关键点还在抖，first-frame 的假信号会被当成眨眼。
   */
  async function watchForBlink(faceapi, video, opts, session, timeoutMs) {
    var warmupFrames = Math.max(0, Math.round(numberOr(opts.warmupFrames, DEFAULT_WARMUP_FRAMES)));
    var intervalMs = Math.max(0, Math.round(numberOr(opts.detectIntervalMs, DEFAULT_DETECT_INTERVAL_MS)));

    // 阈值：默认走「热身期自适应」（见 deriveThresholds 的注释）；
    // 调用方显式传了 earOpen/earClosed 就完全按它给的来（诊断页/真机调参用得上），
    // 也可以用 adaptiveEar:false 退回固定阈值。
    var absOpen = numberOr(opts.earOpen, DEFAULT_EAR_OPEN);
    var absClosed = numberOr(opts.earClosed, DEFAULT_EAR_CLOSED);
    var adaptive = (opts.adaptiveEar !== false)
      && (opts.earOpen === undefined) && (opts.earClosed === undefined);
    var warmSamples = [];
    var derived = null;
    var armThreshold = absOpen;
    var closeThreshold = absClosed;

    var detector = new faceapi.TinyFaceDetectorOptions({
      inputSize: safeInputSize(opts.inputSize),
      scoreThreshold: safeScoreThreshold(opts.scoreThreshold)
    });

    var deadline = Date.now() + timeoutMs;
    var faceFrames = 0;        // 拿到关键点的帧数（热身计数只算这些帧）
    var everSeenFace = false;  // 全程有没有见过脸 → 决定超时报 NO_FACE 还是 NO_BLINK
    var armed = false;         // 见过睁眼
    var closedSeen = false;    // 见过闭眼（等下一次睁眼就成一次眨眼）
    var blinks = 0;
    var lastEar = null;
    var minEar = null;      // 窗口内见过的最低 EAR：排查「眨了却过不去」的关键数据
    var lastHint = '';
    var consecutiveErrors = 0;
    var lastError = null;

    // ---- 转头活体（与眨眼并行的第二条通路）----
    // 为什么要加：真机反馈「帧数 207 / 最低 ear 0.267（阈值 0.226）/ blinks=0」——
    // 采样足够多，但 EAR 全程没跌过，说明在低分辨率 + 软件渲染的手机上，
    // 68 点模型对「闭眼」几乎不响应（眼皮没被跟踪到）。这种链路上眨眼本身不可靠，
    // 再怎么调阈值都没用；而**头部位移是几十像素级的信号**，即使 4 fps 也测得准。
    // 两个动作哪个先完成都算通过（取景框里给用户的提示也是「眨眼或转头」）。
    var turnBaselineX = null;   // 头部中性位置的横坐标（热身期取中位数）
    var turnSamples = [];
    var turnFrames = 0;         // 连续满足位移阈值的帧数（防单帧抖动误判）
var turnRatio = Math.max(0.05, numberOr(opts.turnRatio, DEFAULT_TURN_RATIO));
var turned = false;
    var turnedAway = false;   // 第一段（转头）是否已完成
    var turnHint = '';        // 第二段的自定义提示
    var progressPct = 0;      // 进度条百分比（0-100），两段式
    var startedAtMs = Date.now();   // 时间底用：让进度条在没转头时也在动
    var phase = 'turn';       // turn=转头中 / return=回正中
    var maxTurnRatio = 0;

    hint(opts, '请把脸放入取景框');
    progress(opts, 'detect', null, false, 0);

    for (;;) {
      ensureAlive(session);

      if (Date.now() > deadline) {
        throw everSeenFace
          ? new FaceError('NO_BLINK', '没有检测到活体动作：请眨一下眼、或慢慢左右转头后重试'
            + '（本轮最低 EAR=' + (minEar === null ? '未采集到' : round3(minEar))
            + '，闭眼阈值=' + round3(closeThreshold)
            + '，最大转头幅度=' + round3(maxTurnRatio)
            + '，转头阈值=' + round3(turnRatio) + '，共 ' + faceFrames + ' 帧）')
          : new FaceError('NO_FACE', '没有检测到人脸：请把脸放入取景框，并确认光线充足');
      }

      var result = null;
      try {
        result = await faceapi.detectSingleFace(video, detector).withFaceLandmarks();
        consecutiveErrors = 0;
      } catch (e) {
        // 单帧失败（视频刚起还没出画、WebGL context lost）不该判死，跳过继续。
        // 但一直失败说明环境真的坏了，攒够次数就报出来，别让用户干等到超时。
        consecutiveErrors++;
        lastError = e;
        if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
          throw new FaceError('UNKNOWN',
            '人脸检测持续失败：' + ((e && e.message) || String(e)));
        }
      }

      ensureAlive(session);   // 检测可能跑了几十毫秒，回来时用户也许已经取消了

      var hasFace = !!(result && result.landmarks);
      var ear = hasFace ? averageEar(result.landmarks) : null;
      if (ear === null) { hasFace = false; }

      if (hasFace) {
        everSeenFace = true;
        faceFrames++;
        lastEar = ear;
        // 记下窗口内见过的最低 EAR（只在热身结束后统计，避免把热身期的抖动算进去）
        if (faceFrames > warmupFrames && (minEar === null || ear < minEar)) {
          minEar = ear;
        }

        // ---- 转头活体：热身期记中性位置，之后看横向位移占脸宽的比例 ----
        var box = result.detection && result.detection.box ? result.detection.box : null;
        var centerX = box ? (box.x + box.width / 2) : null;
        if (box && box.width > 20 && centerX !== null) {
          if (faceFrames <= warmupFrames) {
            turnSamples.push(centerX);
          } else {
            if (turnBaselineX === null && turnSamples.length >= 3) {
              turnBaselineX = median(turnSamples);
            }
            if (turnBaselineX !== null) {
              var ratio = Math.abs(centerX - turnBaselineX) / box.width;
              if (ratio > maxTurnRatio) { maxTurnRatio = ratio; }

              // 两段式，避免「一转头就过」：
              //   第一段 转头：转到 turnRatio 并连续 2 帧保持住 → 进入第二段
              //   第二段 回正：转回接近中性位置（≤ 0.25×turnRatio）才算完成
              // 进度条也按这两段走：转头占 60%，回正占 40%。用户反馈明确要求
              // 「不要一转头就识别成功」，所以完成条件是整个往返，而不是一次偏移。
              var turnProgress = Math.min(1, ratio / turnRatio);
              if (!turnedAway) {
                if (ratio >= turnRatio) {
                  turnFrames++;
                } else {
                  turnFrames = 0;
                }
                if (turnFrames >= 2) {
                  turnedAway = true;
                  turnHint = '很好，请把头转回正前方';
                }
                phase = 'turn';
                // 真正的转头进度与「看得见的底」取大者 —— 见 computePercent 的注释：
                // 用户盯着进度条时还没转头，如果只有转头才动，就会以为"进度条不会变"。
                progressPct = computePercent({
                  faceFrames: faceFrames, warmupFrames: warmupFrames,
                  ratio: ratio, peakRatio: maxTurnRatio, turnRatio: turnRatio, turnedAway: false,
                  elapsedMs: Date.now() - startedAtMs
                });
              } else {
                var backProgress = Math.min(1, Math.max(0, 1 - ratio / (turnRatio * 0.25)));
                phase = 'return';
                progressPct = computePercent({
                  faceFrames: faceFrames, warmupFrames: warmupFrames,
                  ratio: ratio, peakRatio: maxTurnRatio, turnRatio: turnRatio, turnedAway: true,
                  elapsedMs: Date.now() - startedAtMs
                });
                if (ratio <= turnRatio * 0.25) {
                  turned = true;
                  progressPct = 100;
                  break;
                }
              }
            }
          }
        }

        if (adaptive) {
          if (faceFrames <= warmupFrames) {
            // 热身期只采样、不判定：这几帧用来量「这个人睁着眼时 EAR 是多少」
            warmSamples.push(ear);
          } else if (derived === null && warmSamples.length >= 3) {
            derived = deriveThresholds(warmSamples, absOpen, absClosed);
            if (derived) {
              armThreshold = derived.arm;
              closeThreshold = derived.close;
            }
          }
        }

        if (ear >= armThreshold) {
          if (closedSeen) {
            // 闭 → 睁：一次完整的眨眼
            blinks++;
            closedSeen = false;
            break;
          }
          armed = true;
        } else if (ear <= closeThreshold && armed && faceFrames > warmupFrames) {
          // 只有热身跑完、并且之前确实见过睁眼，才承认这一帧是「闭眼」
          closedSeen = true;
        }
      }

      if (!hasFace) {
        // 没脸的帧也要刷新百分比，否则用户一扭头进度条就冻住
        progressPct = computePercent({
          faceFrames: faceFrames, warmupFrames: warmupFrames,
          ratio: 0, peakRatio: maxTurnRatio, turnRatio: turnRatio, turnedAway: turnedAway,
          elapsedMs: Date.now() - startedAtMs
        });
      }
      progress(opts, hasFace ? (maxTurnRatio > 0.03 ? 'turn' : 'blink') : 'detect',
        ear === null ? null : round3(ear), hasFace, blinks, {
          arm: armThreshold, close: closeThreshold,
          baseline: derived ? derived.baseline : null,
          minEar: minEar === null ? null : round3(minEar),
          turn: round3(maxTurnRatio),
          turnThreshold: round3(turnRatio),
          percent: progressPct,
          phase: phase,
          frames: faceFrames
        });

      // 提示只在文案变化时回调：每帧都回调会让页面每秒重排十几次，看着闪
      var text = !hasFace
        ? '请把脸放入取景框'
        : (faceFrames <= warmupFrames ? '保持不动，正在校准…'
          : (turnedAway ? (turnHint || '很好，请把头转回正前方') : '请慢慢向左（或向右）转头，转到位后再转回来'));
      if (text !== lastHint) {
        lastHint = text;
        hint(opts, text);
      }

      if (intervalMs > 0) { await sleep(intervalMs); }
    }

    // 两条通路任一完成即通过：眨眼（桌面摄像头通常很灵）或转头（手机低分辨率下更可靠）
    if (blinks < 1 && !turned) {
      throw new FaceError('NO_BLINK', '没有检测到活体动作：请眨一下眼、或慢慢左右转头后重试');
    }
    return { ear: lastEar === null ? 0 : lastEar, byTurn: turned };
  }

  /* ------------------------------------------------------------ 抓拍 */

  /**
   * 抓当前帧，按最长边缩到 640 再编码成 JPEG。
   *
   * 为什么必须缩：手机前置常常给 1280×720 甚至更高，原图 base64 塞进 JSON
   * 请求体会是压缩后的好几倍，而底片只要够服务端做人脸比对就行 —— 长边 640
   * 配 0.7 质量，一般 30–60 KB，是这类用途的常见取值。
   *
   * 为什么不放大：源画面本来就比 640 小的时候，放大只会凭空多出字节，
   * 不会多出任何信息。
   *
   * 输出的是**未镜像**的原始方向：预览用 CSS transform: scaleX(-1) 做成镜子
   * 更符合直觉，但底片要存真实方向，否则底片和现场抓拍一个镜像一个不镜像。
   */
  function grabFrame(video) {
    var vw = video.videoWidth || CAPTURE_MAX_EDGE;
    var vh = video.videoHeight || CAPTURE_MAX_EDGE;
    var scale = Math.min(1, CAPTURE_MAX_EDGE / Math.max(vw, vh));
    var w = Math.max(1, Math.round(vw * scale));
    var h = Math.max(1, Math.round(vh * scale));

    var canvas = document.createElement('canvas');
    canvas.width = w;
    canvas.height = h;
    var ctx = canvas.getContext('2d');
    ctx.drawImage(video, 0, 0, w, h);

    return { imageBase64: canvas.toDataURL('image/jpeg', JPEG_QUALITY), width: w, height: h };
  }

  /* ------------------------------------------------------------ 主流程 */

  /**
   * 采集一张通过活体的人脸照。
   *
   * @param {object}   opts
   * @param {Element}  opts.video        必填，页面上的 <video>，画面直接接进去
   * @param {string}   [opts.modelPath]  模型目录，默认 /vendor/face-api/model
   * @param {Function} [opts.onHint]     (文字) => void，用来告诉用户现在该干什么
   * @param {Function} [opts.onProgress] ({stage,ear,hasFace,blinks}) => void
   * @param {number}   [opts.timeoutMs]  检测阶段超时，默认 45000
   * @param {number}   [opts.earOpen]    睁眼阈值；不传则用热身期自适应值（上限 0.28）
   * @param {number}   [opts.earClosed]  闭眼阈值；不传则用热身期自适应值（上限 0.20）
   * @param {boolean}  [opts.adaptiveEar] 默认 true：用本人静息 EAR 推导阈值。
   *                                      传 false（或显式给 earOpen/earClosed）则用固定阈值
   * @param {number}   [opts.warmupFrames] 热身帧数，默认 12
   * @param {number}   [opts.detectIntervalMs] 检测间隔，默认 60
   * @param {number}   [opts.inputSize]  检测输入边长（32 的倍数），默认 224
   * @param {number}   [opts.scoreThreshold] 人脸置信度门槛，默认 0.5
   * @returns {Promise<{imageBase64:string,width:number,height:number,ear:number}>}
   */
  function capture(opts) {
    opts = opts || {};

    // 先收掉上一次没走完的采集：两路同时抢摄像头，第二路必然拿不到画面，
    // 用户只会看到「打不开摄像头」，而真正的原因是上一次没被关掉。
    if (SESSION) { stopSession(SESSION); }

    var session = { video: opts.video || null, stream: null, cancelled: false };
    SESSION = session;
    return run(opts, session);
  }

  async function run(opts, session) {
    var video = opts.video;

    if (!video || !video.tagName || String(video.tagName).toUpperCase() !== 'VIDEO') {
      stopSession(session);
      throw new FaceError('UNKNOWN', 'capture 需要传入一个 <video> 元素（opts.video）');
    }

    if (!isSupported()) {
      stopSession(session);
      // 摄像头 API 只在 HTTPS 或 localhost 下暴露，这几乎是本地调试时
      // 唯一会踩的坑，所以直接把这句话写进错误信息里，省一轮排查。
      throw new FaceError('NO_CAMERA', global.isSecureContext === false
        ? '当前页面不是 HTTPS 也不是 localhost，浏览器不提供摄像头接口'
        : '当前浏览器不支持摄像头采集（navigator.mediaDevices.getUserMedia 不可用）');
    }

    var faceapi = global.faceapi;
    if (!faceapi || !faceapi.nets || typeof faceapi.detectSingleFace !== 'function') {
      stopSession(session);
      throw new FaceError('LIB_MISSING',
        '人脸库没加载：请在本脚本之前引入 /vendor/face-api/face-api.js');
    }

    var timeoutMs = numberOr(opts.timeoutMs, DEFAULT_TIMEOUT_MS);
    if (!(timeoutMs > 0)) { timeoutMs = DEFAULT_TIMEOUT_MS; }

    try {
      await openCamera(video, session, opts);
      await ensureModels(faceapi, opts, session);
      var blinkResult = await watchForBlink(faceapi, video, opts, session, timeoutMs);

      hint(opts, '活体通过，正在抓拍…');
      progress(opts, 'capture', round3(blinkResult.ear), true, 1);

      var shot = grabFrame(video);
      progress(opts, 'done', round3(blinkResult.ear), true, 1);

      return {
        imageBase64: shot.imageBase64,
        width: shot.width,
        height: shot.height,
        ear: round3(blinkResult.ear)
      };
    } finally {
      // 成功、失败、被取消，三条路都要走到这里。
      // 摄像头指示灯不能因为「抛异常了」就一直亮着 —— 这是本模块最硬的约束。
      stopSession(session);
    }
  }

  /* -------------------------------------------------------------- 导出 */

  global.FaceLiveness = {
    isSupported: isSupported,
    capture: capture,
    stop: stop,
    FaceError: FaceError,
    DEFAULT_MODEL_PATH: DEFAULT_MODEL_PATH,
    // 纯函数、不依赖 faceapi/DOM：真机上调参时可直接算一遍看阈值合不合适，
    // 自动化测试也用它（无需摄像头）。
    deriveThresholds: deriveThresholds,
    computePercent: computePercent
  };
})(window);