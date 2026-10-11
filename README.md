# 原版精修 · Fabric 移植版 (Vanilla Refit — Fabric Port)

在原版基础上做建筑扩展与细节打磨的 **Fabric** 模组：竖半砖、竖楼梯、混合半砖，以及一批更顺手的原版改动。
所有新增形态都由方块状态控制，默认关闭，中途加入模组不会让存档里已有的方块变形。

> 本仓库是 [btsdhz/vanilla-refit](https://github.com/btsdhz/vanilla-refit)（NeoForge 版）的 **Fabric 1.21.1 移植版**。
> 原模组由 **btsdhz** 开发，遵循 MIT 许可证。

| 项目 | 值 |
| --- | --- |
| 游戏版本 | Minecraft 1.21.1 |
| 加载器 | Fabric Loader 0.16+ / Fabric API |
| 模组 id | `btsdhz_original` |
| 运行环境 | 客户端与服务端均可 |
| 许可证 | MIT |

## 台阶与楼梯

- 台阶和楼梯都可以竖着放。按 `V`（可在按键设置里改）切换放置逻辑，台阶与楼梯各自记忆，并且会跟随存档保存：
  - **默认**：点方块面靠外区域竖放，点中央区域平放；手持时显示分割辅助线（台阶为对角线、楼梯为十字线，只在完整面上绘制）。
  - **混合**：面中央的正方形区域内平放，其余位置竖放，正方形比例可在配置里调整。
  - **紧贴**：按点击的那个面决定竖放朝向。
  - **原版**：保留原版放置逻辑（台阶的混合模式仍然可用）。
- **混合半砖**：同一格里叠放两种材质的半砖；潜行时只拆掉其中一半，不潜行则是原版式整体拆除。

## 火把、灯笼、栅栏与墙

- 火把、灵魂火把、红石火把、灯笼、灵魂灯笼可以放在下台阶上，模型、碰撞箱与粒子一起下移半格贴合台阶表面；灯笼也可以挂在上台阶下方。
- 下台阶上的栅栏与墙会整体下移半格，并与周围的栅栏/墙正常连接；墙在方块上方的满高判定也已适配。
- 判定基于原版方块与标签，因此其它模组按原版方式实现的红石火把、灯笼等也能沿用这套贴合。

## 玻璃板与铁栏杆

- 玻璃板与铁栏杆拆成 12 个部件（东西南北各分上下两半，加上四个角的水平面片），大面积铺开时中间的竖直面片会自动隐藏，拼出整块玻璃幕墙的效果。
- 连接规则沿袭原版，且与放置顺序无关，不会出现需要手动更新才正确的情况。

## 其它原版改动

- 玻璃、玻璃板等玻璃类方块可以用镐或斧加速挖掘。
- 拴绳可以在两个栅栏之间拉出一条微微下垂的悬链线；点击栅栏上的绳结即可解开并返还拴绳，拆除栅栏同样会解除连接。
- 铁砧：使用时不再逐渐损坏（只保留跌落受损）；用材料修复物品不再累积修复惩罚，修复比例由 25% 提高到 90%。
- 新增两个按键：爬行（默认 `Ctrl` 按住）与坐下（默认 `Z` 切换），都可在按键设置里更改。

## 配置

配置文件为 `config/btsdhz_original.json`，所有开关都带中文注释，默认开启：

- `allowTorchLanternOnSlab`：火把/灯笼在下台阶上的贴合。
- `allowLanternUnderTopSlab`：灯笼悬挂在上台阶下。
- `slabCenterSquareRatio`：台阶放置逻辑「混合」中央正方形的大小。

> 注：原 NeoForge 版使用 TOML 配置（`btsdhz_original-common.toml`），Fabric 版因加载器差异改为 JSON 格式。

## 构建

```bash
./gradlew build       # 打包，产物在 build/libs
./gradlew runClient   # 启动开发环境客户端
```

## 移植说明

本移植版与原 NeoForge 版的主要差异：

- **加载器 API**：`DeferredRegister` → Fabric `Registry.register`；NeoForge 事件总线 → Fabric API 事件；`ModConfigSpec` → 自实现 JSON 配置。
- **网络层**：NeoForge `RegisterPayloadHandlersEvent` → Fabric `PayloadTypeRegistry` + `ServerPlayNetworking`。
- **模型层**：原版依赖运行时烘焙包装（`ModelEvent.ModifyBakingResult`），Fabric 无等价事件，改为**静态预生成模型**方案（为全部原版台阶/楼梯生成竖形态 blockstate 与模型）。
- **Mixin**：31 个 Mixin 基本原样保留（注入目标均为原版类），仅移除 NeoForge 专有的 `remap = false` 并替换个别 NeoForge 钩子。

## 许可

MIT 许可证，详见 [LICENSE](LICENSE)。原模组版权归 **btsdhz** 所有。

---

## English

**Vanilla Refit (Fabric Port)** is a Fabric mod for Minecraft 1.21.1 that refines vanilla building and behaviour:
vertical slabs, vertical stairs and mixed slabs (with switchable placement modes), torches/lanterns/fences/walls
sitting flush on slabs, glass panes and iron bars rebuilt from 12 pieces with order-independent connections,
crawl/sit keybinds, a lead that can be strung between two fences, glass that can be mined faster with pickaxes or
axes, and anvil changes (no wear from use, no repair cost accumulation, 90% material repair).

This is a Fabric port of [btsdhz/vanilla-refit](https://github.com/btsdhz/vanilla-refit) (originally NeoForge),
by **btsdhz**. Licensed under the MIT License.
