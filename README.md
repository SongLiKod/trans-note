# 途记交通备忘录 TransNote

纯本地离线安卓工具 APP：公交 / 地铁线路个人记录、站点与地铁出入口精细化备注、离线双向检索、在线周边站点查询、本地 ZIP 备份。
**无后端服务、无账号登录、不上传任何用户数据。**

- 包名：`com.transnote.traffic`
- 最低兼容：Android 10（API 29）；目标 SDK：Android 14（API 34）
- 语言：100% Kotlin 业务代码 + Jetpack Compose（XML 仅资源）

---

## 1. 功能概览

- **线路录入**：手动录入或拍照 OCR（离线中文识别）自动填充；公交支持上行 / 下行独立站点、拖拽排序、批量添加。
- **公交线路与站点**：上行 / 下行切换、站点轨迹地图、站点备注 / 实景图片 / 经纬度、绑定地铁出入口并显示换乘线路 · 站点 · 距离。
- **地铁线路与出入口**：一站多出入口；出入口记录出口名、道路、地标、通行备注、图片与地图定位；可从高德批量获取 / 刷新出入口信息（真实街道、行政区、周边道路）。
- **检索**：默认**离线**检索（正向按线路、反向按地点）；可选**在线**检索，输入某地点即可查看其周边公交站 / 地铁站。
- **底部轨迹地图**：可拖拽折叠（隐藏 / 迷你 / 半屏 / 展开）；未选中站点时不显示，避免打扰。
- **备份**：一键导出 / 导入 ZIP（数据库 + 图片），完全本地。
- **主题**：跟随系统 / 浅色 / 深色，实时切换。

---

## 2. 技术栈

| 类别 | 组件 |
| ---- | ---- |
| UI | Jetpack Compose + Material3（深浅色 / 跟随系统，实时切换） |
| 架构 | MVVM：UI → ViewModel（StateFlow）→ Repository → Room |
| 数据库 | Room（SQLite），多表 + 关系查询（含版本迁移） |
| 异步 | Kotlin 协程 + Flow |
| 图片 | Coil-Compose（本地私有目录图片） |
| 相机 / OCR | CameraX + ML Kit 离线中文文字识别 |
| 地图 | osmdroid（OSM 瓦片，站点打点 + 轨迹绘制；瓦片需网络，业务全离线） |
| 在线能力（可选） | 高德 Web 服务（线路 / 出入口 / 周边站点；需自备 Key，仅联网时使用） |
| 配置存储 | DataStore Preferences |
| 文件处理 | Kotlin IO + ZIP（本地备份导入导出） |

未引入：Retrofit、推送 SDK、埋点、广告 SDK。

---

## 3. 目录结构

```
com.transnote.traffic
├── MainActivity.kt              // APP 入口，挂载 Compose + 全局主题
├── TransNoteApp.kt              // Application + 轻量依赖容器 AppContainer
├── ui
│   ├── theme                    // ColorScheme / Typography / Shapes
│   ├── navigation               // 路由定义
│   ├── screens                  // 首页、线路编辑、线路详情、站点详情、搜索、设置、OCR
│   └── components               // 卡片、标签、图片网格、拖拽列表、地图、相机等
├── viewmodel                    // 全部 ViewModel（StateFlow）
├── repository                   // 本地仓库：Room + 文件 IO
├── data
│   ├── database                 // Room Entity / DAO / Database / Relations
│   └── file                     // ImageStorage（图片读写）、BackupManager（ZIP 备份）
└── utils                        // OCR、搜索、坐标、拖拽、定位、主题、时间/距离格式化
```

---

## 4. 版本号（单一来源）

版本号**只在** `gradle.properties` 中维护一处：

```properties
APP_VERSION_NAME=1.1.0
```

- `versionName` 直接使用该值；`versionCode` 由版本名自动计算生成（`major*10000 + minor*100 + patch`，如 `1.1.0 → 10100`）。
- 升级版本时**只需修改这一行**，无需改动其它任何文件。
- 发布工作流在未显式指定版本时，也从该处读取版本用于产物命名。

---

## 5. 构建与运行

```bash
# Debug APK
./gradlew :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk

# Release APK（未配置签名时输出未签名包）
./gradlew :app:assembleRelease
```

- 使用 JDK 17、Android SDK 34（AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.21）。
- 仓库根目录自带 Gradle Wrapper；也可用本地 Gradle 执行 `gradle :app:assembleDebug`。

---

## 6. 持续集成与发布（GitHub Actions）

工作流：`.github/workflows/build.yml`

- 触发：推送到 `master`、`v*` 标签、PR 到 `master`，以及手动运行（可填 `version` / `prerelease`）。
- 作业：
  - `test`：运行单元测试。
  - `android`：构建 Debug / Release APK 并上传为 artifact。
  - `release`：打 `v*` 标签或手动运行时创建 GitHub Release（产物命名 `TransNote-<版本>.apk` 等）。
- 可选发布签名 secrets（不配置则输出未签名包）：`ANDROID_KEYSTORE_BASE64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`。

---

## 7. 权限说明

| 权限 | 用途 |
| ---- | ---- |
| CAMERA | 拍照录入站牌 / 站点实景图片 |
| READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE(≤32) | 相册选图 |
| ACCESS_FINE/COARSE_LOCATION | 地图打点定位 |
| INTERNET / ACCESS_NETWORK_STATE | 仅地图瓦片与可选的在线高德查询；业务逻辑默认全部离线 |

---

## 8. 数据与隐私

所有数据（Room 数据库、图片文件）保存在 App 私有目录，卸载即清除；不上传服务器、无云同步、无广告、无推送、无埋点。请定期使用「设置 → 本地数据备份」导出 ZIP。

---

## 9. 使用声明

应用内「关于」页的使用声明为作者原文，本 README 不复制其法律文本，以应用内 `AboutContent.DISCLAIMER` 为准（由单测逐字锁定）。要点：

- 本软件为个人兴趣开发产物，仅供个人学习、娱乐、非商业性质免费使用；
- 作者保留全部知识产权，未经书面许可不得二次开发、修改、复刻、衍生创作或用于任何商业营利用途；
- 使用者需遵守当地法律法规与网络使用规范，违规使用的后果自行承担。

> 完整、具有约束力的声明文本以应用内「关于」页展示为准，本 README 仅为说明，不构成额外授权或承诺。
