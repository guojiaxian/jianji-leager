#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""用真实截图测试 GLM-4.6V 订单识别效果"""
import base64, json, sys, urllib.request

API_URL = "https://open.bigmodel.cn/api/paas/v4/chat/completions"
API_KEY = "51bcfc59b56847f683fd572744174923.O2o5N17YaaINNKf8"

PROMPT = """你是记账助手。分析这张支付/订单截图，提取信息并严格按以下JSON格式返回：
{
  "platform": "截图所属App（京东/拼多多/淘宝/微信/支付宝/纸质小票/其他）",
  "amount": 实付金额（数字，优先取"实付款/实付/合计/转账金额"，不含货币符号）,
  "date": "YYYY-MM-DD（下单/支付日期，无年份默认2026年；无法判断填null）",
  "time": "HH:MM（下单/支付时间；无法判断填null）",
  "items": ["购买的具体物品名称，逐项列出；无法判断返回空数组[]"],
  "merchant": "商家/店铺/收款人名称（无法判断填null）",
  "category": "消费分类，只能从：餐饮/交通/购物/娱乐/居住/医疗/教育/通讯/其他 中选（无法判断填null）"
}
判断platform的关键规则：
- platform指截图来自哪个App，看页面UI特征（顶部logo、配色、页面标题），而不是支付渠道。
- 京东订单页即使显示"微信支付"（支付方式），platform仍是"京东"。淘宝橙色UI、拼多多红色UI同理。
- 淘宝订单详情页特征：顶部"交易成功"状态、橙色"已签收"标签、"88VIP好评率"、"假一赔四"、"大促价保"、"再买一单"、"进店逛逛"按钮。
- 京东订单详情页特征："取件码"数字、"卖了换钱"、"再次购买"红色按钮、"安心保障"模块、京东物流。
- 微信：绿色"微信支付"账单详情页、微信聊天里的转账页面。
- 支付宝：蓝色UI、"支付宝"字样、账单详情页。账单详情页若含"账单分类"字段（如"餐饮美食"）或"碰一碰"相关字样，是支付宝账单。
- 纸质小票：打印的热敏纸购物小票照片。
其他规则：
1. 金额必须是用户实付总额，区分"商品金额"与"实付款"。
2. items只列具体商品名（如"小米温湿度计"），不含金额；给个人的转账无法判断物品，返回[]。
3. category推断依据：电商购物→购物；餐饮店/食品→餐饮；打车/加油→交通；个人转账且无信息→其他。
4. 只返回JSON本身，不要其他文字、不要markdown代码块。"""

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
        print(f"===== {image_path} ===== RAW: {raw[:1200]}")
        data = json.loads(raw)
        content = (data["choices"][0]["message"].get("content") or "").strip()
        if content.startswith("```"):
            content = content.split("```")[1].lstrip("json").strip()
        print(f"----- parsed: {content}")
        print()
    except urllib.error.HTTPError as e:
        print(f"===== {image_path} ===== HTTP {e.code}: {e.read().decode()[:500]}\n")
    except Exception as e:
        print(f"===== {image_path} ===== ERROR: {e}\n")

if __name__ == "__main__":
    for img in sys.argv[1:]:
        test(img)
