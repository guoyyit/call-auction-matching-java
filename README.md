# 本地证券集合竞价撮合服务

基于 Spring Boot 3 / Java 17 的本地集合竞价(call auction)撮合服务。通过 HTTP 接口
接收委托，在开盘竞价与收盘竞价阶段结束时按**统一价格**批量成交；收盘竞价的结束
时刻在配置窗口内由**可注入种子的随机源**产生。全部行为可在本地复现与验证。

## 1. 快速开始

```bash
mvn spring-boot:run
# 服务默认监听 http://localhost:8080
```

端到端复现脚本见 [`scripts/demo.sh`](scripts/demo.sh)：

```bash
./scripts/demo.sh
```

运行全部自动化测试：

```bash
mvn clean test
```

## 2. 阶段模型

阶段严格单向流转：

```
OPEN_AUCTION  ──撮合/手动推进──▶  INTRADAY  ──开始收盘──▶  CLOSE_AUCTION  ──随机时刻到期撮合──▶  CLOSED
```

- `OPEN_AUCTION`：开盘集合竞价，接受挂单/撤单/改单与不平衡查询。
- `INTRADAY`：盘中占位阶段，**不接受委托**。
- `CLOSE_AUCTION`：收盘集合竞价。进入时立即采样一个结束时刻偏移量
  `closeEndOffset ∈ [closeWindowStartMillis, closeWindowStartMillis + closeWindowLengthMillis)`，
  使用 `new Random(randomSeed)` 均匀取模生成；相同种子与相同调用序列下逐次一致。
  后台轮询线程(`CloseAuctionMonitor`)在到达该时刻时自动撮合，也可调用
  `/api/auction/phase/close-poll` 手动触发检查。
- `CLOSED`：全天结束。

**阶段边界的确定性**：委托只在 `OPEN_AUCTION` / `CLOSE_AUCTION` 接受；阶段切换、
撮合、未成交处理在同一把服务锁内一次性完成并写日志，因此“切换前的委托一定参与
本场竞价，切换后的委托一定被拒绝或进入下一场”，不存在跨边界的歧义。

## 3. 委托模型与价格归一化

- 委托类型：`LIMIT`(限价) / `MARKET`(市价)；方向：`BUY` / `SELL`。
- 每笔委托记录**到达顺序号 `arrivalSeq`**(全局单调递增)与到达时刻。
- 撤单：仅竞价阶段可对仍有余量的有效委托撤单。
- 改单：语义为“旧单置为 `REPLACED` + 挂入一笔新单”，通过
  `replaces` / `replacedBy` 字段保持双向可追溯；新单获得新的到达序号。
- **价格归一化**：内部全部价格以最小变动价位 `tick-size` 的整数倍(long tick)存储，
  杜绝浮点误差。委托货币价按四舍五入(`HALF_UP`)归一到最近合法价位；
  撮合价必然落在合法价位上。

## 4. 撮合价格规则(完整文档)

在候选价集合(全部有效限价委托价格；仅有市价单时取参考价)上逐价评估。
某候选价 `p` 处：

- 有效买量 = 全部市价买量 + 限价 ≥ `p` 的买量；
- 有效卖量 = 全部市价卖量 + 限价 ≤ `p` 的卖量；
- 可成交量 `matched = min(有效买量, 有效卖量)`；
- 剩余买量/卖量为成交后两侧各自余量，残差 `residual = |有效买量 - 有效卖量|`。

撮合价按以下优先级**逐档消解，直到唯一**：

1. **最大可成交量**：取 `matched` 最大的候选价。
2. **最小剩余未匹配量**：平局时取 `residual` 最小者。
3. **市场压力方向 + 参考价距离**：仍平局时：
   - 买方剩余多于卖方(上行压力)：取离参考价更近者；距离再相等取较高价；
   - 卖方剩余多于买方(下行压力)：取离参考价更近者；距离再相等取较低价；
   - 完全平衡：取不高于参考价的最高价；参考价缺失时取较低价兜底。

`call-auction.tie-break-by-pressure` 可改变第三档的方向性偏好
(`STANDARD` / `BUY_PRESSURE_HIGH` / `SELL_PRESSURE_LOW`)。

成交分配：在最终撮合价上，买卖双方各按 `arrivalSeq` 顺序配对，生成逐笔 `Trade`，
全部成交共享同一统一价格。

## 5. 涨跌停限制

- 参考价优先使用外部配置 `reference-price`；未配置时取**上一场竞价成交价**；
  再无则本场不设限。
- 涨停价 = `floor(ref × (1 + limit-up-ratio) / tick) × tick`(向下归位，区间不放宽)；
  跌停价 = `ceil(ref × (1 - limit-down-ratio) / tick) × tick`(向上归位)。
- 委托入场即做边界夹持：买价高于涨停按涨停价、卖价低于跌停按跌停价参与，
  委托上 `priceLimited=true` 标记被压制，**保持可追溯**。
- 候选价与最终撮合价夹持到区间内；若理论撮合价越界，结果中
  `priceAdjustedByLimit=true` 记录被调整。

## 6. 买卖不平衡信息

`GET /api/auction/imbalance` 发布指示性信息，披露粒度严格可配置：

