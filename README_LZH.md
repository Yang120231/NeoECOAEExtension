# Neo ECO AE Extension · Minecraft 1.20.1 GTO 專版

[English](README.md) | [简体中文](README_ZH_CN.md) | [繁體中文](README_ZH_HK.md) | 文言文

Neo ECO AE Extension，Applied Energistics 2 之擴充模組也。為 ME 網絡設合成、儲存、運算三系統，皆可擴其構；又置整合工坊，以製進階組件。

此 `1.20.1-GTO` 分支承 `1.20.1` 而來，專適 **GregTech Odyssey（GTO）** 之 AE2 與 GregTech 介面。今版 **`20.5.4-gto.1`**，所適者 **GTO `0.6.0-dev25`（Dev25）**，以預發佈版供之。凡用一般 AE2 整合包者，當取本倉庫 `1.20.1` 分支及其相應版本。

## 諸般功用

| 系統 | 所司 |
| --- | --- |
| ECO 合成系統 | 以樣板匯流排、工作核心及並行核心行自動合成，兼備批次、超頻與主動冷卻。 |
| ECO 儲存系統 | 以儲存主機、驅動器及儲存矩陣擴 ME 網絡之容量，可納物品、流體，亦可兼納附屬資源。 |
| ECO 運算系統 | 為自動合成供 CPU、任務執行緒、協同處理器及任務儲存之所，其能隨結構而增。 |
| 整合工坊 | 製盈能材料、處理器、儲存組件，及三系統所需之進階部件。 |
| 大型整合工坊 | 行批次加工、樣板自動化、超頻與冷卻，並納所支援附屬之配方。 |

合成、儲存、運算三主機，皆有自動搭建子窗，可定長度與鏡像。結構終端之物仍存，啟之則示意：請用相應主機 UI 內之搭建按鈕。

大型整合工坊凡四類：**常規、AdvancedAE、ExtendedAE、AE2LT**。AE2LT 之過載加工、閃電組裝、閃電模擬，共用其一類。所需升級模組，惟相應附屬 Mod 已載入，方予註冊。ExtendedAE Plus 其餘聯動仍存，惟不另立 Plus 大型工坊之類。

## 所適之境

下表乃 `20.5.2-gto.1` 發佈記所載目標實例之組合，非謂一般 AE2 皆可通用也：

| 項目 | 目標版本 |
| --- | --- |
| Minecraft | `1.20.1` |
| GregTech Odyssey | `0.6.0-dev25`（Dev25） |
| Forge | 實例用 `47.4.20`；原始碼建置用 `47.4.28` |
| Java | 遊戲須用 **Java 21** |
| GTOCore | `26.10.18` |
| GTO GTM | `26.10.80` |
| GTO AE2 | `15.2610.40` |

GuideME、LDLib 皆必備，詳見 [Mod 中繼資料](src/main/resources/META-INF/mods.toml)。安裝宜循目標 GTO 整合包之依賴組合。AdvancedAE、ExtendedAE、ExtendedAE Plus、AE2LT 皆可選；欲用其功，亦須備其各自前置。

此專版已適 GTO 原生合成 CPU、樣板派發、合成確認、精確數量同步及巢狀網絡儲存諸介面；亦可用 GTO 樣板修改器 Pro，改大型整合工坊通訊介面內之樣板。工坊配方可重載，並列 EMI 分類。

## 安裝與用法

1. 於本倉庫 GitHub Releases 取 **`20.5.4-gto.1`**，下載 `neoecoae-20.5.4-gto.1.jar`。
2. 置此 JAR 於目標 GTO 實例之 `mods` 目錄，並去舊版 NeoECOAE JAR。`-sources.jar` 乃原始碼之包，不可作模組安裝。
3. 聯機之時，用戶端與伺服器須裝同版；ECO 網絡握手，必兩端版本全同乃可。
4. 以 Java 21 啟目標實例，於配方檢視器查材料與機器諸方。

初學可先得盈能水晶、製整合工坊，繼按所需立儲存、運算或合成主機。多方塊之搭建、冷卻、升級，詳遊戲內 GuideME，亦可閱倉庫之簡體中文指南：

