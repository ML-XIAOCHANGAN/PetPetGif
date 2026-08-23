# 项目07：超级无敌宇宙霹雳摸头（摸头杀生成器 Android 版）

> 把网页 https://xhnav.com/petpet/（摸头杀 GIF 生成器）复刻成安卓 App。
> 万物皆可摸头 🐾

## 状态：✅ V1.0.0 正式版已交付（2026-08-23）

- 正式包：`摸头杀生成器_V1.0.0_正式版.apk`（6.6MB，签名 keystore/petpet.jks）
- 模拟器全流程验收通过：启动 / 预览动画 / 参数调节 / 生成 GIF / 保存 / 分享
- 包名 `com.xiaochangan.petpetgif`，minSdk 26 / targetSdk 35，纯离线可用（URL 上传需网络）

## 功能（与网页版一一对应）

| 网页版 | App 版 | 说明 |
|---|---|---|
| 上传图片（本地/URL） | Photo Picker 免权限选图 + URL 输入框 | 图片等比缩放 ≤512px |
| 翻转 | 开关 | 原位水平镜像（图片反向，手不动，同网页） |
| 大小 (15-200%) | 滑块，默认 88% | scale 0.875 同网页 DEFAULTS |
| 压扁 (100-300%) | 滑块，默认 125% | squish 1.25 同网页 |
| 速度 (2-60fps) | 滑块，默认 16fps，偶数步进 | delay = 1000/fps ms |
| 微调模式 | 开关 + 按住拖动 | 拖动改 spriteX/spriteY，红框辅助线 |
| 预览播放 | 画布循环 + ⏮/⏸/⏭ | 最近邻放大保持像素感 |
| 重置 | 按钮 | 恢复全部默认 |
| 生成 gif | 按钮 | 5 帧 112×112 无限循环，透明底 |
| 保存 | 保存到相册 | API 29+ MediaStore（Pictures/摸头杀），26-28 请求存储权限 |
| —（网页无） | 分享 | FileProvider + 系统分享面板 |
| 🌝/🌚 主题切换 | 右上角按钮 | 复刻网页明暗双主题，持久化 |

## 主题（复刻网页版配色）

- **明色**（默认）：奶油黄底 #FFF5D3、白卡片、金色按钮 #FEDC7D+棕字 #943D1A、
  金边框 #F6B816、棕褐文字 #572E23、预览区 #FFFBF3
- **暗色**：黑底 #000000、深棕卡片 #291712、奶油字 #FEF1C7、暗金按钮 #FED86E
- **字体**：标题 Balsamiq Sans Bold、正文 Work Sans（网站同款，OFL 开源，已内置 res/font/）
- 配色变量源：`%TEMP%\petpet_ref\style.css` 的 `:root` 与 `[data-theme=dark]`

## 技术要点

- **渲染引擎** `engine/PetPetEngine.kt`：逐行移植 main.js —— 5 帧偏移表、压扁公式
  `dx=trunc(spriteX+off.x*squish*0.4)` / `dy=trunc(spriteY+off.y*squish*0.9)` /
  `dw=trunc((112+off.w*squish)*scale)`、手部 y = `max(0, trunc(dy*0.75-spriteY-0.5))`
- **GIF 编码器** `gif/`：NeuQuant(Anthony Dekker) + LZW + AnimatedGifEncoder(Kevin Weiner v1.03)
  从原始 Java 源码忠实移植 Kotlin；透明色自动处理（alpha<0x80 → 未用调色板索引）
- **编码器验证闭环**：JVM 单测生成 GIF → Python PIL 解码逐帧像素对比（平均误差 5.62，
  透明区保持），`tools/verify_gif.py` 可复跑
- **⚠️ 翻转 Canvas 变换坑**：Android Canvas 变换是后乘，必须 `translate(dx+dw,dy)` 再
  `scale(-1,1)`（镜像轴在 dx+dw 处），顺序反了图片会跑出画布
- **⚠️ Compose snapshot 状态线程坑**：ViewModel 里 `mutableStateOf` 只能在主线程读写，
  耗时操作放 `Dispatchers.Default`，状态变更回主线程（否则首启崩溃
  `Reading a state that was created after the snapshot...`）
- **⚠️ 中文路径单测坑**：JVM 单测在中文路径必 ClassNotFoundException，构建/单测必须走
  英文 junction `F:\Agent-Program\PETPETGIF_WORK`

## 构建

```bash
# 构建（必须从英文 junction 路径，JAVA_HOME 显式指 JDK17）
MSYS_NO_PATHCONV=1 cmd.exe /c "set JAVA_HOME=F:\JAVAML\JAVAJDK17&&cd /d F:\Agent-Program\PETPETGIF_WORK&&gradlew.bat assembleRelease --console=plain"

# 单测（GIF 编码器往返验证）
... gradlew.bat testDebugUnitTest --console=plain
# 出包后 Python 解码验证（可选）
python tools/verify_gif.py app/build/test-output/roundtrip.gif
```

- SDK：`F:\Agent-Program\PETPET_SDK`（local.properties 已指）
- 签名：`keystore/petpet.jks`，密码在 `local.properties`（**100 年有效期，勿丢失**）
- 技术栈：Kotlin + Compose（AGP 8.5.2 / Kotlin 1.9.24 / Compose BOM 2024.06 / Gradle 8.9）

## 素材

- `app/src/main/res/drawable-nodpi/petpet_hand.png`：560×112 五帧摸头手精灵图（网页原版）
- `app/src/main/res/drawable-nodpi/petpet_sample.png`：网页同款示例图（小猪）
- 应用图标：PIL 生成天空蓝底白色爪印

## 参考

- 网页源码参考：`%TEMP%\petpet_ref\`（main.js / sprite.png / 原始 Java GIF 编码器）
- 网页版原作：benisland.neocities.org/petpet/（摸头杀生成器，gif.js 生成）
