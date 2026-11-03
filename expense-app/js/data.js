// ===== Data Layer - localStorage =====

const STORAGE_KEY = 'jianji_transactions';

// ===== 定制分类图标集（V1）：1.6px 描边 · 墨色单色（currentColor 随主题） =====
// 分类数据的 icon 字段存图标 id；渲染时 id 命中图标集则用 SVG，
// 否则按分类名回落，再否则原样显示（兼容旧 Emoji 与自定义分类）
const CATEGORY_ICON_SET = {
  meals: {
    name: '餐饮',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 12.5h16"/><path d="M5.5 12.5a6.5 6.5 0 0 0 13 0"/><path d="M9.5 9.5V6.8M14.5 9.5V6.2"/></svg>'
  },
  transport: {
    name: '交通',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M19 17h2c.6 0 1-.4 1-1v-3c0-.9-.7-1.7-1.5-1.9C18.7 10.6 16 10 16 10s-1.3-1.4-2.2-2.3c-.5-.4-1.1-.7-1.8-.7H5c-.6 0-1.1.4-1.4.9l-1.4 2.9A3.7 3.7 0 0 0 2 12v4c0 .6.4 1 1 1h2"/><circle cx="7" cy="17" r="2"/><path d="M9 17h6"/><circle cx="17" cy="17" r="2"/></svg>'
  },
  shopping: {
    name: '购物',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M6 8h12l1 12H5L6 8Z"/><path d="M9 10.5V7a3 3 0 0 1 6 0v3.5"/></svg>'
  },
  fun: {
    name: '娱乐',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3" y="8" width="18" height="9" rx="4.5"/><path d="M8 11v3M6.5 12.5h3"/><path d="M15.2 11.5h.01M17.6 13.5h.01"/></svg>'
  },
  home: {
    name: '居住',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 11l8-7 8 7"/><path d="M6 9.5V20h12V9.5"/><path d="M10 20v-5.5h4V20"/></svg>'
  },
  health: {
    name: '医疗',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M12 8.5v7M8.5 12h7"/></svg>'
  },
  study: {
    name: '教育',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M5 4h11a2 2 0 0 1 2 2v14H7a2 2 0 0 1-2-2V4Z"/><path d="M5 17a2 2 0 0 1 2-2h11"/><path d="M9 8.5h6"/></svg>'
  },
  phone: {
    name: '通讯',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="7" y="3" width="10" height="18" rx="2.5"/><path d="M11 17.5h2"/></svg>'
  },
  misc: {
    name: '其他',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 11.5l-7.8 7.8a5 5 0 0 1-7-7L13 4.4a3.3 3.3 0 0 1 4.7 4.7L10 16.8a1.7 1.7 0 0 1-2.4-2.4l7-7"/></svg>'
  },
  salary: {
    name: '工资',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="8"/><path d="M9 8.5l3 3.5 3-3.5M12 12v4M9.5 13.5h5M9.5 15.5h5"/></svg>'
  },
  bonus: {
    name: '奖金',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M8.5 3.5L11 10M15.5 3.5L13 10"/><circle cx="12" cy="14.5" r="4.2"/><path d="M10.5 14.5l1.2 1.2 2-2.2"/></svg>'
  },
  parttime: {
    name: '兼职',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="3.5" y="8" width="17" height="12" rx="2"/><path d="M9 8V6.5A1.5 1.5 0 0 1 10.5 5h3A1.5 1.5 0 0 1 15 6.5V8"/><path d="M3.5 13h17"/></svg>'
  },
  invest: {
    name: '理财',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M4 17l5-5 3.5 3.5L20 9"/><path d="M15 9h5v5"/></svg>'
  },
  redpacket: {
    name: '红包',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="5" y="3.5" width="14" height="17" rx="2.5"/><path d="M5 8.5c2.2 1.6 4.6 2.4 7 2.4s4.8-.8 7-2.4"/><circle cx="12" cy="14.5" r="1.6"/></svg>'
  },
  refund: {
    name: '退款',
    svg: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 14l-4-4 4-4"/><path d="M5 10h9a5 5 0 0 1 0 10h-3"/></svg>'
  }
};

