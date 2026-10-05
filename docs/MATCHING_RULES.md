# 集合竞价撮合服务：规则、一致性语义与配置说明

本服务是一个可本地运行、可复现验证的证券集合竞价撮合服务，支持**开盘竞价（OPEN）**与
**收盘竞价（CLOSE）**两种场景。委托通过 HTTP 接口收集，在竞价阶段结束时按统一价格批量成交。

## 1. 基本概念

- **价位（tick）**：内部所有价格均为整数价位序号。价位序号 = `round_half_up(价格 / 最小变动价位)`，
  因此任何进入系统的委托价格都会先归一到合法价位上，撮合价格必然落在合法价位上。
- **限价委托**：`limitPrice` 非空且 `market=false`，按归一后的限价参与。
- **市价委托**：`market=true`，忽略价格字段，在任意候选价位上都可成交。
- **到达顺序（arrival sequence）**：委托在**当前竞价**收集阶段中首次进入订单簿的先后顺序。
  - 改单（replace）会把原委托标记为 `REPLACED`，新委托以改单命令到达时的顺序追加到队尾。
  - 撤单后重新挂单视为新的委托与新的到达顺序。
  - 选择「保留到下一场竞价」策略时，被保留委托在下一场中排在新委托之前（沿用其原始到达顺序）。

## 2. 撮合价格的确定

### 2.1 候选价格集合

候选价格为以下价位落在涨跌停区间 `[lowerLimit, upperLimit]` 内的去重集合：

1. 参考价对应的价位；
2. 所有在区间内的有效限价委托价位。

被涨跌停压制而归一到区间边界的委托，其原始价位不进入候选集合，归一后的边界价位参与撮合。

### 2.2 候选价位上的可成交量

在价位 `p`：

- 可成交买单量 `B(p)` = 所有「买限价 ≥ p」的买单与市价买单的未成交数量之和；
- 可成交卖单量 `S(p)` = 所有「卖限价 ≤ p」的卖单与市价卖单的未成交数量之和；
- 可成交量 `M(p) = min(B(p), S(p))`；
- 买方剩余 `RB(p) = B(p) - M(p)`，卖方剩余 `RS(p) = S(p) - M(p)`。

### 2.3 唯一价格的四级裁决（按顺序执行，逐级保留并列者）

1. **最大可成交量**：取 `M(p)` 最大的价位。若全部候选 `M(p)=0`，本场无成交价（结果标记 `NO_MATCH`）。
2. **最小剩余未成交委托量**：在并列价位中取 `RB(p) + RS(p)` 最小者。
3. **市场压力方向**：在仍然并列的价位上计算合计净压力
   `P = Σ RB(p) - Σ RS(p)`（只对当前仍并列的价位求和）：
   - `P > 0`（买方压力）：取其中**最高**价位；
   - `P < 0`（卖方压力）：取其中**最低**价位；
   - `P = 0`：进入下一轮。
4. **距参考价最近**：取 `|p - reference|` 最小的价位；若仍有高低两侧等距并列，取**较低**价位。

> 说明：第 3 级的直觉是「买方压力推高价格、卖方压力压低价格」。第 4 级保证规则在任何输入下都能
> 决出唯一价格。

## 3. 成交分配

确定唯一撮合价 `p*` 后，在该价位批量成交 `M(p*)` 数量：

- 可成交总量较小的一方为**主动方**，另一方为**被动方**（买卖量相等时以买方为主动方）；
- 主动方每笔委托按到达顺序取量，被动方按**到达顺序先后**分配（先到先得），直到成交总量分配完毕；
- 同一委托在本场最多产生一条成交记录，成交价统一为 `p*`；
- 成交记录序号 `sequence` 按委托到达顺序在全局连续递增，跨竞价也不回绕；
- 委托状态据此置为 `FILLED` / `PARTIALLY_FILLED`。

## 4. 阶段流转与阶段边界的确定性行为

阶段：`IDLE → COLLECTING → FINISHED`。

- `START`：开启一场新竞价。若上一场处于 `COLLECTING`，启动命令被拒绝（HTTP 409）。
  启动时携带本场全部参数（含最小变动价位、参考价、涨跌停区间、未成交策略、计划结束时刻），
  这些参数随命令一起持久化，因此恢复后行为与当时完全一致。
