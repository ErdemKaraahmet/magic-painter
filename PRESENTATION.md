# Magic Air Painter — Sunum Şablonu (10-12 Slayt)

Kural referansı: COMP 4687 CV Software Development Contest — pitch presentation,
10-12 slides; zorunlu içerik: team members, idea, design considerations,
target user group, implementation details, functionalities, library references.

---

## Slayt 1 — Kapak

- **Başlık:** Magic Air Painter (Sihirli Hava Ressamı)
- **Alt başlık:** COMP 4687 — Introduction to Computer Vision, Spring 2026
- Işık Üniversitesi logosu
- **Takım üyeleri:**
  Fatih Altınışık · Mehmet Eren Bombacı · Kaan Emre Evci
  Erdem Karaahmet · Oğuzhan Önder · Elif Zeybekoğlu

---

## Slayt 2 — Fikir ve Motivasyon

- **Tek cümle:** Kameraya gösterilen renkli bir nesneyi havada hareket ettirerek
  canlı video üzerine çizim yapmak.
- Kullanıcı bilgisayara, klavyeye veya fareye **dokunmuyor**; yalnızca kamera var.
- Motivasyon: bilgisayarlı görüyü soyuttan somuta taşımak, eğlenceli ve erişilebilir
  bir interaksiyon deneyimi sunmak.
- Kısa demo GIF / ekran görüntüsü (uygulamanın çalışır hali)

---

## Slayt 3 — Hedef Kullanıcı Kitlesi

- **Eğitimciler ve öğrenciler:** akıllı tahta olmayan sınıflarda dokunmatik
  yüzey simülasyonu.
- **Yaratıcı kullanıcılar:** fare veya dokunmatik ekran olmadan dijital çizim.
- **Erişilebilirlik:** ince motor becerisi kısıtlı bireyler için temassız arayüz.
- Herhangi bir donanım eklentisi gerektirmez — sadece webcam yeterli.

---

## Slayt 4 — Tasarım Kararları

- **Dil: Java** — cross-platform (Mac Intel/Apple Silicon + Windows), tek kod tabanı.
- **Saf Bilgisayarlı Görü:** MediaPipe / YOLO / AI tabanlı el takibi **kullanılmadı**;
  tüm algoritmalar müfredattaki konularla örtüşüyor.
- **Dinamik renk kalibrasyonu:** sabit renk yerine kullanıcı kendi nesnesini ekrana
  tutup SPACE ile kitleme yapıyor; ışık koşullarından bağımsız çalışıyor.
- **Çizim geçmişi bellekte:** bulut/veritabanı yok, tüm veri yerel (local) tutuluyor.
- **Hover/Dwell seçimi:** fare tıklaması yerine 1 saniyelik bekleme ile buton seçimi.

---

## Slayt 5 — Kullanılan Kütüphaneler ve Atıflar

| Kütüphane | Sürüm | Kullanım Amacı |
|-----------|-------|----------------|
| **JavaCV** (org.bytedeco) | 1.5.10 | OpenCV Java wrapper; OS'a özgü native binary'leri otomatik yükler |
| **OpenCV** (JavaCV içinde) | 4.7.x | Tüm görüntü işleme operasyonları |
| **JavaCPP** (JavaCV bağımlılığı) | — | JNI köprüsü; C++ pointer yönetimi |
| **Maven Shade Plugin** | 3.5.1 | Dağıtılabilir fat JAR üretimi |

Kaynak: https://github.com/bytedeco/javacv

---

## Slayt 6 — CV Algoritmaları: Renk Takibi (Syllabus Haftaları 2-4)

Üç adımlı pipeline görseli (akış diyagramı):

1. **BGR → HSV dönüşümü** *(W4 — Colour space conversions)*
   Işık ve gölge değişimlerinden etkilenmeyen HSV kanalında renk tespiti.

2. **İkili Eşikleme — `inRange()`** *(W2-3 — Binary thresholding)*
   Kilitlenen nesne rengi: beyaz piksel; arka plan: siyah piksel.

3. **Morfolojik Opening (Erosion + Dilation)** *(W3 — Binary morphology)*
   Küçük gürültü bölgelerini yok et, asıl nesneyi koru ve pürüzsüzleştir.

---

## Slayt 7 — CV Algoritmaları: Koordinat ve Çizim (Syllabus Haftaları 3-6)

