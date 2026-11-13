# 超级无敌宇宙霹雳摸头杀

安卓版摸头杀 GIF 生成器，把 [xhnav.com/petpet](https://xhnav.com/petpet/) 的网页玩法复刻成原生 App。万物皆可摸头 🐾

## 功能

- Photo Picker 免权限选图，也支持输入图片 URL
- 翻转、大小、压扁、速度、微调参数实时预览
- 5 帧 112×112 透明 GIF 生成，保存到相册或系统分享
- 明暗双主题持久化，纯离线可用（仅 URL 上传需要网络）
- 包名 com.xiaochangan.petpetgif，minSdk 26 / targetSdk 35

## 技术栈

Kotlin + Jetpack Compose + Canvas 逐帧渲染；GIF 编码器由 NeuQuant + LZW + AnimatedGifEncoder 移植到 Kotlin。

## 构建

需要 JDK 17、Android SDK 35。项目路径含中文时，建议通过英文目录联接构建：

    cd /d F:\Agent-Program\PETPETGIF_WORK
    gradlew.bat assembleRelease

生成文件位于 app/build/outputs/apk/release/。

## 文档

更完整的功能对照、渲染公式、GIF 编码验证与踩坑记录见 [IDEA.md](./IDEA.md)。
