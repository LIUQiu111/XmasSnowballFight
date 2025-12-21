# 🎄 XmasSnowballFight - 圣诞雪球大战插件
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Minecraft 1.8.8](https://img.shields.io/badge/Minecraft-1.8.8-brightgreen.svg)](https://www.minecraft.net/)
[![Spigot/Paper](https://img.shields.io/badge/Server-Spigot%2FPaper-blue.svg)](https://www.spigotmc.org/)

为圣诞节而生的Minecraft 1.8.8趣味雪球大战插件，集成游戏计时、击杀计分、权限管控、方块保护等核心功能，打造节日专属的多人对战玩法！

## 📋 插件功能
### 核心玩法
- 通过`/startxmas`指令一键启动雪球大战
- **双结束条件**：20分钟计时结束 或 玩家击杀数达到10人立即结束
- 全玩家永久饱食Buff，专注对战无需担心饥饿值
- 游戏结束后公示获胜者，并发送专属彩色标题奖励

### 计分板系统
- 侧边栏实时展示玩家击杀积分与血量信息
- 积分随击杀行为实时刷新，对战数据直观可见

### 权限与地图保护
- **OP专属权限**：仅OP可进行方块破坏/放置，非OP玩家全程禁止
- **动态提示**：结合游戏状态为非OP玩家显示差异化操作限制提示
- **爆炸防护**：集成`XmasExplosionProtect`类，防止游戏中爆炸破坏地图

### 广播与倒计时
- 游戏启动/结束全服广播，同步对战进度
- 10分钟、5分钟、1分钟倒计时提醒，增强游戏紧迫感

## 🔧 技术细节
- **适配版本**：Minecraft 1.8.8（Spigot/Paper服务端）
- **开发语言**：Java
- **核心依赖**：Bukkit/Spigot API（无第三方插件依赖）
- **事件监听**：实现方块操作、雪球伤害、玩家进出等核心事件处理

## 🚀 快速开始
### 安装步骤
1. 将编译后的`XmasSnowballFight.jar`放入服务器`plugins`目录
2. 重启服务器，插件自动加载（无额外配置文件）
3. 给管理员分配OP权限：`op [你的游戏ID]`

### 游戏指令
| 指令          | 权限要求 | 功能描述                     |
|---------------|----------|------------------------------|
| `/startxmas`  | OP权限   | 启动雪球大战游戏             |

### 权限说明
| 玩家类型 | 权限范围 |
|----------|----------|
| OP玩家   | 可使用启动指令、自由破坏/放置方块、编辑告示牌 |
| 非OP玩家 | 仅可参与雪球大战，无法进行任何方块操作 |

## 📁 核心类结构

org.xmas.java/├── XmasChristmasParty.java # 插件主类（初始化、指令注册、生命周期管理）├── XmasSnowballFight.java # 核心逻辑（计分板、雪球伤害、玩家 Buff）├── XmasBlockProtect.java # 方块保护（OP / 非 OP 权限管控）├── XmasExplosionProtect.java # 爆炸保护（地图防破坏）├── KillScoreManager.java # 积分管理（击杀数、游戏状态）
├── TitleUtil.java # 标题工具（获胜者奖励标题）
plaintext


## 💻 开发与贡献
本项目由**初开**个人结合AI辅助开发，欢迎各位玩家/开发者参与优化：
1. 克隆仓库：`git clone https://github.com/LIUQiu111/XmasSnowballFight.git`
2. 导入至IntelliJ IDEA/Eclipse（需配置**Spigot 1.8.8**开发依赖）
3. 修改代码后编译为Jar包，直接部署到服务器测试
4. 提交PR前请确保核心功能（方块保护、权限管控）无Bug

## 📜 许可证
本项目采用 **MIT许可证** 开源，你可自由修改、分发与商用，需保留原作者声明。

## ⚠️ 注意事项
1. 仅支持Minecraft 1.8.8版本，高版本服务端存在兼容性问题
2. 需在Spigot/Paper服务端运行，纯Vanilla（原版）服务端不支持
3. 调整游戏规则可修改以下参数：
   - 游戏时长：修改`XmasChristmasParty.java`中`startGameTimer()`的`timeLeft`变量
   - 击杀获胜条件：修改`KillScoreManager.java`中的击杀数阈值

## 📞 反馈与交流
若遇到Bug或有功能建议，可通过GitHub Issues提交反馈，我会尽快处理！
