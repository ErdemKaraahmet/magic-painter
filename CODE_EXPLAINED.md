# Magic Air Painter — Kod Dosyaları Detaylı Açıklaması

---

## Genel Bakış: Dosyalar Arası İlişki

```
Main.java
  │  her kare için: kamerayı oku → takip et → çiz → ekranda göster
  │
  ├── CameraManager          webcam'i açar, her kareyi aynalayarak verir
  ├── ColorTracker           HSV dönüşümü, renk maskesi, kontur, merkez noktası
  ├── BrushManager           çizim geçmişini tutar, frame üzerine çizer
  ├── OverlayPanel           sanal butonları çizer, hover sayar, buton aksiyonlarını tetikler
  └── ShapeAnalyzer          [FAZ 2 - henüz bağlanmadı] dinamik kalınlık + glow efekti
```

---

## 1. `Main.java` — Uygulamanın Kalbi (Core Loop)

### Ne Yapar?
Tüm modülleri bir araya getirir ve `while` döngüsüyle uygulamayı çalıştırır.

### Başlangıç Kurulumu
- İki `CanvasFrame` penceresi açılır:
  - **"Magic Air Painter"** → kullanıcının gördüğü ana pencere
  - **"Pen Detection Mask"** → binary maske önizlemesi (debug / jüri sunumu için)
- `KeyListener` ile üç tuş dinlenir:
  - `SPACE` → renk kalibrasyonu tetikle
  - `C` → renk kilidini sıfırla, yeniden kalibre et (çizim silinmez)
  - `S` → ekran görüntüsü kaydet
  - `Q` / `ESC` → uygulamadan çık
- Her tuş bir `AtomicBoolean` flag'ine yazar; ana döngü bu flag'leri okur.
  `AtomicBoolean` kullanımının sebebi: Swing event thread ile ana döngünün
  farklı thread'lerde çalışması — thread-safe okuma/yazma gerekli.

### Ana Döngü (`while`) — Her Kare İçin

```
1. camera.grabMirrored()       → yatayda çevrilmiş (mirror) BGR kare al
2. tracker.toHsv(frame)        → HSV kopyası oluştur
3. C tuşu basıldıysa           → tracker.resetCalibration()
4. Renk kilitli DEĞİLSE:
     SPACE basıldıysa          → tracker.calibrate(hsv, frame, roi)
     brush.drawOnFrame(frame)  → önceki çizimi göster (kalibrasyon beklerken kaybolmasın)
     overlay.update(null, ...)  → kalibrasyon kutusu ve yönergeyi çiz
5. Renk KİTLİYSE:
     mask = tracker.buildCleanMask(hsv)   → binary maske üret
     tip  = tracker.findTip(mask)         → nesnenin merkez noktası (cx, cy)
     overlay.isOverAnyButton(tip)?
       EVET → brush.insertBreak()          → buton üstünde, çizme
       HAYIR→ brush.addPoint(tip, color)   → noktayı geçmişe ekle
     brush.drawOnFrame(frame)             → geçmişi kare üzerine çiz
     overlay.update(tip, frame, ...)       → butonları, hover ilerlemesini çiz
     overlay.drawEraserBox(frame, tip)     → silgi modunda etki alanı kutusunu çiz
     maskCanvas.showImage(mask)            → ikinci pencereye maskeyi bas
6. S tuşu basıldıysa           → saveScreenshot(frame)
7. canvas.showImage(frame)     → ana pencereye göster
8. hsv.release(), frame.release() → native belleği serbest bırak
```

### `saveScreenshot(Mat frame)`
- `outputs/` klasörü yoksa `mkdirs()` ile oluşturur.
- Dosya adı: `outputs/screenshot_YYYYMMDD_HHmmss.png`
- `opencv_imgcodecs.imwrite()` ile PNG olarak diske yazar.

---

## 2. `CameraManager.java` — Kamera Yönetimi

