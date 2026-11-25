# API 与数据契约 v1

基础地址 `http://127.0.0.1:18765`。请求携带 `Authorization: Bearer <token>`。仅 GET。

## GET /v1/summary?month=YYYY-MM

```json
{
  "month": "2026-11",
  "expense_fen": 2500,
  "income_fen": 1000000,
  "balance_fen": 997500,
  "invest_fen": 200000,
  "redeem_fen": 50000,
  "net_invested_fen": 150000,
  "expense_categories": {"餐饮": 2500},
  "count": 4,
  "budget": {
    "configured": true,
    "limit_fen": 300000,
    "remaining_fen": 297500,
    "days_left": 7,
    "daily_allowance_fen": 42500
  }
}
```

API 中 net_invested_fen 为所选月净投入，App 统计页的累计净投入则遍历全部月份。count 包含四种流水类型。

budget 为所选月的生活消费预算。remaining_fen = limit_fen − expense_fen，超支时可以为负数。未设置预算时 configured 为 false、limit_fen 为 0、remaining_fen 和 daily_allowance_fen 为 null。days_left 根据手机本地日期计算：当月包含今天，未来月份为整月天数，过去月份为 0。daily_allowance_fen 为非负剩余额度除以剩余天数后向下取整，已结束月份为 0。投资不消耗生活预算。

## GET /v1/entries?month=YYYY-MM

```json
{
  "month": "2026-11",
  "entries": [
    {
      "id": "3315be2e-72e2-4f02-a293-dd3f6e2f2321",
      "type": "expense",
      "amount_fen": 2500,
      "date": "2026-11-24",
      "category": "餐饮",
      "note": "午餐"
    }
  ]
}
```

type 为 expense / income / invest / redeem。amount_fen 始终为正整数，类型决定资金方向；1 元 = 100 分。id 为 UUID，date 为手机本地日历日期，不做 UTC 转换。

## JSON 备份

```json
{"schema_version":3,"currency":"CNY","entries":[],"budgets":[{"month":"2026-11","amount_fen":300000}],"categories":[{"type":"expense","name":"餐饮","enabled":true}]}
```

entries 为上述相同结构。备份包含全部月份。通过系统文件选择器读写，无需存储权限。导入文件上限 4 MiB，按 id 合并；同 id 覆盖，未出现的现存账目保留。任一记录校验失败时整体回滚。导出的文件大小若超过导入限制，需拆为若干保留相同顶层结构的文件。

0.2.0 起导出 schema_version 2，包含月预算。预算按 month 合并，同月份使用备份金额覆盖，未出现的月份保留。仍接受 schema_version 1 的旧账单备份，导入旧格式时保留手机上的现有预算。分类排序由已保存账目实时计算，因此无需独立备份排序计数。新格式不能导入旧版 0.1.0 应用。

字段限制：金额 1–999999999999 分；分类 1–40 字符；备注最多 500 字符；日期必须真实有效。

0.3.2 起导出 schema_version 3，增加独立分类目录 categories（type、name、enabled）。分类按类型与名称合并，enabled=false 记录已删除分类，恢复备份时不会重新出现。未被备份覆盖的本地分类保留；仍被合并后账目使用的分类保持可用。仍支持导入版本 1、2；旧版应用无法导入版本 3。改名同步该类型的历史账目，删除分类时将历史账目移入用户选定的同类型分类，不删除账单。
