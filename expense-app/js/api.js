// ===== OCR API Layer =====

// 默认系统提示词
const DEFAULT_OCR_PROMPT = `你是记账助手。这张截图可能含一笔或多笔订单/支付记录，请逐笔识别，提取每笔信息并严格按以下JSON数组返回：
[
  {
    "platform": "该笔截图所属App（京东/拼多多/淘宝/微信/支付宝/纸质小票/其他）",
    "amount": 实付金额（数字，优先取"实付款/实付/合计/转账金额"，不含货币符号）,
    "date": "YYYY-MM-DD（下单/支付日期；无年份默认为2026年）",
    "time": "HH:MM（交易时间；无法判断填null）",
    "items": ["该笔购买的具体物品名称，逐项列出；无法判断返回空数组[]"],
    "merchant": "商家/店铺/收款人名称（无法判断填null）",
    "category": "消费分类（餐饮/交通/购物/娱乐/居住/医疗/教育/通讯/其他；无法判断填null）"
  }
]

判断platform关键规则：
- platform看截图UI特征（顶部标题、筛选标签、记法）而非支付渠道。京东即使显示"微信支付"仍是京东；淘宝橙色UI、拼多多红色UI同理。
- 淘宝订单详情页特征：顶部"交易成功"、"88VIP好评率"、"假一赔四"、"大促价保"、"再买一单"、"进店逛逛"。
- 京东订单详情页特征："取件码"数字、"卖了换钱"、"再次购买"红色按钮、"安心保障"模块。
- 微信账单列表页特征：顶部居中标题"微信支付"、每条记录含"使用零钱支付/支付方式"、头像是灰色圆形。
- 支付宝账单列表页特征：顶部搜索栏提示"搜索交易记录"、横向筛选标签"全部/支出/转账/退款/订单"、"筛选"按钮、"本月已省xx元"、每笔金额为负数（如-4.83）且按类别分组（餐饮美食/日用百货/医疗健康等）。只要出现"全部/支出/转账/退款/订单"筛选或"本月已省"，即使金额都是负数，也判定为支付宝，而不是微信。
- 纸质小票：打印的热敏纸购物小票照片。

其他规则：
1. 金额是每笔实付总额，区分商品金额与实付款。
2. items只列具体商品名（如"小米温湿度计"）；给个人转账无法判断物品返回[]。
3. category推断：电商购物→购物；餐饮店/食品→餐饮；打车/加油→交通；个人转账且无信息→其他。
4. 每张截图中的每一笔订单都要识别，订单数量必须与图上可见的一致；若只有一笔，也返回含一个元素的数组。
5. 只返回JSON本身，不要其他文字、不要markdown代码块。`;

// 当前日期串（用于无日期回退）
function getTodayStrApi() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

// 归一化单笔订单字段（兼容 platform / order_type）
function normalizeOrder(o) {
  return {
    platform: o.platform || o.order_type || null,
    amount: typeof o.amount === 'number' ? o.amount : parseFloat(o.amount),
    date: o.date || getTodayStrApi(),
    time: o.time || null,
    items: Array.isArray(o.items) ? o.items.filter(Boolean) : [],
    merchant: o.merchant || o.note || null,
    category: o.category || null
  };
}

// 获取有效的提示词
function getEffectivePrompt() {
  const saved = getOcrPrompt();
  return saved || DEFAULT_OCR_PROMPT;
}

// 将图片文件转为 base64
function fileToBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

// 调用大模型API进行OCR识别
async function ocrRecognize(imageBase64) {
  const settings = getApiSettings();

  if (!settings.key) {
    throw new Error('请先在设置中配置API Key');
  }

  if (!settings.url) {
    throw new Error('请先在设置中配置API地址');
  }

  const prompt = getEffectivePrompt();

  const payload = {
    model: settings.model || 'glm-4.6v',
    messages: [
      {
        role: 'user',
        content: [
          { type: 'text', text: prompt },
          {
            type: 'image_url',
            image_url: {
              url: imageBase64,
              detail: 'high'
            }
          }
        ]
      }
    ],
    max_tokens: 2000,
    temperature: 0.1,
    thinking: { type: 'disabled' }
  };

  const response = await fetch(settings.url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${settings.key}`
    },
    body: JSON.stringify(payload)
  });

  if (!response.ok) {
    const errText = await response.text().catch(() => '');
    throw new Error(`API请求失败 (${response.status}): ${errText || response.statusText}`);
  }

  const data = await response.json();
  const content = data.choices?.[0]?.message?.content || '';

  // 尝试提取 JSON
  let parsed;
  try {
    // 先尝试直接解析
    parsed = JSON.parse(content);
  } catch (e) {
    // 尝试从代码块中提取
    const jsonMatch = content.match(/```(?:json)?\s*([\s\S]*?)```/);
    if (jsonMatch) {
      try {
        parsed = JSON.parse(jsonMatch[1].trim());
      } catch (e2) {}
    }
    // 尝试从文本中提取 {...}
    if (!parsed) {
      const braceMatch = content.match(/\{[\s\S]*\}/);
      if (braceMatch) {
        try {
          parsed = JSON.parse(braceMatch[0]);
        } catch (e3) {}
      }
    }
  }
  if (!parsed) {
    throw new Error('无法解析API返回结果，请检查API返回格式');
  }

  // 兼容三种形态：单对象 / 对象数组 / {"orders":[...] }
  let list;
  if (Array.isArray(parsed)) list = parsed;
  else if (Array.isArray(parsed.orders)) list = parsed.orders;
  else list = [parsed];

  // 每笔归一化，过滤无效项
  return list
    .map(normalizeOrder)
    .filter(o => o && (!Number.isNaN(o.amount) || o.platform || o.merchant));
}