### Ne Yapar?
Webcam'i açar, her çağrıda yatayda aynalı bir `Mat` döndürür.

### Aynalama Neden İlk Adım?
Kamera görüntüsü doğal haliyle "ayna yansıması" değildir — sağ el solda görünür.
`opencv_core.flip(captured, mirrored, 1)` (`flipCode=1` = yatay) uygulanınca
kullanıcı ekranda kendini aynada görür gibi görür; el hareketleri sezgisel olur.
Bu işlem koordinat hesabından önce yapılır, aksi hâlde tüm (cx, cy) değerleri
yatayda ters çıkar.

### Çözünürlük Dinamizmi
`grabber.start()` sonrasında `grabber.getImageWidth/Height()` ile sürücünün
fiilen verdiği çözünürlük okunur (1280×720 istenir ama sürücü farklı verebilir).
`OverlayPanel` ve `ColorTracker` bu değerleri alarak buton konumlarını ve ROI
boyutlarını o çözünürlüğe göre hesaplar.

---

## 3. `ColorTracker.java` — Görüntü İşleme Çekirdeği

### Ne Yapar?
Kameradan gelen her kareyi işleyerek takip edilen nesnenin ekrandaki (cx, cy)
koordinatını döndürür. Syllabus konularının tamamı bu sınıfta hayat bulur.

### `toHsv(Mat bgrFrame)`
`cvtColor(frame, hsv, COLOR_BGR2HSV)` ile BGR kareyi HSV renk uzayına çevirir.

**Neden HSV?**
BGR'de aynı nesnenin rengi ışığa göre dramatik değişir.
HSV'de **Hue (ton)** kanalı ışık şiddetinden büyük ölçüde bağımsızdır;
renk tespiti çok daha kararlı hâle gelir.
*(Syllabus W4 — Colour space conversions)*

### `calibrate(Mat hsvFrame, Mat bgrFrame, Rect roi)`
Ekranın ortasındaki kalibrasyon kutusunun (ROI) ortalama HSV ve BGR değerlerini
`opencv_core.mean()` ile hesaplar.

- BGR ortalaması → çizim rengi olarak kaydedilir (kullanıcı hangi renkle çizerse
  kalem de o renkle çizer).
- HSV ortalaması ± tolerans → `lowerBound` ve `upperBound` Scalar'ları:
  - H ± 10, S ± 40, V ± 40
  - S ve V alt limitleri 50'den küçük olamaz (çok soluk / siyah nesneleri dışarıda bırakır).

### `buildCleanMask(Mat hsvFrame)`
İki adımlı temizleme:

**Adım 1 — `inRange()` eşikleme:**
Kilitli HSV sınırları içindeki pikseller beyaz (255), dışındakiler siyah (0) olur.
*(Syllabus W2-3 — Binary thresholding)*

**Adım 2 — Morfolojik Opening:**
```
kernel = 5×5 dikdörtgen yapısal eleman
morphologyEx(mask, mask, MORPH_OPEN, kernel)
```
Opening = önce Erosion (aşındırma), ardından Dilation (genişletme).
- Erosion: küçük gürültü noktalarını yok eder.
- Dilation: asıl nesneyi tekrar eski boyutuna getirir.
*(Syllabus W3 — Binary morphology: erosion, dilation, opening)*

### `findTip(Mat mask)`
Temizlenmiş maskede kontur analizi:

1. `findContours()` → tüm beyaz bölgelerin dış hatlarını bulur.
2. En büyük kontur seçilir (`contourArea()` ile).
3. Alan `MIN_VALID_AREA = 300` px²'den küçükse nesne yok sayılır (gürültü koruması).
4. `moments(largestContour)` ile kütle momentleri hesaplanır:
   - `cx = M10 / M00`
   - `cy = M01 / M00`
   *(Syllabus W3-4 — Region properties, Mass moments)*