// 图标渲染：icon 为图标集 id 时用 SVG；未命中按分类名回落；再否则原样显示（兼容旧 Emoji）
function catIconHtml(icon, name) {
  const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, ch =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
  const byId = CATEGORY_ICON_SET[icon];
  if (byId) return byId.svg;
  const byName = Object.values(CATEGORY_ICON_SET).find(i => i.name === name);
  if (byName) return byName.svg;
  return esc(icon || '📌');
}

// 预设分类模板
const PRESET_CATEGORIES = [
  { name: '餐饮', icon: 'meals', subcategories: ['早餐', '午餐', '晚餐', '外卖', '奶茶', '咖啡', '水果', '零食'] },
  { name: '交通', icon: 'transport', subcategories: ['公交', '地铁', '打车', '加油', '停车', '火车', '飞机'] },
  { name: '购物', icon: 'shopping', subcategories: ['超市', '服装', '电子产品', '日用品', '美妆', '家居'] },
  { name: '娱乐', icon: 'fun', subcategories: ['电影', '游戏', '健身', '旅游', 'KTV', '运动'] },
  { name: '居住', icon: 'home', subcategories: ['房租', '水电', '物业', '维修', '网络', '燃气'] },
  { name: '医疗', icon: 'health', subcategories: ['看病', '买药', '体检', '牙科'] },
  { name: '教育', icon: 'study', subcategories: ['课程', '书籍', '文具', '培训'] },
  { name: '通讯', icon: 'phone', subcategories: ['话费', '流量', '设备'] },
  { name: '其他', icon: 'misc', subcategories: ['转账', '红包', '礼物', '其他'] }
];

// 预设收入分类
const PRESET_INCOME_CATEGORIES = [
  { name: '工资', icon: 'salary', subcategories: [] },
  { name: '奖金', icon: 'bonus', subcategories: [] },
  { name: '兼职', icon: 'parttime', subcategories: [] },
  { name: '理财', icon: 'invest', subcategories: [] },
  { name: '红包', icon: 'redpacket', subcategories: [] },
  { name: '退款', icon: 'refund', subcategories: [] },
  { name: '其他收入', icon: 'misc', subcategories: [] }
];

// ===== 获取所有分类 =====
function getCategories() {
  const saved = localStorage.getItem('jianji_categories');
  if (saved) {
    try {
      return JSON.parse(saved);
    } catch (e) {}
  }
  return PRESET_CATEGORIES;
}

// ===== 保存分类 =====
function saveCategories(categories) {
  localStorage.setItem('jianji_categories', JSON.stringify(categories));
}

// ===== 获取收入分类 =====
function getIncomeCategories() {
  const saved = localStorage.getItem('jianji_income_categories');
  if (saved) {
    try {
      return JSON.parse(saved);
    } catch (e) {}
  }
  return PRESET_INCOME_CATEGORIES;
}

// ===== 保存收入分类 =====
function saveIncomeCategories(categories) {
  localStorage.setItem('jianji_income_categories', JSON.stringify(categories));
}

// ===== 获取所有交易记录 =====
function getTransactions() {
  const data = localStorage.getItem(STORAGE_KEY);
  if (!data) return [];
  try {
    return JSON.parse(data);
  } catch (e) {
    return [];
  }
}

// ===== 保存交易记录 =====
function saveTransactions(transactions) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(transactions));
}

// ===== 添加一条记录 =====
function addTransaction(record) {
  const transactions = getTransactions();
  const newRecord = {
    id: Date.now().toString(36) + Math.random().toString(36).slice(2, 6),
    amount: parseFloat(record.amount),
    category: record.category || '其他',
    subcategory: record.subcategory || '',
    note: record.note || '',
    date: record.date,
    time: record.time,
    type: record.type || 'manual',
    flow: record.flow === 'income' ? 'income' : 'expense',
    platform: record.platform || '',
    items: record.items || [],
    createdAt: Date.now()
  };
  transactions.unshift(newRecord);
  saveTransactions(transactions);
  return newRecord;
}

// ===== 删除一条记录 =====
function deleteTransaction(id) {
  let transactions = getTransactions();
  transactions = transactions.filter(t => t.id !== id);
  saveTransactions(transactions);
}

