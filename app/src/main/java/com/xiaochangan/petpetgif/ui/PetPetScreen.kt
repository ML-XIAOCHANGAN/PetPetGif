package com.xiaochangan.petpetgif.ui

import android.Manifest
import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.xiaochangan.petpetgif.engine.PetPetEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 摸头杀生成器主界面 —— 简洁明快版：
 * 无卡片堆叠，预览/操作/参数直接平铺，URL 上传收进可展开行。
 */
@Composable
fun PetPetScreen(vm: PetPetViewModel = viewModel()) {
    PetPetTheme(darkTheme = vm.darkTheme) {
        PetPetContent(vm)
    }
}

@Composable
private fun PetPetContent(vm: PetPetViewModel) {
    val sprite = vm.sprite
    val hand = vm.hand
    val params = vm.params
    val darkTheme = vm.darkTheme

    // 状态栏配色跟随主题
    val context = LocalContext.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = if (darkTheme) AndroidColor.BLACK else 0xFFFFF5D3.toInt()
        window.decorView.systemUiVisibility =
            if (darkTheme) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
    }

    // 参数/图片变化 → 重渲染 5 帧
    val frames = remember(params, sprite) {
        if (sprite != null) PetPetEngine.renderAllFrames(params, hand, sprite!!) else emptyList()
    }

    // 播放状态
    var playing by remember { mutableStateOf(true) }
    var currentFrame by remember { mutableIntStateOf(0) }
    var adjustMode by remember { mutableStateOf(false) }
    var urlExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(playing, params.fps, frames.size) {
        if (!playing || frames.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(params.frameDelayMs.toLong())
            currentFrame = (currentFrame + 1) % (PetPetEngine.MAX_FRAME + 1)
        }
    }

    // 选图
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { vm.loadImageUri(it) }
    }

    // 旧系统保存权限
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.saveToGallery() else vm.notify("需要存储权限才能保存到相册")
    }

    // 提示条
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm.notice) {
        vm.notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
        ) {
            // ---- 标题行（简洁：标题 + 主题切换）----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "🐾 摸头杀生成器",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { vm.toggleTheme() },
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    modifier = Modifier.size(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) { Text(if (darkTheme) "🌚" else "🌝", fontSize = 16.sp) }
            }

            // ---- 预览画布（网页版 .canvas：金边 + pixelated）----
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(300.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
                    .pointerInput(adjustMode) {
                        if (!adjustMode) return@pointerInput
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val s = size.width / PetPetEngine.OUT_SIZE.toFloat()
                            vm.nudgeSprite(
                                (dragAmount.x / s).roundToInt(),
                                (dragAmount.y / s).roundToInt(),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (frames.isEmpty()) {
                    Text(
                        "上传一张图片，开始摸头",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Canvas(Modifier.fillMaxSize()) {
                        val bmp = frames.getOrNull(currentFrame) ?: return@Canvas
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstSize = androidx.compose.ui.unit.IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.None,
                        )
                        if (adjustMode) {
                            val cf = PetPetEngine.getSpriteFrame(params, currentFrame)
                            val s = size.width / PetPetEngine.OUT_SIZE.toFloat()
                            drawRect(
                                color = Color.Red,
                                topLeft = androidx.compose.ui.geometry.Offset(cf.dx * s, cf.dy * s),
                                size = androidx.compose.ui.geometry.Size(abs(cf.dw) * s, cf.dh * s),
                                style = Stroke(width = 1.5f),
                            )
                        }
                    }
                }
            }

            // ---- 播放 + 图片操作一行 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PlayButton("⏮", 38.dp) {
                    playing = false
                    currentFrame = ((currentFrame - 1) % (PetPetEngine.MAX_FRAME + 1) + (PetPetEngine.MAX_FRAME + 1)) % (PetPetEngine.MAX_FRAME + 1)
                }
                PlayButton(if (playing) "⏸" else "▶", 38.dp) {
                    playing = !playing
                }
                PlayButton("⏭", 38.dp) {
                    playing = false
                    currentFrame = (currentFrame + 1) % (PetPetEngine.MAX_FRAME + 1)
                }
                Spacer(Modifier.weight(1f))
                // 图片文件名
                Text(
                    vm.fileName.ifEmpty { "示例图" },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Button(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) { Text("选图片", fontSize = 13.sp) }
                TextButton(onClick = { urlExpanded = !urlExpanded }) {
                    Text("URL", fontSize = 13.sp, color = MaterialTheme.colorScheme.secondary)
                }
            }

            // ---- URL 上传（默认收起）----
            if (urlExpanded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = vm.urlText,
                        onValueChange = { vm.urlText = it },
                        placeholder = { Text("输入图片 URL", fontSize = 13.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(6.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        ),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.loadImageUrl(vm.urlText) }) { Text("上传", fontSize = 13.sp) }
                }
            }

            // ---- 参数（直接平铺）----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                ParamToggleRow("翻转", vm.params.flip) { vm.setFlip(it) }
                ParamSliderRow(
                    label = "大小",
                    valueText = "${(params.scale * 100).roundToInt()}%",
                    value = (params.scale * 100).coerceIn(15f, 200f),
                    range = 15f..200f,
                ) { vm.setScale(it / 100f) }
                ParamSliderRow(
                    label = "压扁",
                    valueText = "${(params.squish * 100).roundToInt()}%",
                    value = (params.squish * 100).coerceIn(100f, 300f),
                    range = 100f..300f,
                ) { vm.setSquish(it / 100f) }
                ParamSliderRow(
                    label = "速度",
                    valueText = "${params.fps} fps",
                    value = params.fps.toFloat(),
                    range = 2f..60f,
                ) { vm.setFps(((it / 2).roundToInt() * 2).coerceIn(2, 60)) }
                ParamToggleRow("微调模式", adjustMode) {
                    adjustMode = it
                    if (it) playing = false
                }
                if (adjustMode) {
                    Text(
                        "拖动图片调整位置",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }

            // ---- 操作行 ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        vm.reset()
                        currentFrame = 0
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("重置") }
                Button(
                    onClick = { vm.exportGif() },
                    enabled = !vm.exporting,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (vm.exporting) "生成中…" else "生成 GIF")
                }
            }

            // ---- 生成结果 ----
            if (vm.resultFile != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = vm.resultFile,
                        contentDescription = "生成的摸头杀 GIF",
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(2.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    Text(
                        vm.resultInfo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 6.dp),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT < 29) {
                                permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                vm.saveToGallery()
                            }
                        }) { Text("保存到相册", fontSize = 13.sp) }
                        OutlinedButton(onClick = { vm.shareGif()?.let { context.startActivity(it) } }) {
                            Text("分享", fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PlayButton(symbol: String, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = Modifier.size(size),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) { Text(symbol, fontSize = 15.sp) }
}

@Composable
private fun ParamToggleRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedTrackColor = MaterialTheme.colorScheme.outlineVariant,
                uncheckedThumbColor = MaterialTheme.colorScheme.primaryContainer,
                uncheckedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
            ),
        )
    }
}

@Composable
private fun ParamSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChanged: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp, modifier = Modifier.width(52.dp))
        Slider(
            value = value,
            onValueChange = onChanged,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.tertiary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant,
                activeTickColor = MaterialTheme.colorScheme.onPrimary,
                inactiveTickColor = MaterialTheme.colorScheme.outline,
            ),
        )
        Text(
            valueText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(58.dp),
            textAlign = TextAlign.End,
        )
    }
}