5. **EMA (Exponential Moving Average) pürüzsüzleştirme:**
   ```
   smoothedX = rawX * 0.3 + smoothedX * 0.7
   smoothedY = rawY * 0.3 + smoothedY * 0.7
   ```
   Kamera gürültüsünden kaynaklanan ani koordinat sıçramalarını bastırır;
   çizilen çizgi titremeden akıcı görünür.
   Nesne kaybolduğunda `smoothedX/Y = -1` sıfırlanır ki bir sonraki görünmede
   eski konumdan EMA başlamasın.

---

## 4. `BrushManager.java` — Çizim Geçmişi

### Ne Yapar?
Tüm çizim noktalarını bellekte tutar ve her karede bunları frame üzerine çizer.

### Neden Native `Point` Listesi Değil?
JavaCV `Point` nesneleri C++ pointer'ı sarmalıyor. Bunları `List` içinde tutmak
GC ile JavaCV'nin bellek yöneticisini çakıştırır → RAM sızıntısı.
Bu yüzden `StrokePoint` adında saf Java sınıfı kullanılır: `int x, int y,
boolean isBreak, double[] colorBgr, int thickness`.
`Point` / `Scalar` nesneleri yalnızca `drawOnFrame()` içinde kısa ömürlü
olarak oluşturulur ve hemen `close()` ile kapatılır.

### `StrokePoint` ve Gap (Kırılma) Güvenliği
Kalem kaldırıldığında, nesne gizlendiğinde veya mod değiştiğinde listeye
`isBreak=true` olan özel bir işaretçi `(-1, -1)` eklenir.

`drawOnFrame()` içinde ardışık iki nokta çizilirken:
```java
if (a.isBreak || b.isBreak) continue;
```
Bu kontrol sayesinde "kalem kapandı → tekrar açıldı" durumunda önceki son nokta
ile yeni ilk nokta arasında ekranı kesen istem dışı çizgi oluşmaz.

### Çizim Modları
- **PEN:** `thickness = 3 px` — ince, hassas çizgi.
- **BRUSH:** `thickness = 14 px` — kalın fırça darbesi.
- **ERASER:** nokta eklemek yerine `eraseNear(x, y)` çağrılır. Geçmişte yarıçap
  (`ERASER_RADIUS = 50 px`) içinde kalan tüm noktaların yerine `isBreak` işaretçisi
  koyulur; böylece silinen segment otomatik olarak bölünür.
- Mod değişiminde `insertBreak()` çağrılır; eski ve yeni stroke birleşmez.

---

## 5. `OverlayPanel.java` — Sanal Kontrol Paneli

### Ne Yapar?
Ekranda dört sanal buton (PEN, BRUSH, ERASER, CLEAR) çizer ve nesne ucu
üzerlerinde beklediğinde hover/dwell mekanizmasıyla modu değiştirir.

### Buton Yerleşimi — Dinamik Ölçekleme
Buton boyutları ve konumları `frameWidth` / `frameHeight` oranlarına göre
`Math.max()` korumasıyla hesaplanır. 640×480'de de 1280×720'de de butonlar
frame'in sol üst köşesine orantılı yerleşir.

### Hover / Dwell Mekanizması
Her `update()` çağrısında (yani her kare):
- Nesne ucu buton dikdörtgeni içindeyse `b.hoverFrames++`.
- `hoverFrames >= REQUIRED_FRAMES (30)` olduğunda `b.action.run()` tetiklenir
  ve sayaç sıfırlanır.

**Grace Period (Jitter Toleransı):**
Nesne ucu butonu anlık olarak terk ederse (kamera titremesi) sayaç hemen
sıfırlanmaz; `MAX_MISSED_FRAMES = 5` karelık tolerans tanınır. Kullanıcı
gerçekten butonu terk ettiyse ancak o zaman sıfırlanır.