// ===== 更新一条记录（仅覆盖 patch 给出的字段，id/createdAt/type/platform/items 等原样保留） =====
function updateTransaction(id, patch) {
  const transactions = getTransactions();
  const idx = transactions.findIndex(t => t.id === id);
  if (idx === -1) return null;
  transactions[idx] = { ...transactions[idx], ...patch };
  saveTransactions(transactions);
  return transactions[idx];
}

// ===== 按日期范围查询 =====
function getTransactionsByDateRange(startDate, endDate) {
  const transactions = getTransactions();
  return transactions.filter(t => {
    return t.date >= startDate && t.date <= endDate;
  });
}

// ===== 按日期查询 =====
function getTransactionsByDate(date) {
  return getTransactionsByDateRange(date, date);
}

// ===== 按月查询 =====
function getTransactionsByMonth(year, month) {
  const start = `${year}-${String(month).padStart(2, '0')}-01`;
  const lastDay = new Date(year, month, 0).getDate();
  const end = `${year}-${String(month).padStart(2, '0')}-${String(lastDay).padStart(2, '0')}`;
  return getTransactionsByDateRange(start, end);
}

// ===== 按周查询 =====
function getTransactionsByWeek(dateStr) {
  const d = new Date(dateStr + 'T00:00:00');
  const day = d.getDay();
  const diff = d.getDate() - day + (day === 0 ? -6 : 1); // 周一为开始
  const monday = new Date(d.setDate(diff));
  const sunday = new Date(monday);
  sunday.setDate(monday.getDate() + 6);
  
  const start = formatDate(monday);
  const end = formatDate(sunday);
  return getTransactionsByDateRange(start, end);
}

// ===== 统计 =====
function calcStats(transactions) {
  const total = transactions.reduce((sum, t) => sum + t.amount, 0);
  const count = transactions.length;
  return { total, count };
}

// ===== 按分类统计 =====
function calcCategoryStats(transactions) {
  const map = {};
  transactions.forEach(t => {
    const cat = t.category || '其他';
    if (!map[cat]) map[cat] = 0;
    map[cat] += t.amount;
  });
  return map;
}

// ===== 工具函数 =====
function formatDate(d) {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

function getTodayStr() {
  return formatDate(new Date());
}

function getTimeStr() {
  const d = new Date();
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

function getMonthDays(year, month) {
  return new Date(year, month, 0).getDate();
}

function getFirstDayOfMonth(year, month) {
  return new Date(year, month - 1, 1).getDay();
}

// ===== API 设置 =====
function getApiSettings() {
  const saved = localStorage.getItem('jianji_api_settings_v2');
  if (saved) {
    try {
      return JSON.parse(saved);
    } catch (e) {}
  }
  return {
    url: 'https://open.bigmodel.cn/api/paas/v4/chat/completions',
    key: '51bcfc59b56847f683fd572744174923.O2o5N17YaaINNKf8',
    model: 'glm-4.6v'
  };
}

function saveApiSettingsToStorage(settings) {
  localStorage.setItem('jianji_api_settings_v2', JSON.stringify(settings));
}

function getOcrPrompt() {
  return localStorage.getItem('jianji_ocr_prompt_v2') || '';
}

function saveOcrPrompt(prompt) {
  localStorage.setItem('jianji_ocr_prompt_v2', prompt);
}

// ===== 导出数据 =====
async function exportData() {
  const data = {
    app: 'jianji',
    version: 2,
    exportTime: new Date().toISOString(),
    transactions: getTransactions(),
    categories: getCategories(),
    incomeCategories: getIncomeCategories()
  };
  const json = JSON.stringify(data, null, 2);
  const fileName = `简记数据_${getTodayStr()}.json`;

  // Capacitor 环境：走原生导出（写入系统下载目录），浏览器环境：blob 下载
  const p = window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.SharedImport;
  if (p && p.exportJson) {
    try {
      const r = await p.exportJson({ json, fileName });
      showToast(r && r.path ? `已导出到 ${r.path}` : '已导出');
    } catch (e) {
      showToast('导出失败：' + (e && e.message || '未知错误'));
    }
    return;
  }

  const blob = new Blob([json], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  URL.revokeObjectURL(url);
  showToast('已导出');
}

// ===== 清除所有交易数据 =====
function clearAllTransactions() {
  localStorage.removeItem(STORAGE_KEY);
}