- 挂单 / 撤单 / 改单**只在 `COLLECTING` 阶段生效**；在其它阶段到达的这类命令被安全忽略，不改变任何状态。
  （HTTP 层会先返回 409；命令层保持幂等忽略以保证重放安全。）
- `FINISH`：结束当前竞价并执行撮合。重复的 `FINISH` 在命令层被忽略。
- 开盘竞价没有内建自动结束时刻；收盘竞价在指定窗口内随机产生结束时刻，也可通过
  `POST /api/auction/finish` 手动结束。

### 4.1 收盘随机结束时刻

- 窗口 = `[开始时刻 + close-window-start-millis-after-start, 开始时刻 + 上述偏移 + close-window-duration-millis]`；
- 结束时刻 = 窗口起点 + `floorMod(RNG.nextLong(), 窗口长度毫秒 + 1)`，因此均匀离散地落在闭区间内；
- 随机源为以 `call-auction.random-seed` 为种子的 `java.util.Random`。**相同种子、相同窗口与相同开始时刻
  ⇒ 结束时刻逐次一致**；
- 结束时刻在 `START` 命令中即被计算并写入日志。恢复时直接复用日志中的值，**不会重新抽取随机数**，
  从而保证崩溃恢复后收盘时刻与首次运行一致。

## 5. 涨跌停价格限制

- 允许区间以参考价为基础：`lower = ref × (1 - limit-change-percent)`，
  `upper = ref × (1 + limit-change-percent)`；
- 边界**向外**取整到合法价位：下限 `FLOOR`、上限 `CEILING`，保证理论边界完全被区间覆盖；
- 参考价来源：显式传入的外部参考价；若未传入，则使用上一场竞价的成交价（首场必须显式提供）；
- 委托限价超出区间时：将其参与撮合的限价**压制（clamp）到最近边界**，同时
  - 保留原始价位 `originalLimitTick`；
  - 置 `priceClamped=true`，可在委托查询中追溯；
- 撮合算法只在区间内选价，因此撮合价不会超出区间。`AuctionResult.priceAdjustedByLimit`
  保留给理论均衡价落在区间外而被调整的场景（当前候选集合已预先裁剪到区间内，正常情况下为 `false`）。

## 6. 未成交委托处理策略（可配置）

在 `FINISH` 对仍有未成交数量的委托按本场策略处理，策略随每场 `START` 持久化，切换时状态保持一致：

- `CARRY_OVER`：保留到下一场竞价，继续留在订单簿并在下一场排在新委托之前；
- `CANCEL`：自动撤销，状态置为 `EXPIRED`，从订单簿移除；
- `CONVERT_TO_LIMIT`：转为存量限价委托——市价残余按本场成交价（无成交时为参考价）获得限价，
  `market` 标记改为 `false`，状态置为 `CONVERTED` 并移出竞价订单簿；限价残余同样转为存量委托。

## 7. 买卖不平衡指示信息

通过 `GET /api/auction/imbalance` 在收集阶段发布，粒度由 `call-auction.imbalance.level` 配置：

- `NONE`：不披露任何信息（方向、数量、价格均为空 / 0）；
- `DIRECTION_ONLY`：只披露主导方向（`BUY` / `SELL` / 平衡时为 `null`），**不披露任何数量与价格**；
- `BUCKETED`：披露方向，以及向下取整到数量桶（`quantity-bucket-size`）的买/卖/指示性可成交/不平衡量，
  因此披露的永远是桶粒度的近似值而非精确数量；参考价仅在 `disclose-reference-price=true` 时披露。

系统不会在允许粒度之外泄露未成交委托的具体数量与具体价格。

## 8. 一致性与持久化语义

- **命令串行化**：所有改变状态的操作（挂单、撤单、改单、启动、结束、定时收盘）都在服务内部的
  单一锁上串行执行。并发的 HTTP 请求与定时收盘无论如何交错，结果都等同于命令按日志序号给出的
  **某个单一全序**串行执行。
