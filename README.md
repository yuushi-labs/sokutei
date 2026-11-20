# Sokutei（測定）

カメラとセンサで「なんでも測る」Android アプリ。
長さ・高さ・個数を、3つのモードで測定します。

> ⚠️ 計量法上の「計量器」ではありません。どのモードも誤差を含みます。
> 正確な値が必要な場面（取引・証明・工事など）では使わないでください。
> 精度の目安と限界は [docs/ACCURACY.md](docs/ACCURACY.md) に正直に書いてあります。

## モード

### 1. 上下スキャンで測る（＋傾けて測る）

スマホを物体に近づけ、**下端の高さで静止 → 測定開始 → ゆっくり上端まで持ち上げる → 静止して停止**。
加速度センサ（線形加速度＋回転ベクトル）の鉛直成分を二重積分し、
ZUPT（静止区間で速度ゼロ補正）と線形ドリフト補正で誤差を抑えます。

サブモード「傾けて測る」は三角測量です。カメラの高さを入力し、
照準を下端・上端に合わせて仰角を記録すると高さが出ます。
近づけない物（建物など）向け。

### 2. 静止画で測る

撮影またはギャラリーの写真で測ります。

1. 基準物（実寸が分かる物：クレジットカード 85.6mm、1円玉 20mm、A4 など）の両端をタップ
2. 実寸を選ぶ／入力する
3. 測りたい物の点をタップ（2点で直線、3点以上で折れ線の合計長）

基準物と測る物が**同じ平面**にあることが前提です。

### 3. 個数を数える

写真の中の物を数えます。

- 自動: 縮小 → グレースケール → 大津の二値化 → 連結成分ラベリング。
  反転（明るい物を数える）とノイズ除去の下限面積を調整できます
- 手動: タップした点を数える

## 構成

| モジュール | 内容 |
|---|---|
| `core` | 純粋 Kotlin/JVM の測定ロジック。Android に依存しないので単体テストで全て検証できる |
| `app` | Jetpack Compose + CameraX の Android アプリ。センサと画像処理の橋渡しと UI |

`core` の中身:

- `VerticalDisplacementEstimator` — 鉛直加速度の二重積分（ZUPT＋ドリフト補正）
- `TiltMath` — 三角測量（カメラ高と下端・上端の仰角から高さ）
- `PixelScale` — 静止画のピクセル⇔実寸スケール（折れ線対応）
- `Otsu` — 大津の二値化
- `BlobCounter` — 連結成分（4近傍）による個数カウント
- `LengthFormat` — mm/cm/m の整形

## ビルド

JDK 17 と Android SDK（platform 35）が必要です。

```bash
# 初回のみ: Gradle Wrapper の jar を生成（配布経路の都合で jar は未コミット）
gradle wrapper --gradle-version 8.11.1

./gradlew :core:test            # ロジックの単体テスト（JVM のみで実行可能）
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug    # APK: app/build/outputs/apk/debug/
```

CI（GitHub Actions）では core テスト、app 単体テスト、lint、APK ビルドを毎回実行します。

## 技術

- Kotlin 2.1 / Jetpack Compose（Material 3）/ CameraX 1.4
- minSdk 26 / targetSdk 35
- センサ: `TYPE_LINEAR_ACCELERATION` + `TYPE_ROTATION_VECTOR`

## 既知の制約

- 測定履歴はアプリ起動中のみ保持（永続化は未対応）
- 上下スキャンの精度は端末のセンサ品質と動かし方に大きく依存する
- 個数カウントは、物同士がくっついていると1個に数える
- ARCore は使っていない（対応端末を限定しない代わりに、絶対精度は出ない）

詳細な仕様は [SPEC.md](SPEC.md)、精度の議論は [docs/ACCURACY.md](docs/ACCURACY.md) を参照。

## ライセンス

MIT
