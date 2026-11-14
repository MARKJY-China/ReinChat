package com.reinchat.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reinchat.app.data.ApiProtocol
import com.reinchat.app.data.ProviderConfig
import com.reinchat.app.data.newId
import com.reinchat.app.net.ApiEndpoint
import com.reinchat.app.ui.components.GlassIconButton
import com.reinchat.app.ui.components.GlassInputField
import com.reinchat.app.ui.components.LiquidPrimaryButton
import com.reinchat.app.ui.components.clickableNoRipple
import com.reinchat.app.ui.components.liquidGlass
import com.reinchat.app.ui.components.pressScale
import com.reinchat.app.ui.theme.LiquidEase
import com.reinchat.app.ui.theme.LiquidEaseOutSoft
import com.reinchat.app.ui.theme.ReinColors

private data class ProviderTemplate(
    val label: String,
    val name: String,
    val protocol: ApiProtocol,
    val baseUrl: String,
    val model: String,
)

private val templates = listOf(
    ProviderTemplate("OpenAI", "OpenAI", ApiProtocol.RESPONSES, "https://api.openai.com/v1", "gpt-4.1-mini"),
    ProviderTemplate("DeepSeek", "DeepSeek", ApiProtocol.CHAT_COMPLETIONS, "https://api.deepseek.com/v1", "deepseek-chat"),
    ProviderTemplate("OpenRouter", "OpenRouter", ApiProtocol.CHAT_COMPLETIONS, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    ProviderTemplate("自定义", "自定义服务商", ApiProtocol.CHAT_COMPLETIONS, "", ""),
)

@Composable
fun ProviderEditorScreen(
    initial: ProviderConfig?,
    signedIn: Boolean,
    onBack: () -> Unit,
    onSave: (ProviderConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isNew = initial == null
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var protocolName by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.protocol?.name ?: ApiProtocol.CHAT_COMPLETIONS.name)
    }
    var baseUrl by rememberSaveable(initial?.id) { mutableStateOf(initial?.baseUrl ?: "") }
    var model by rememberSaveable(initial?.id) { mutableStateOf(initial?.model ?: "") }
    var apiKey by rememberSaveable(initial?.id) { mutableStateOf(initial?.apiKey ?: "") }
    var systemPrompt by rememberSaveable(initial?.id) { mutableStateOf(initial?.systemPrompt ?: "") }
    var temperature by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.temperature?.toString() ?: "")
    }
    var maxTokens by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.maxTokens?.toString() ?: "")
    }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var localOnly by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.localOnly ?: true)
    }
    var reasoningEffort by rememberSaveable(initial?.id) {
        mutableStateOf(initial?.reasoningEffort ?: "")
    }
    var advancedOpen by rememberSaveable { mutableStateOf(initial?.temperature != null || initial?.maxTokens != null) }
    val effectiveLocalOnly = !signedIn || localOnly
    var formError by remember { mutableStateOf<String?>(null) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var baseError by remember { mutableStateOf<String?>(null) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var paramsError by remember { mutableStateOf<String?>(null) }
    val protocol = runCatching { ApiProtocol.valueOf(protocolName) }
        .getOrDefault(ApiProtocol.CHAT_COMPLETIONS)
    val arrowRotation by animateFloatAsState(
        targetValue = if (advancedOpen) 180f else 0f,
        animationSpec = tween(260, easing = LiquidEase),
        label = "advancedArrow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color(0xFF0A1411),
                    0.44f to Color(0xFF060C0A),
                    1f to Color(0xFF040706),
                )
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(ReinColors.Mint.copy(alpha = 0.14f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.86f, size.height * 0.04f),
                    radius = size.width * 0.75f,
                )
            )
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(ReinColors.Violet.copy(alpha = 0.08f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(size.width * 0.02f, size.height * 0.9f),
                    radius = size.width * 0.8f,
                )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 18.dp, top = 8.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassIconButton(
                    icon = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "返回",
                    onClick = onBack,
                    size = 40.dp,
                    iconSize = 18.dp,
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isNew) "添加服务商" else "编辑服务商",
                        style = MaterialTheme.typography.headlineSmall.copy(fontSize = 19.sp),
                        color = ReinColors.TextPrimary,
                    )
                    Text(
                        text = "Responses 或 Chat Completions",
                        style = MaterialTheme.typography.labelMedium,
                        color = ReinColors.TextTertiary,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
            ) {
                Spacer(Modifier.height(8.dp))

                if (isNew) {
                    Text(
                        text = "从模板开始",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                        color = ReinColors.TextTertiary,
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        templates.forEach { template ->
                            TemplateChip(
                                label = template.label,
                                onClick = {
                                    name = template.name
                                    protocolName = template.protocol.name
                                    baseUrl = template.baseUrl
                                    model = template.model
                                    nameError = null
                                    baseError = null
                                    modelError = null
                                    formError = null
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(22.dp))
                }

                SectionLabel("连接方式")
                Spacer(Modifier.height(9.dp))
                ProtocolSelector(
                    selected = protocol,
                    onSelect = { protocolName = it.name },
                )
                Spacer(Modifier.height(20.dp))

                GlassInputField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = "显示名称",
                    placeholder = "例如 OpenAI、DeepSeek",
                    isError = nameError != null,
                    supportingText = nameError,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                )
                Spacer(Modifier.height(15.dp))

                GlassInputField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        baseError = null
                    },
                    label = "Base URL",
                    placeholder = "https://api.openai.com/v1",
                    isError = baseError != null,
                    supportingText = baseError ?: endpointHint(baseUrl, protocol),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(15.dp))

                GlassInputField(
                    value = model,
                    onValueChange = {
                        model = it
                        modelError = null
                    },
                    label = "模型（可填多个）",
                    placeholder = if (protocol == ApiProtocol.RESPONSES) {
                        "gpt-4.1-mini, gpt-4.1"
                    } else {
                        "gpt-4o-mini, deepseek-chat"
                    },
                    isError = modelError != null,
                    supportingText = modelError
                        ?: "多个模型用英文逗号分隔；对话页可随时切换，使用精确模型 ID",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                )
                Spacer(Modifier.height(15.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = RoundedCornerShape(17.dp),
                            topTint = Color.White.copy(alpha = 0.045f),
                            bottomTint = Color.White.copy(alpha = 0.018f),
                        )
                        .padding(13.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "思考强度",
                            style = MaterialTheme.typography.titleMedium,
                            color = ReinColors.TextPrimary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "reasoning_effort",
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.sp),
                            color = ReinColors.TextTertiary,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "低 = low · 高 = high · 最高 = max；自动则使用模型默认值",
                        style = MaterialTheme.typography.labelMedium.copy(lineHeight = 15.sp),
                        color = ReinColors.TextTertiary,
                    )
                    Spacer(Modifier.height(9.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        listOf(
                            "" to "自动",
                            "low" to "低",
                            "high" to "高",
                            "max" to "最高",
                        ).forEach { (value, label) ->
                            val selected = reasoningEffort == value
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(
                                        if (selected) ReinColors.Mint.copy(alpha = 0.13f)
                                        else Color.White.copy(alpha = 0.035f)
                                    )
                                    .border(
                                        1.dp,
                                        if (selected) ReinColors.Mint.copy(alpha = 0.22f)
                                        else Color.White.copy(alpha = 0.06f),
                                        RoundedCornerShape(9.dp),
                                    )
                                    .clickableNoRipple { reasoningEffort = value }
                                    .padding(vertical = 7.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                                    color = if (selected) ReinColors.Mint else ReinColors.TextSecondary,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(15.dp))

                GlassInputField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = "API Key",
                    placeholder = "sk-…（可留空）",
                    visualTransformation = if (showKey) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    supportingText = "加密保存在应用私有目录；留空时不发送 Authorization 头；会自动清理中文、全角与隐藏字符",
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next,
                    ),
                    trailing = {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Color.White.copy(alpha = 0.045f))
                                .clickableNoRipple { showKey = !showKey },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showKey) "隐藏密钥" else "显示密钥",
                                tint = ReinColors.TextTertiary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                )
                Spacer(Modifier.height(15.dp))

                GlassInputField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = "系统提示词（可选）",
                    placeholder = "为这位助手设定身份、语气或回答边界",
                    singleLine = false,
                    minLines = 3,
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Default,
                    ),
                )
                Spacer(Modifier.height(17.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .liquidGlass(
                            shape = RoundedCornerShape(18.dp),
                            topTint = Color.White.copy(alpha = 0.055f),
                            bottomTint = Color.White.copy(alpha = 0.02f),
                        )
                        .clickable(
                            enabled = signedIn,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { localOnly = !localOnly }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(33.dp)
                            .clip(CircleShape)
                            .background(ReinColors.Mint.copy(alpha = 0.08f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = null,
                            tint = if (effectiveLocalOnly) ReinColors.TextTertiary else ReinColors.Mint,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "仅保存在本机",
                            style = MaterialTheme.typography.titleMedium,
                            color = ReinColors.TextPrimary,
                        )
                        Text(
                            text = when {
                                !signedIn -> "未登录时固定开启；登录后可关闭并同步"
                                effectiveLocalOnly -> "此服务商的配置和 API Key 不会上传"
                                else -> "配置和 API Key 会加密同步到你的账号"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp),
                            color = if (effectiveLocalOnly) {
                                ReinColors.TextTertiary
                            } else {
                                ReinColors.Mint.copy(alpha = 0.82f)
                            },
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = effectiveLocalOnly,
                        onCheckedChange = if (signedIn) {
                            { localOnly = it }
                        } else null,
                        enabled = signedIn,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF052019),
                            checkedTrackColor = ReinColors.Mint,
                            checkedBorderColor = ReinColors.Mint,
                            uncheckedThumbColor = ReinColors.TextTertiary,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.10f),
                            uncheckedBorderColor = Color.White.copy(alpha = 0.12f),
                            disabledCheckedThumbColor = Color(0xFF052019).copy(alpha = 0.5f),
                            disabledCheckedTrackColor = ReinColors.Mint.copy(alpha = 0.35f),
                            disabledUncheckedThumbColor = ReinColors.TextTertiary.copy(alpha = 0.4f),
                            disabledUncheckedTrackColor = Color.White.copy(alpha = 0.05f),
                        ),
                    )
                }
                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(Color.White.copy(alpha = 0.032f))
                        .clickableNoRipple { advancedOpen = !advancedOpen }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "高级参数",
                            style = MaterialTheme.typography.titleMedium,
                            color = ReinColors.TextPrimary.copy(alpha = 0.9f),
                        )
                        Text(
                            text = "temperature · 最大输出长度",
                            style = MaterialTheme.typography.labelMedium,
                            color = ReinColors.TextTertiary,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = ReinColors.TextTertiary,
                        modifier = Modifier.size(18.dp).rotate(arrowRotation),
                    )
                }
                AnimatedVisibility(
                    visible = advancedOpen,
                    enter = fadeIn(tween(180)) + expandVertically(tween(260, easing = LiquidEaseOutSoft)),
                    exit = fadeOut(tween(100)) + shrinkVertically(tween(180)),
                ) {
                    Column {
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                            GlassInputField(
                                value = temperature,
                                onValueChange = {
                                    temperature = it.filter { ch -> ch.isDigit() || ch == '.' }
                                    paramsError = null
                                },
                                label = "Temperature",
                                placeholder = "默认",
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Next,
                                ),
                            )
                            GlassInputField(
                                value = maxTokens,
                                onValueChange = {
                                    maxTokens = it.filter(Char::isDigit)
                                    paramsError = null
                                },
                                label = "最大输出",
                                placeholder = "默认",
                                modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = {}),
                            )
                        }
                        if (paramsError != null) {
                            Text(
                                text = paramsError!!,
                                style = MaterialTheme.typography.labelMedium,
                                color = ReinColors.Danger.copy(alpha = 0.9f),
                                modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                            )
                        }
                    }
                }

                if (formError != null) {
                    Spacer(Modifier.height(15.dp))
                    Text(
                        text = formError!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ReinColors.Danger.copy(alpha = 0.9f),
                    )
                }
                Spacer(Modifier.height(28.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xF0080E0C), Color(0xFF080E0C))
                        )
                    )
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 12.dp),
            ) {
                LiquidPrimaryButton(
                    text = if (isNew) "保存并开始对话" else "保存修改",
                    onClick = {
                        var valid = true
                        nameError = if (name.isBlank()) "请填写显示名称".also { valid = false } else null
                        val urlProblem = ApiEndpoint.validationError(baseUrl)
                        baseError = urlProblem.also { if (it != null) valid = false }
                        modelError = if (model.isBlank()) "请填写模型名称".also { valid = false } else null
                        val parsedTemperature = temperature.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                        val parsedTokens = maxTokens.trim().takeIf { it.isNotEmpty() }?.toIntOrNull()
                        paramsError = when {
                            temperature.isNotBlank() && parsedTemperature == null ->
                                "Temperature 需为数字"
                            parsedTemperature != null && parsedTemperature !in 0.0..2.0 ->
                                "Temperature 需在 0 到 2 之间"
                            maxTokens.isNotBlank() && parsedTokens == null ->
                                "最大输出需为整数"
                            parsedTokens != null && parsedTokens <= 0 ->
                                "最大输出需大于 0"
                            else -> null
                        }
                        if (paramsError != null) {
                            valid = false
                            advancedOpen = true
                        }
                        formError = if (valid) null else "请检查标注的配置项"
                        if (valid) {
                            onSave(
                                ProviderConfig(
                                    id = initial?.id ?: newId(),
                                    name = name.trim(),
                                    protocol = protocol,
                                    baseUrl = baseUrl.trim(),
                                    apiKey = apiKey.trim(),
                                    model = model.trim(),
                                    systemPrompt = systemPrompt.trim(),
                                    temperature = parsedTemperature,
                                    maxTokens = parsedTokens,
                                    reasoningEffort = reasoningEffort,
                                    localOnly = effectiveLocalOnly,
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "配置只保存在本机 · 随时可以修改",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 9.5.sp),
                    color = ReinColors.TextTertiary.copy(alpha = 0.55f),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        color = ReinColors.TextTertiary,
    )
}

@Composable
private fun ProtocolSelector(
    selected: ApiProtocol,
    onSelect: (ApiProtocol) -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.03f), shape)
                .border(1.dp, Color.White.copy(alpha = 0.06f), shape)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ProtocolOption(
                label = "Responses",
                selected = selected == ApiProtocol.RESPONSES,
                onClick = { onSelect(ApiProtocol.RESPONSES) },
                modifier = Modifier.weight(1f),
            )
            ProtocolOption(
                label = "Chat Completions",
                selected = selected == ApiProtocol.CHAT_COMPLETIONS,
                onClick = { onSelect(ApiProtocol.CHAT_COMPLETIONS) },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = if (selected == ApiProtocol.RESPONSES) {
                "适用于 OpenAI 原生 Responses API，支持流式文本与图片输入。"
            } else {
                "兼容绝大多数 OpenAI 风格接口，包括 DeepSeek、OpenRouter 与自建服务。"
            },
            style = MaterialTheme.typography.labelMedium.copy(lineHeight = 16.sp),
            color = ReinColors.TextTertiary.copy(alpha = 0.78f),
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun ProtocolOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val background by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.10f) else Color.Transparent,
        animationSpec = tween(240, easing = LiquidEase),
        label = "protocolBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) ReinColors.TextPrimary else ReinColors.TextTertiary,
        animationSpec = tween(240, easing = LiquidEase),
        label = "protocolText",
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .then(
                if (selected) Modifier.border(
                    1.dp,
                    ReinColors.Mint.copy(alpha = 0.18f),
                    RoundedCornerShape(12.dp)
                ) else Modifier
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
            color = textColor,
            maxLines = 1,
        )
    }
}

@Composable
private fun TemplateChip(label: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressScale(interaction, 0.96f)
            .liquidGlass(
                shape = RoundedCornerShape(50),
                topTint = Color.White.copy(alpha = 0.07f),
                bottomTint = Color.White.copy(alpha = 0.025f),
            )
            .clip(RoundedCornerShape(50))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = ReinColors.TextSecondary,
        )
    }
}

private fun endpointHint(baseUrl: String, protocol: ApiProtocol): String {
    if (baseUrl.isBlank()) return "填写到 /v1 一级，或直接填写完整接口地址"
    if (ApiEndpoint.validationError(baseUrl) != null) return "填写到 /v1 一级，或直接填写完整接口地址"
    return "请求地址：${runCatching { ApiEndpoint.endpointUrl(baseUrl, protocol) }.getOrDefault("")}"
}