- **先持久化后生效**：每条命令先分配单调递增序号、序列化为一行 JSON 追加写入命令日志
  （以 `DSYNC` 落盘），成功后才应用到内存状态。未成功落盘的命令不会生效。
- **崩溃恢复**：启动时顺序重放日志中的全部命令重建内存状态（当前阶段、订单簿、成交结果、序号、
  收盘结束时刻）。命令处理是确定性的，因此恢复结果与首次执行完全一致：
  - 不跳过任何已落盘命令；
  - 不重复应用命令（命令处理对重复 START/FINISH 与重复委托 ID 幂等，且恢复进程默认不再接收新命令）；
  - 不重新抽取随机收盘时刻（复用 START 中记录的时刻）。

## 9. HTTP 接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auction/start` | 开启竞价。请求体：`kind`、`referencePrice`(可选)、`unfilledPolicy`(可选)、`fixedEndMillis`(可选，收盘可指定固定结束时刻) |
| POST | `/api/auction/finish` | 手动结束当前竞价并撮合 |
| POST | `/api/auction/orders` | 挂单：`clientOrderId`、`symbol`、`side`、`limitPrice`、`market`、`quantity` |
| POST | `/api/auction/orders/cancel` | 撤单：`clientOrderId`（要撤销的原委托 ID） |
| POST | `/api/auction/orders/{origClientOrderId}/replace` | 改单：新的 `clientOrderId`、`limitPrice`/`market`、`quantity` |
| GET | `/api/auction/imbalance` | 当前不平衡指示 |
| GET | `/api/auction/orders` | 当前活跃委托（含 `priceClamped`、`originalLimitTick` 等可追溯字段） |
| GET | `/api/auction/results` | 历次竞价结果（含全部成交记录） |

非法参数返回 400，阶段错误（如收集期间重复 START、非收集期挂单）返回 409。

## 10. 配置项（前缀 `call-auction`）

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `symbol` | `SECURITY` | 默认证券代码（测试使用，挂单以请求中的 symbol 为准） |
| `tick-size` | `0.01` | 最小变动价位 |
| `limit-change-percent` | `0.10` | 涨跌停幅度（相对参考价） |
| `close-window-start-millis-after-start` | `0` | 收盘随机窗口起点相对开始时刻的偏移（毫秒） |
| `close-window-duration-millis` | `0` | 收盘随机窗口长度（毫秒），结束时刻在闭区间内均匀随机 |
| `random-seed` | `1` | 收盘结束时刻随机源种子，相同种子结果可复现 |
| `log-file` | `data/commands.log` | 命令日志（JSON Lines）路径 |
| `reset-on-startup` | `false` | 启动时是否清空命令日志（本地演示 / 测试用，生产恢复必须为 false） |
| `default-unfilled-policy` | `CARRY_OVER` | 未显式指定时的未成交处理策略 |
| `imbalance.level` | `DIRECTION_ONLY` | 不平衡披露粒度：`NONE`/`DIRECTION_ONLY`/`BUCKETED` |
| `imbalance.quantity-bucket-size` | `100` | `BUCKETED` 粒度下的数量桶大小 |
| `imbalance.disclose-reference-price` | `false` | 是否在不平衡信息中披露参考价 |

## 11. 本地复现

```bash
mvn spring-boot:run
# 开盘竞价
curl -X POST localhost:8080/api/auction/start \
  -H 'Content-Type: application/json' \
  -d '{"kind":"OPEN","referencePrice":"10.00"}'
# 挂单 / 撤单 / 改单 / 查看不平衡 / 结束撮合
curl -X POST localhost:8080/api/auction/finish
# 收盘竞价（结束时刻在配置窗口内按种子随机产生）
curl -X POST localhost:8080/api/auction/start \
  -H 'Content-Type: application/json' \
  -d '{"kind":"CLOSE","referencePrice":"10.00"}'
```

使用相同 `random-seed` 与相同输入重复运行，收盘结束时刻、撮合价格与成交分配逐次一致。
自动化测试覆盖价格平局消解、涨跌停边界、并发交错、随机收盘与崩溃恢复：

```bash
mvn test
```