**Progress Fill Animasyonu:**
`ratio = hoverFrames / REQUIRED_FRAMES` oranında butonun sol kısmı nesne rengiyle
doldurulur. Metin rengi `getHsvComplement()` ile hesaplanan tamamlayıcı renge
geçer; doldurulan arka planla kontrast sağlanır.

### `getHsvComplement(Scalar bgr)`
- BGR rengi 1×1 Mat olarak HSV'ye dönüştürülür.
- Hue 90 birim kaydırılır (OpenCV'de H aralığı 0-179; 90 = 180° shift).
- Value: orijinal renk koyuysa metin açık, orijinal renk açıksa metin koyu.
- Saturation minimum 150'ye sabitlenir (soluk tamamlayıcı renkten kaçınılır).

### Kalibrasyon Modu Görsel Geri Bildirimi
Renk kilitli değilken ekranın ortasına siyah dikdörtgen kutu ve
"Hold object in box and press SPACE" metni yazdırılır.

### `drawEraserBox(Mat frame, Point tip)`
ERASER modu aktifken nesne ucunun etrafına `ERASER_RADIUS = 50 px` yarıçaplı
beyaz dikdörtgen çizilir; kullanıcı silginin etki alanını görür.

---

## 6. `ShapeAnalyzer.java` — Faz 2 (Henüz Bağlanmadı)

### Ne Yapar?
Faz 2 özellikleri için iki yardımcı metot içerir. Sınıf yazılmış ama
`Main.java` veya `BrushManager.java` tarafından henüz import edilmiyor.

### `dynamicThickness(double contourArea)`
```java
thickness = (int)(Math.sqrt(contourArea) / 6.0)
```
Nesnenin kameradaki görünür alanı (piksel²) büyüdükçe (kameraya yaklaşınca)
fırça kalınlığı artar. `sqrt` kullanımının sebebi: alanın karekökü nesnenin
çapına orantılıdır; bu çok daha doğal bir his verir.
Sonuç `[MIN=3, MAX=40]` aralığına kısıtlanır.
*(Syllabus W3-4 — Region properties: contour area)*

### `applyGlow(Mat frame, int kernelSize)`
```java
GaussianBlur(frame, frame, kernelSize×kernelSize, sigma=0)
```
Çizilen çizgilere düşük geçiren Gaussian blur uygulanarak "neon ışık saçma"
(glow) illüzyonu yaratılır. `kernelSize` tek sayı olmak zorunda; çift gelirse
otomatik olarak `+1` ile düzeltilir.
*(Syllabus W6 — Spatial filtering, Gaussian filter)*

### Sisteme Bağlamak İçin Yapılması Gerekenler
1. `BrushManager` veya `Main.java` içine `ShapeAnalyzer analyzer = new ShapeAnalyzer()` ekle.
2. `findTip()` dönüşüne ek olarak `tracker.getLastContourArea()` metodunu `ColorTracker`'a ekle.
3. `brush.addPoint()` çağrısı sırasında `analyzer.dynamicThickness(area)` ile
   `StrokePoint.thickness` değerini dinamik olarak ver.
4. `drawOnFrame()` sonrasında `analyzer.applyGlow(frame, 15)` çağır.

---

## Özet Tablo

| Dosya | Ana Sorumluluk | Syllabus Bağlantısı |
|-------|---------------|---------------------|
| `Main.java` | Core loop, pencere yönetimi, klavye, ekran görüntüsü | — |
| `CameraManager.java` | Webcam, BGR yakalama, yatay aynalama | — |
| `ColorTracker.java` | HSV dönüşüm, eşikleme, morfoloji, moment, EMA | W2, W3, W4 |
| `BrushManager.java` | Çizim geçmişi, gap güvenliği, modlar, silgi | — |
| `OverlayPanel.java` | Sanal butonlar, hover/dwell, progress fill, status bar | — |
| `ShapeAnalyzer.java` | [FAZ 2] Dinamik kalınlık, Gaussian glow | W3-4, W6 |
