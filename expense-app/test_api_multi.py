#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""测试多笔订单截图识别：返回JSON数组，逐笔识别"""
import base64, json, sys, urllib.request

API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
API_KEY = "51bcfc59b56847f683fd572744174923.O2o5N17YaaINNKf8"

PROMPT = """你是记账助手。这张截图可能含一笔或多笔订单/支付记录，请逐笔识别，提取每笔信息并严格按以下JSON数组返回：
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
- 京东订单详情页特征："取件码"数字、"卖了换钱"、"再次购买"红按钮、"安心保障"模块。
- 微信账单列表页特征：顶部居中标题"微信支付"、每条记录含"使用零钱支付/支付方式"、头像是灰色圆形。
- 支付宝账单列表页特征：顶部搜索栏提示"搜索交易记录"、横向筛选标签"全部/支出/转账/退款/订单"、"筛选"按钮、"本月已省xx元"、每笔金额为负数（如-4.83）且按类别分组（餐饮美食/日用百货/医疗健康等）。只要出现"全部/支出/转账/退款/订单"筛选或"本月已省"，即使金额都是负数，也判定为支付宝，而不是微信。
- 纸质小票：打印的热敏纸购物小票照片。

其他规则：
1. 金额是每笔实付总额，区分商品金额与实付款。
2. items只列具体商品名；给个人转账无法判断物品返回[]。
3. category推断：电商→购物；餐饮店/食品→餐饮；打车/加油→交通；个人转账无信息→其他。
4. 每张截图中每一笔订单都要识别，数量必须与图上可见的订单一致。
5. 若截图只有一笔，也返回含一个元素的数组。
6. 只返回JSON本身，不要其他文字、不要markdown代码块。"""

def test(image_path):
    with open(image_path, "rb") as f:
        b64 = base64.b64encode(f.read()).decode()

    payload = {
        "model": "glm-4.6v",
        "messages": [{
            "role": "user",
            "content": [
                {"type": "text", "text": PROMPT},
                {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{b64}"}}
            ]
        }],
        "max_tokens": 2000,
        "temperature": 0.1,
        "thinking": {"type": "disabled"}
    }

    req = urllib.request.Request(API_URL, data=json.dumps(payload).encode(),
                                 headers={"Content-Type": "application/json",
                                          "Authorization": f"Bearer {API_KEY}"})
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            raw = resp.read().decode()
        print(f"===== {image_path} =====")
        print(f"RAW: {raw[:1500]}")
        print()
    except urllib.error.HTTPError as e:
        print(f"===== {image_path} ===== HTTP {e.code}: {e.read().decode()[:500]}\n")
    except Exception as e:
        print(f"===== {image_path} ===== ERROR: {e}\n")

if __name__ == "__main__":
    for img in sys.argv[1:]:
        test(img)