- `publish-enabled=false` 或阶段开关关闭时返回 `disclosed=false`，
  **不泄露任何数量与价格**。
- 数量按 `quantity-bucket-size` **向下取整**后披露，无法反推精确簿内数量；
  `bucket=0` 时只披露方向(`BUY`/`SELL`/`BALANCED`)。
- 是否披露参考价由 `disclose-reference-price` 控制。

## 7. 未成交委托策略

`call-auction.unmatched-policy`：

- `CARRY`：保留到下一场竞价，维持可成交状态与原到达顺序。
- `CANCEL`：本场结束后全部置为 `CANCELLED`。
- `CONVERT_LIMIT`：市价未成交单按参考价(无参考价取 0)转为限价单并标记 `CONVERTED`；
  限价未成交单直接标记 `CONVERTED`。

策略动作同样写入 WAL，切换策略后重放结果与在线状态保持一致。

## 8. 一致性与崩溃恢复语义

- **可串行化**：挂单、撤单、改单、阶段切换全部在 `AuctionService` 的同一把锁内
  执行，并发交错的结果等同于委托按某个单一全局顺序串行执行的结果。
  订单 ID 与 `arrivalSeq` 全局唯一(有并发测试覆盖)。
- **写前日志(WAL)**：每次状态变更先追加一行 JSON 到
  `journal-dir/events.log` 并 flush，再修改内存态。
- **恢复**：启动时按事件 `seq` 顺序完整重放，重建委托簿、成交记录、撮合结果、
  当前阶段与收盘结束时刻偏移；**不重放也不跳过任何一条已确认事件**。
  ID 序列推进到日志最大值之后，恢复后新分配 ID 不会与历史冲突。

## 9. HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auction/orders` | 挂单 |
| POST | `/api/auction/orders/replace` | 改单(旧单撤销+新单) |
| POST | `/api/auction/orders/{id}/cancel` | 撤单 |
| GET  | `/api/auction/orders` | 全部委托 |
| GET  | `/api/auction/trades` | 逐笔成交 |
| GET  | `/api/auction/results` | 各场竞价结果 |
| GET  | `/api/auction/phase` | 当前阶段 |
| GET  | `/api/auction/imbalance` | 不平衡指示 |
| POST | `/api/auction/phase/open-match` | 开盘竞价撮合并转盘中 |
| POST | `/api/auction/phase/start-close` | 进入收盘竞价(采样随机结束时刻) |
| POST | `/api/auction/phase/close-match` | 立即撮合收盘竞价 |
| POST | `/api/auction/phase/close-poll` | 检查随机结束时刻，到期则撮合 |

挂单请求示例：

```json
{ "side": "BUY", "type": "LIMIT", "price": 10.05, "quantity": 200 }
```

## 10. 全部配置项

| 配置键 | 默认值 | 说明 |
| --- | --- | --- |
| `call-auction.symbol` | `600000` | 证券代码 |
| `call-auction.tick-size` | `0.01` | 最小变动价位 |
| `call-auction.limit-up-ratio` | `0.10` | 涨停幅度 |
| `call-auction.limit-down-ratio` | `0.10` | 跌停幅度 |
| `call-auction.reference-price` | 空 | 外部参考价；空时取上场成交价 |
| `call-auction.close-window-start-millis` | `60000` | 收盘结束窗口起点 |
| `call-auction.close-window-length-millis` | `10000` | 收盘结束窗口长度 |
| `call-auction.random-seed` | `42` | 收盘时刻随机种子 |
| `call-auction.tie-break-by-pressure` | `STANDARD` | 第三档平局方向规则 |
| `call-auction.unmatched-policy` | `CARRY` | 未成交策略 |
| `call-auction.imbalance.publish-enabled` | `true` | 是否发布不平衡信息 |
| `call-auction.imbalance.quantity-bucket-size` | `100` | 数量披露取整步长 |
| `call-auction.imbalance.disclose-reference-price` | `true` | 是否披露参考价 |
| `call-auction.imbalance.publish-in-open-auction` | `true` | 开盘期是否发布 |
| `call-auction.imbalance.publish-in-close-auction` | `true` | 收盘期是否发布 |
| `call-auction.journal-dir` | `data/journal` | WAL 目录 |
| `call-auction.recovery-enabled` | `true` | 启动时是否从 WAL 恢复 |
| `call-auction.close-poll-millis` | `100` | 收盘到期后台轮询间隔 |

## 11. 测试覆盖

- `MatchingEngineTest`：最大成交量、残差平局、市场压力+参考价距离平局消解、
  市价单、无成交、涨跌停边界夹持、按到达顺序成交。
- `AuctionServiceRulesTest`：涨跌停压制重定价与可追溯、撤改单链、
  三种未成交策略、阶段边界拒绝委托、不平衡粒度/禁用不泄露。
- `ConcurrencyTest`：8 线程并发挂单+撤单交错，ID/到达序号全局唯一。
- `RandomCloseAndRecoveryTest`：相同种子产生相同收盘结束时刻、到期前不撮合/
  到期后撮合、崩溃后恢复委托/成交/阶段、恢复后 ID 与序号不冲突。
