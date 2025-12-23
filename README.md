🎄 一款适配 Minecraft 1.8.8 版本的圣诞主题互动插件，让玩家在游戏中体验雪球对战的乐趣，增添节日氛围！

![GitHub stars](https://img.shields.io/github/stars/LIUQiu111/XmasSnowballFight?style=flat-square) 
    ![GitHub license](https://img.shields.io/github/license/LIUQiu111/XmasSnowballFight?style=flat-square) 
    ![适配版本](https://img.shields.io/badge/Minecraft-1.8.8-brightgreen?style=flat-square)

📖 插件介绍

「圣诞雪球大战」是一款基于 Spigot/Paper 1.8.8 核心开发的互动插件，专为圣诞节日打造。插件支持雪球投掷伤害判定、圣诞特效展示、投掷冷却机制等核心功能，适配个人服务器与小型社群使用，无需复杂配置即可快速部署。

当前版本：v2.0（在 v1.0 基础上优化特效、修复BUG，新增可配置化伤害与冷却机制）

✨ 核心功能

- ❄️ 雪球对战伤害：雪球击中玩家可造成自定义伤害（默认2.0点），附带节日提示信息

- 🎆 圣诞特效：投掷雪球时触发红白色烟花特效，增强节日氛围，自动清理避免卡顿

- ⏱️ 投掷冷却：1秒冷却机制，防止高频投掷导致服务器压力过大

- 🐛 BUG修复：解决v1.0版本中伤害判定异常、非玩家实体触发特效等问题

- ⚙️ 可配置化：支持通过配置文件自定义雪球伤害，无需修改代码即可适配需求

📋 适配环境

环境要求

具体信息

Minecraft 版本

1.8.8（Spigot/Paper 核心）

前置插件

无（独立运行，无需额外依赖）

开发环境

JDK 8+、Maven 3.6+

服务器内存

最低 1G（推荐 2G 及以上，确保特效流畅）

🚀 安装部署

1. 下载插件

从本仓库 release 目录下载最新版本插件（XmasSnowballFight-2.0.jar），或通过源码编译生成。

2. 部署步骤

1. 将插件 jar 文件放入服务器 plugins 目录

2. 重启 Minecraft 服务器，插件将自动加载

3. 查看服务器控制台，若输出 圣诞雪球大战插件 v2.0 加载成功！适配1.8.8版本 即为部署完成

3. 配置修改（可选）

插件加载后，会在 plugins/XmasSnowballFight 目录生成配置文件 config.yml，可修改以下参数：

# 雪球基础伤害（默认2.0）
snowball-damage: 2.0

# 投掷冷却时间（单位：秒，默认1）
throw-cooldown: 1

# 是否开启圣诞特效（默认true）
christmas-effect: true

修改后保存文件，执行指令 /snowball reload 即可生效（需管理员权限）。

🎮 指令与权限

1. 核心指令

指令

功能说明

使用权限

/snowball reload

重新加载插件配置

xmas.snowball.use（管理员权限）

/snowball help

查看插件指令帮助

所有玩家

2. 权限说明

默认情况下，普通玩家拥有雪球投掷与对战权限，管理员需手动配置 xmas.snowball.use 权限以使用重载指令。

💻 源码编译

若需自定义开发，可通过以下步骤编译源码：

# 克隆仓库
git clone https://github.com/LIUQiu111/XmasSnowballFight.git

# 进入项目目录
cd XmasSnowballFight

# 执行Maven编译
mvn clean package

编译完成后，插件 jar 文件将生成在 target 目录下。

🐞 常见问题

1. 插件加载失败？

检查服务器核心是否为 1.8.8 版本，JDK 版本是否为 8+，确保插件 jar 文件未损坏。

2. 雪球投掷无特效？

确认配置文件中 christmas-effect 为 true，且服务器内存充足（特效需少量内存支持）。

3. 伤害数值不生效？

修改配置后需执行 /snowball reload 重载配置，或重启服务器。

📌 后续扩展计划

- 新增对战房间模式，支持玩家组队对战与击杀统计

- 添加圣诞礼盒奖励，击中玩家可掉落节日道具

- 适配 1.12.2、1.16.5 等主流 Minecraft 版本

- 新增管理员面板，支持可视化配置与玩家管理

📜 许可证

本项目采用 MIT 许可证开源，详见LICENSE 文件。

🎄 致谢

感谢 Spigot 社区提供的 API 支持，以及所有测试与反馈的玩家！祝大家圣诞快乐，游戏愉快！

最后更新时间：2025年12月23日
