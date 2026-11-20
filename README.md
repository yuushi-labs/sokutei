# Sokutei

個数を数える専用の Android アプリ。4つのモードで数えます。

> ⚠️ 自動で数えるモードは概算です。背景と物のコントラストがはっきりしている場面向けで、
> 重なった物や模様のある背景では間違えます。限界は [docs/ACCURACY.md](docs/ACCURACY.md) に
> 正直に書いてあります。正確な数が要る場面では手動カウンターかタップ数えを使ってください。

v0.1 は長さ・高さの測定アプリでしたが、目的は個数を数えることだったため、v0.2 で測定機能を廃止し、数えることに絞りました（経緯は SPEC.md）。

## モード

### 1. ライブで数える

カメラをかざすと、写っている物をその場で自動で数えます。撮影はしません。
CameraX の ImageAnalysis の輝度を縮小し、大津二値化＋連結成分で検出。表示は直近フレームの中央値で平滑化します。検出した物の位置にマーカーが出ます。

### 2. 写真で数える

撮影またはギャラリーの写真で数えます。

- 自動: ライブと同じ方式。反転（明るい物を数える）とノイズ除去の下限面積を調整できます
- タップ: 画像をタップした点を数える

### 3. 通過カウント

カメラを固定し、画面中央の水平線を物が横切るたびに数えます（ベルトコンベアや出入り口を想定）。フレーム間で重心を追跡し、上→下 / 下→上を方向別に数えます。線際の震えで二重に数えないようデッドバンドを持ちます。

### 4. 手動カウンター

大きな +1 ボタンを押して数えるだけのカウンター。カメラは使いません。ラベルを付けて履歴に残せます。

## 構成

| モジュール | 内容 |
|---|---|
| `core` | 純粋 Kotlin/JVM の「数える」ロジック。Android に依存しないので単体テストで検証できる |
| `app` | Jetpack Compose + CameraX の Android アプリ。カメラ・画像の橋渡しと UI |

`core` の中身:

- `Otsu` — 大津の二値化
- `BlobCounter` — 連結成分（4近傍）による個数カウント
- `FrameCounter` — グレースケール画像から物を検出する共通処理（各モードが共有）
- `CountSmoother` — ライブ表示の平滑化（直近フレームの中央値）
- `LineCrossingCounter` — 重心追跡とライン横切り判定（通過カウント用）
- `GrayImage` — カメラフレームの回転

## ビルド

JDK 17 と Android SDK（platform 35）が必要です。

```bash
# 初回のみ: Gradle Wrapper の jar を生成（配布経路の都合で jar は未コミット）
gradle wrapper --gradle-version 8.11.1

./gradlew :core:test            # ロジックの単体テスト（JVM のみで実行可能）
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug    # APK: app/build/outputs/apk/debug/
```

CI（GitHub Actions）では core テスト、app 単体テスト、lint、APK ビルドを毎回実行します。APK は CI のアーティファクト（sokutei-debug-apk）から取得できます。

## 技術

- Kotlin 2.1 / Jetpack Compose（Material 3）/ CameraX 1.4
- minSdk 26 / targetSdk 35

## 既知の制約

- 履歴はアプリ起動中のみ保持（永続化は未対応）
- 自動カウントは、物同士がくっついていると1個に数える
- 通過カウントはカメラ固定が前提。物が線の上で重なると誤る
- 実機での精度は未検証（誤差は「分からない」と明記）

詳細な仕様は [SPEC.md](SPEC.md)、精度の議論は [docs/ACCURACY.md](docs/ACCURACY.md) を参照。

## ライセンス

MIT