- [入門與系統概覽](guidebook/_zh_cn/neoecoae_intro/index.md)
- [整合工坊](guidebook/_zh_cn/neoecoae_intro/integrated_working_station.md)與[大型整合工坊](guidebook/_zh_cn/neoecoae_intro/large_integrated_working_station.md)
- [合成系統](guidebook/_zh_cn/neoecoae_intro/crafting_system.md)與[合成控制器圖說](guidebook/_zh_cn/neoecoae_intro/crafting_controller.md)
- [儲存系統](guidebook/_zh_cn/neoecoae_intro/storage_system.md)與[相容儲存矩陣](guidebook/_zh_cn/neoecoae_intro/compat_storage_matrices.md)
- [運算系統](guidebook/_zh_cn/neoecoae_intro/computation_system.md)

指南之互動結構場景、物品及配方組件，須於遊戲內觀之。

## 由原始碼建置

須備 **JDK 17、JDK 21、Python 3**。`build-local.ps1` 以 Java 17 啟 Gradle；GTO AE2 依賴用 Java 21 位元組碼，故編譯工具鏈用 Java 21，而 ECO 自身仍以 `--release 17` 輸出。至於遊戲，仍須依上表用 Java 21。

於專案根目錄，以 PowerShell 行下列諸令：

```powershell
# 若未尋得 Java 17，則設為本機 JDK 17 所在目錄
$env:NEOECOAE_JAVA17_HOME = 'C:\path\to\jdk-17'

# 初次取碼，先下載驗明固定依賴，並生本機依賴鎖
python tools/compat/bootstrap.py --gto

# 製安裝包、原始碼包，且行 GTO 專題迴歸檢驗
.\build-local.ps1 jar sourcesJar gtoCompatibilityTest --console=plain

# 驗發佈包之內容
python tools/compat/check_artifacts.py --release-only
```

Gradle 工具鏈須能尋得 JDK 21；若未自識，可設 `org.gradle.java.installations.paths` 以指其安裝目錄。Gradle 諸項驗證宜依次而行，免共用輸出相衝。依賴快取既備，建置令可加 `--offline`。

建置所得，皆在 `build/libs/`：

- `neoecoae-20.5.4-gto.1.jar`：安裝之包。
- `neoecoae-20.5.4-gto.1-sources.jar`：原始碼之包。

編譯依賴今鎖於 GTO AE2 **`15.2610.31`**，見 [GTO 依賴鎖](porting/gto-dependencies.json)；Dev25 實例所用 **`15.2610.40`**，乃執行時適配之目標，二者分記。`bootstrap.py --gto` 惟還原建置所需依賴，不代裝完整 GTO 整合包。

## 目錄之制

```text
src/main/java/cn/dancingsnow/neoecoae/
  api/                      ECO 業務與擴充契約
  blocks/                   機器、介面與方塊實體
  crafting/                 合成規劃與執行
  impl/storage/             儲存實作與精確數量處理
  compat/                   AE2、GTO 與可選附屬相容
  gui/                      主機頁面與搭建子窗
  platform/                 Forge 1.20.1 平台適配
src/main/resources/         Mod 中繼資料、資源與配方
src/test/java/              業務與平台檢驗
src/gtoTest/java/           GTO 專題迴歸檢驗
guidebook/                  GuideME 指南與結構場景
docs/compat/                GTO 適配記與依賴鎖
docs/releases/              中英雙語發佈記
tools/compat/               固定依賴還原與產物檢驗工具
```

`libs/`、`build/`、`porting/` 皆本機生成或工作之所；惟固定依賴鎖 `porting/gto-dependencies.json` 入 Git。乾淨取碼後，建置所需依賴資訊，由 `tools/compat/build-dependencies.json` 與此鎖供之。

## 驗證與報錯

今次本機 `jar sourcesJar gtoCompatibilityTest` 與 JAR 之檢皆過：**專題測試八十六，通過八十三，略三，無敗**；另有**循環規劃專項三十四，皆過**。所略者須實裝 AE2/GTOCore JAR；今次未重行完整客戶端與獨立專服之驗。詳見 [20.5.4-gto.1 發佈記](docs/releases/20.5.4-gto.1.md)。

然完整舊測試套件、全部附屬組合、獨立專服，尚未盡驗。舊測試有仍用 GTO 已刪之原版 AE2 類別者，故上列建置令用獨立之 `gtoCompatibilityTest`；此項既過，未可遂謂完整 `test` 或 `build` 皆過。

報錯之時，請具 ECO 版本、GTO 整合包版本、實裝 GTOCore／GTM／AE2 版本、重現步驟，並附 `logs/latest.log` 或相應崩潰報告。

## 作者與授權

作者：DancingSnow、ZhuRuoLing、Yang120231、y271727uy。

本專案以 [GNU GPL v3](LICENSE) 授權。