4. **Kontur bulma + Kütle Momenti (Centroid)** *(W3-4 — Region properties)*
   En büyük kontur → M10/M00 ve M01/M00 formülleriyle (cx, cy) hesabı.
   Bu nokta fırçanın ucudur.

5. **EMA Pürüzsüzleştirme** (bonus)
   Smoothing factor 0.3 ile kamera titremesi bastırılır, çizgi akışkan görünür.

6. **Gaussian Blur — Neon Efekti** *(W6 — Spatial filtering)*
   Faz 2: düşük geçiren filtre ile çizgilere "glow" illüzyonu.

---

## Slayt 8 — Özellikler (Functionalities)

**Çizim Modları (hover ile seçilir):**
- PEN — ince kalem (3 px)
- BRUSH — kalın fırça (14 px)
- ERASER — çevre noktalara göre bölgesel silgi (50 px yarıçap)
- CLEAR — tüm tuvali temizle

**Kalibrasyon & Kontrol:**
- SPACE: ekran ortasındaki kutuya nesneyi tutup rengi kilitle
- C: rengi sıfırla, yeni nesneyle yeniden kalibre et
- S: mevcut çizimi `outputs/screenshot_YYYYMMDD_HHmmss.png` olarak kaydet
- Q / ESC: uygulamadan çık

**Görsel Geri Bildirimler:**
- Hover sırasında buton soldan sağa renk doluşuyla dolar (~1 saniye)
- Silgi modunda etki alanını gösteren beyaz kutu
- Alt status bar: aktif mod + kısayol ipuçları
- İkinci pencere: canlı ikili maske önizlemesi (debug)

---

## Slayt 9 — Mimari ve Kod Yapısı

Paket diyagramı (kutu-ok gösterimi):

```
Main.java  (core loop)
    │
    ├── camera/CameraManager      kamera açma, BGR yakalama, flip
    ├── vision/ColorTracker       HSV dönüşüm, maskeleme, morfoloji, centroid, EMA
    ├── vision/ShapeAnalyzer      dinamik fırça kalınlığı (contourArea), Gaussian glow
    └── ui/
        ├── OverlayPanel          sanal butonlar, hover/dwell zamanlayıcı, status bar
        └── BrushManager          çizim geçmişi (pointsHistory), gap güvenlik kontrolü
```

- Bellek yönetimi: her Mat nesnesi döngü sonunda `.release()` ile serbest bırakılır.
- UI koordinatları kamera çözünürlüğüne (640x480 veya 1280x720) göre dinamik ölçeklenir.

---

## Slayt 10 — Canlı Demo

- Uygulama açık, kamera aktif.
- Adımlar jüri önünde gösterilir:
  1. Nesneyi kutuya tut → SPACE ile kalibre et.
  2. PEN moduyla serbest çizim.
  3. BRUSH moduna hover ile geç.
  4. ERASER ile bir kısmı sil.
  5. S tuşuyla ekran görüntüsü al.
  6. C ile rengi değiştir.

> Demo sırasında "Pen Detection Mask" penceresini de göster — jüriye algoritmanın
> çıktısını doğrudan görme imkânı tanır.

---

## Slayt 11 — Sonuç ve Teşekkür

- **Öğrendiklerimiz:** Renk uzayı dönüşümleri, morfoloji, moment tabanlı bölge
  analizi ve uzamsal filtrelemenin gerçek zamanlı bir üründe nasıl bir arada
  çalıştığını gördük.
- **Zorluklar:** Değişen ışık koşullarında kararlı renk takibi; jitter bastırma
  (EMA + grace period).
- **Gelecek:** Gaussian glow efektinin tam entegrasyonu, çizim export seçenekleri.
- Teşekkürler — Prof. Dr. Devrim Akca ve COMP 4687 ekibine.

---

## Notlar

- Toplam: **11 slayt** (kurala göre 10-12 arası).
- Slayt 6-7 gerekirse tek slaytta birleştirilebilir (10 slayda indirilir).
- Slayt 10 gerekirse kaldırılıp yerine daha detaylı bir demo video QR kodu eklenebilir.
- Görsel önerisi: her CV adımı için gerçek bir ekran görüntüsü (ham frame / HSV mask /
  temiz mask / sonuç) yan yana dört panel olarak konulabilir.
