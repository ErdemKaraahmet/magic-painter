# **Magic Air Painter (Sihirli Hava Ressamı) \- Proje Spesifikasyon Dokümanı**

Bu doküman, **Işık Üniversitesi COMP 4687 \- Introduction to Computer Vision (Spring 2026\)** dersi dönem sonu yarışması (contest) için geliştirilecek olan **Java** tabanlı, cross-platform çalışabilen temassız hava çizim uygulamasının tüm teknik ve mimari detaylarını içermektedir.

Bu dosya, hem geliştirici ekibin ortak dili konuşmasını sağlamak hem de yazılım üreten yapay zeka ajanlarının (coding agents) projeyi sıfır hata ile ayağa kaldırabilmesi için bir **ana kılavuz (specification sheet)** olarak tasarlanmıştır.

## **0\. Proje Ekibi**

| İsim | Soyisim |
| :---- | :---- |
| Fatih | Altınışık |
| Mehmet Eren | Bombacı |
| Kaan Emre | Evci |
| Erdem | Karaahmet |
| Oğuzhan | Önder |
| Elif | Zeybekoğlu |

## **1\. Proje Genel Tanımı (Context)**

"Magic Air Painter", kullanıcının bilgisayara, klavyeye veya fareye dokunmadan, sadece kameraya gösterdiği herhangi bir renkli nesneyi (kalem kapağı, renkli karton, pinpon topu vb.) havada hareket ettirerek doğrudan canlı webcam görüntüsü üzerine pürüzsüz çizimler yapmasını sağlayan bir bilgisayarlı görü (computer vision) uygulamasıdır.

### **Temel Çalışma Felsefesi:**

Uygulama, ekranda yarı şeffaf bir **Sanal Kontrol Paneli (Overlay UI)** barındırır. Kullanıcı, elindeki çizim nesnesini bu butonların üzerinde **2 saniye (60 kare)** beklettiğinde mod seçimi (Kalem, Fırça, Sprey, Ekranı Temizle vb.) yapar. Çizim işlemi, yapay bir tuval yerine doğrudan aynalanmış (mirrored) canlı video akışı üzerinde gerçekleşir.

## **2\. Teknik Stack ve Kütüphaneler**

Projenin Mac (Intel & Apple Silicon) ve Windows işletim sistemlerinde sıfır kurulum ve maksimum cross-platform uyumluluğu ile çalışması için aşağıdaki bağımlılıklar ve kütüphaneler seçilmiştir:

### **A. Core Stack**

* **Dil:** Java (JDK 17 veya üzeri önerilir)  
* **Build System:** Maven veya Gradle

### **B. Bağımlılıklar (Dependencies)**

Doğrudan OpenCV native kütüphaneleriyle ve işletim sistemine özel .dll / .dylib bağlama zahmetiyle uğraşmamak için **JavaCV** platform paketi kullanılacaktır. Bu kütüphane, işletim sistemini otomatik algılayıp uygun OpenCV ikililerini (binaries) arka planda yükler.

#### **Maven Bağımlılığı (pom.xml):**

\<dependencies\>  
    \<\!-- JavaCV platform paketi: Tüm OS binary'lerini otomatik barındırır \--\>  
    \<dependency\>  
        \<groupId\>org.bytedeco\</groupId\>  
        \<artifactId\>javacv-platform\</artifactId\>  
        \<version\>1.5.10\</version\>  
    \</dependency\>  
\</dependencies\>

#### **Gradle Bağımlılığı (build.gradle):**

dependencies {  
    implementation 'org.bytedeco:javacv-platform:1.5.10'  
}

## **3\. Syllabus ve Bilgisayarlı Görü (CV) Algoritmaları Eşleşmesi**

Bu proje, COMP 4687 ders müfredatında gösterilen teorik konuların çalışan bir üründe nasıl hayat bulduğunu kanıtlamak üzere tasarlanmıştır. Yarışma sunumunda jüriye (Prof. Dr. Devrim Akca'ya) gösterilecek akademik eşleşmeler şu şekildedir:

### **1\. Renk Uzayı Dönüşümü (Color Space Conversions)**

* **Kullanım Yeri:** Kameradan gelen BGR (RGB) görüntüsü, ışık değişimlerinden (gölge, parlama, sınıf ışıkları) çok çabuk etkilenir. Nesne takibinin kararlı olması için görüntü **HSV** (Hue, Saturation, Value) renk uzayına çevrilir.  
* **Syllabus Konusu:** *Week 4 (Transformations: colour to gray, RGB colour bases).*

### **2\. İkilik Eşikleme (Binary Thresholding)**

* **Kullanım Yeri:** Kilitlenen nesne renginin alt ve üst HSV sınırları kullanılarak Core.inRange() ile maskeleme yapılır. Görüntü, hedefin beyaz (![][image1]) ve arka planın siyah (![][image2]) olduğu bir ikili görüntüye (Binary Image) dönüştürülür.  
* **Syllabus Konusu:** *Week 2 & 3 (Binary Images, Thresholding).*

### **3\. Matematiksel Morfoloji (Mathematical Morphology)**

* **Kullanım Yeri:** Sınıf ortamındaki gürültüleri ve ışık parazitlerini temizlemek için ikili maskeye **Opening (Açma)** işlemi uygulanır. Önce **Erosion (Aşındırma)** ile küçük gürültüler yok edilir, ardından **Dilation (Genişletme)** ile asıl takip edilen nesne eski boyutuna getirilerek pürüzsüzleştirilir.  
* **Syllabus Konusu:** *Week 3 (Connected components labelling, Binary morphology: Erosion, dilation, opening and closing).*

### **4\. Kütle ve Bölge Özellikleri (Region Properties & Mass Moments)**

* **Kullanım Yeri:** Temizlenen maske üzerindeki en büyük kontur (beyaz alan) bulunur. Bu alanın geometrik kütle merkezi (Centroid), sıfırıncı ve birinci dereceden momentler (![][image3]) kullanılarak ![][image4] ve ![][image5] formülüyle hesaplanır. Bu koordinat, çizim fırçamızın tam ucudur.  
* **Syllabus Konusu:** *Week 3 & 4 (Connected components labelling, Region properties: Area, perimeter).*

### **5\. Uzamsal Filtreleme (Spatial Filtering \- Faz 2\)**

* **Kullanım Yeri:** Gelişmiş fırça modlarında neon / ışık kılıcı efekti yaratmak için çizilen çizgilere düşük geçiren **Gaussian Blur** filtresi uygulanarak ışık saçma (glow) illüzyonu oluşturulur.  
* **Syllabus Konusu:** *Week 6 (Spatial filtering, Smoothing filters, Gaussian filter).*

## **4\. Proje Kapsamı (Scope Matrix)**

| Kapsam Dahilinde (In Scope) | Kapsam Dışında (Out of Scope) |
| :---- | :---- |
| Canlı webcam görüntüsünün yatayda ters çevrilmesi (Mirroring/Flip) | Yapay Zeka (AI) tabanlı el/parmak eklem takibi (MediaPipe/YOLO kullanmak yok; saf CV kullanılacak) |
| Ekranda kilitli bir ROI kutusu ile dinamik renk kalibrasyonu (Sampling) | Cloud tabanlı kullanıcı kaydı veya Firestore entegrasyonu (İlk aşamada her şey local/bellekte) |
| 2 Saniye (60 Kare) Hover/Dwell zamanlayıcısı ile sanal buton seçimi | Çoklu el ile eşzamanlı çizim yapılması |
| Kesintisiz çizgi çizimi için geçmiş koordinat zinciri ve "Aralık Engelleme" (Gap check) | Mobil uygulama desteği veya web tabanlı arayüz |
| Faz 1: Kalem, Kalın Fırça, Silgi, Ekranı Temizle modları | 3D uzayda çizim yapma |
| Faz 2: Objenin kameradaki alanına (contourArea) göre fırça kalınlığı |  |

## **5\. Proje Klasör Yapısı (Folder Structure)**

Projenin Git üzerinde çakışma (conflict) yaratmadan paralel geliştirilebilmesi için modüler paket tasarımı:

magic-painter/  
│  
├── pom.xml                           \# JavaCV / OpenCV bağımlılıklarını yöneten Maven dosyası  
├── README.md                         \# Çalıştırma kılavuzu ve teknik özet  
│  
└── src/  
    └── main/  
        ├── java/  
        │   └── com/  
        │       └── isikun/  
        │           └── magicpainter/  
        │               │  
        │               ├── Main.java         \# Uygulamanın giriş noktası (Core loop)  
        │               │  
        │               ├── camera/  
        │               │   └── CameraManager.java   \# Kamera bağlantısı, frame yakalama ve Aynalama (Flip)  
        │               │  
        │               ├── vision/  
        │               │   ├── ColorTracker.java    \# HSV dönüşümü, Morfoloji, Moments ve koordinat tespiti  
        │               │   └── ShapeAnalyzer.java   \# \[FAZ 2\] Contour Area ve fırça boyutu hesaplama  
        │               │  
        │               └── ui/  
        │                   ├── OverlayPanel.java    \# Sanal butonların, yazıların ve Loading Arc efektinin çizilmesi  
        │                   └── BrushManager.java    \# Çizim geçmişi (Points) yönetimi ve Gap kontrolü

## **6\. Detaylı Mimari ve Algoritmik Akış**

Yazılım ajanlarının projeyi koda dökerken ana döngü (while(camera.read())) içinde uygulayacağı akış diyagramı ve algoritma adımları:

\[Kamera Kareyi Oku\] ➔ \[Yatayda Aynala (Flip)\] ➔ \[Kareyi Kopyala & HSV'ye Çevir\]  
                                                      │  
 ┌────────────────────────────────────────────────────┘  
 ▼  
\[Renk Kilitli mi?\]   
 ├─► HAYIR: \[Kalibrasyon Kutusunu Çiz (ROI)\] ➔ \[Tuşa Basılınca Ortalama HSV Al\]  
 │  
 └─► EVET:   
     ▼  
   \[Maskele (inRange)\] ➔ \[Morfolojik Temizleme (Opening)\] ➔ \[En Büyük Konturu Bul\]  
                                                                  │  
 ┌────────────────────────────────────────────────────────────────┘  
 ▼  
\[Alan Limitleri Uygun mu?\]  
 ├─► EVET: \[Moments ile Merkez (cx, cy) Hesapla\]  
 │         │  
 │         ▼  
 │       \[Sanal Buton Alanlarında mı? (Hover Kontrolü)\]  
 │        ├─► EVET: \[Sayaç Arttır (++hoverFrames) \>= 60 ise MOD DEĞİŞTİR\]  
 │        └─► HAYIR: \[Moda Göre Çizim Yap\] ➔ \[PointsHistory Listesine Ekle\]  
 │  
 └─► HAYIR (Fiziksel Kapatma): \[PointsHistory Listesine Geçersiz Nokta (-1,-1) Ekle\]  
                               │  
 ┌─────────────────────────────┘  
 ▼  
\[PointsHistory Listesini Canlı Kare Üzerine Çiz\] ➔ \[Sanal UI'ı Üstüne Bas (Overlay)\] ➔ \[Ekranda Göster\]

## **7\. Kritik Kod Snippet'ları (JavaCV)**

Aşağıdaki kod blokları, projenin en kritik mekanizmalarının JavaCV kütüphanesi kullanılarak nasıl gerçekleştirileceğini gösteren şablonlardır.

### **A. Dynamic Color Calibration (Renk Kilitleme)**

Kullanıcı kalemi ekranın ortasındaki hedef kutusuna getirdiğinde tetiklenecek ortalama değer ve tolerans hesabı:

import org.bytedeco.opencv.opencv\_core.\*;  
import org.bytedeco.opencv.global.opencv\_core;  
import org.bytedeco.opencv.global.opencv\_imgproc;

public class CalibrationHelper {  
    // Dinamik alt ve üst sınırlar  
    private Scalar lowerBound \= new Scalar();  
    private Scalar upperBound \= new Scalar();  
    private Scalar avgBGR \= new Scalar(); // Çizim fırçasının rengi olacak

    public void calibrateColor(Mat hsvFrame, Mat originalFrame, Rect roi) {  
        // Sadece hedef kutusunun (Region of Interest) alt matrisini al  
        Mat roiHSV \= new Mat(hsvFrame, roi);  
        Mat roiBGR \= new Mat(originalFrame, roi);

        // Ortalama HSV ve BGR değerlerini hesapla  
        Scalar meanHSV \= opencv\_core.mean(roiHSV);  
        this.avgBGR \= opencv\_core.mean(roiBGR);

        // HSV Tolerans Değerleri (H: \+-10, S: \+-40, V: \+-40)  
        double h \= meanHSV.get(0);  
        double s \= meanHSV.get(1);  
        double v \= meanHSV.get(2);

        this.lowerBound \= new Scalar(  
            Math.max(0, h \- 10),   
            Math.max(50, s \- 40),   
            Math.max(50, v \- 40),   
            0  
        );  
          
        this.upperBound \= new Scalar(  
            Math.min(180, h \+ 10),   
            Math.min(255, s \+ 40),   
            Math.min(255, v \+ 40),   
            0  
        );  
    }  
}

### **B. Tracking & Centroid Extraction (Syllabus'a Sunulacak Kısım)**

Opening filtresi ve Mass Moments kullanılarak nesne takibi:

import org.bytedeco.opencv.opencv\_core.\*;  
import org.bytedeco.opencv.opencv\_imgproc.\*;  
import org.bytedeco.opencv.global.opencv\_imgproc;  
import java.util.ArrayList;  
import java.util.List;

public class ObjectTracker {  
      
    public Point trackObject(Mat binaryMask) {  
        // 1\. Morfolojik Opening (Erosion \+ Dilation) \- Gürültü Temizleme  
        Mat kernel \= opencv\_imgproc.getStructuringElement(opencv\_imgproc.MORPH\_RECT, new Size(5, 5));  
        opencv\_imgproc.morphologyEx(binaryMask, binaryMask, opencv\_imgproc.MORPH\_OPEN, kernel);

        // 2\. Konturları Bul  
        MatVector contours \= new MatVector();  
        opencv\_imgproc.findContours(binaryMask, contours, opencv\_imgproc.RETR\_EXTERNAL, opencv\_imgproc.CHAIN\_APPROX\_SIMPLE);

        if (contours.size() \> 0\) {  
            // En büyük konturu (kalemi) bul  
            Mat largestContour \= contours.get(0);  
            double maxArea \= opencv\_imgproc.contourArea(largestContour);

            for (long i \= 0; i \< contours.size(); i++) {  
                Mat c \= contours.get(i);  
                if (opencv\_imgproc.contourArea(c) \> maxArea) {  
                    largestContour \= c;  
                    maxArea \= opencv\_imgproc.contourArea(c);  
                }  
            }

            // Eğer bulunan alan yeterince büyükse (parazit değilse)  
            if (maxArea \> 300\) {  
                // Mass Moments hesabı ile Merkez Noktası (Centroid) bulma  
                Moments mu \= opencv\_imgproc.moments(largestContour);  
                int cx \= (int) (mu.m10() / mu.m00());  
                int cy \= (int) (mu.m01() / mu.m00());  
                  
                return new Point(cx, cy);  
            }  
        }  
        return null; // Kalem bulunamadıysa (Fiziksel Kapatma)  
    }  
}

### **C. Continuous Drawing with Gap Safety Check (Kesintisiz Çizim & Kırılma Kontrolü)**

Kalem kapatıldığında ekranın ortasından geçen istem dışı dikey çizgileri engelleme algoritması:

import org.bytedeco.opencv.opencv\_core.\*;  
import org.bytedeco.opencv.global.opencv\_imgproc;  
import java.util.ArrayList;  
import java.util.List;

public class PaintCanvas {  
    // Çizim geçmişini hafızada tutan dinamik liste  
    private List\<Point\> pointsHistory \= new ArrayList\<\>();  
      
    public void addPoint(Point p) {  
        if (p \== null) {  
            // Kalem kapandıysa kırılma işareti ekle  
            pointsHistory.add(new Point(-1, \-1));  
        } else {  
            pointsHistory.add(p);  
        }  
    }

    public void drawHistoryOnFrame(Mat frame, Scalar color, int thickness) {  
        for (int i \= 1; i \< pointsHistory.size(); i++) {  
            Point p1 \= pointsHistory.get(i \- 1);  
            Point p2 \= pointsHistory.get(i);

            // Eğer araya kırılma işareti girdiyse bu adımı çizmeden atla (Gap Check)  
            if (p1.x() \== \-1 || p2.x() \== \-1) {  
                continue;  
            }

            // Ardışık iki noktayı pürüzsüzce bağla  
            opencv\_imgproc.line(frame, p1, p2, color, thickness, opencv\_imgproc.LINE\_AA, 0);  
        }  
    }  
}

### **D. Hover/Dwell UI Controller with 2-Second Timer (2 Saniyelik Seçim Mekanizması)**

Buton üzerinde 2 saniye (60 kare) kalınca modu güncelleyen ve etrafına dairesel yükleme çubuğu çizen kontrolör:

import org.bytedeco.opencv.opencv\_core.\*;  
import org.bytedeco.opencv.global.opencv\_imgproc;

public class UIHoverController {  
    private static final int REQUIRED\_FRAMES \= 60; // 30 FPS için 2 saniye  
    private int hoverFrameCount \= 0;  
    private String activeMode \= "PEN";

    private Rect clearButtonRect \= new Rect(20, 50, 120, 60);

    public void update(Point drawingTip, PaintCanvas canvas, Mat displayFrame) {  
        // Sanal Butonu Ekrana Çiz  
        opencv\_imgproc.rectangle(displayFrame, clearButtonRect, new Scalar(0, 0, 255, 0), 2);  
        opencv\_imgproc.putText(displayFrame, "TEMIZLE", new Point(35, 85),   
                opencv\_imgproc.FONT\_HERSHEY\_SIMPLEX, 0.5, new Scalar(255, 255, 255, 0), 1, opencv\_imgproc.LINE\_AA, false);

        if (drawingTip \!= null && isInside(drawingTip, clearButtonRect)) {  
            hoverFrameCount++;

            // GÖRSEL GERİ BİLDİRİM: Butonun etrafında dolan yüklenme dairesi (Arc) çizimi  
            double progressRatio \= (double) hoverFrameCount / REQUIRED\_FRAMES;  
            int angle \= (int) (progressRatio \* 360);  
              
            Point center \= new Point(clearButtonRect.x() \+ clearButtonRect.width() / 2,   
                                     clearButtonRect.y() \+ clearButtonRect.height() / 2);  
              
            opencv\_imgproc.ellipse(displayFrame, center, new Size(65, 35), 0, 0, angle,   
                                   new Scalar(0, 255, 0, 0), 3, opencv\_imgproc.LINE\_AA, 0);

            if (hoverFrameCount \>= REQUIRED\_FRAMES) {  
                canvas.clear(); // 2 saniye doldu, ekranı sıfırla\!  
                hoverFrameCount \= 0;  
            }  
        } else {  
            hoverFrameCount \= 0; // Kalem butondan çıkınca süreyi sıfırla  
        }  
    }

    private boolean isInside(Point p, Rect r) {  
        return p.x() \>= r.x() && p.x() \<= (r.x() \+ r.width()) &&  
               p.y() \>= r.y() && p.y() \<= (r.y() \+ r.height());  
    }  
}

## **8\. Gerçek Uygulama Notları (Codebase ile Spec Farkları)**

Aşağıdaki özellikler kodda mevcut olup yukarıdaki spesifikasyona henüz yansıtılmamıştır. Geliştirici ajanlar bu bölümü **gerçeğin kaynağı (source of truth)** olarak kabul etmelidir.

### **A. Hover Süresi: 30 Kare (~1 Saniye)**

`OverlayPanel.java` içindeki `REQUIRED_FRAMES = 30` olarak tanımlanmıştır. Spec'te belirtilen 60 kare (2 saniye) değeri **uygulanmamıştır**; gerçek eşik 1 saniyedir.

### **B. Hover Geri Bildirimi: Soldan Sağa Doluş (Fill) Animasyonu**

Spec'te dairesel yükleme yayı (arc) önerilmişti; gerçek uygulamada buton arka planı **soldan sağa ilerleyen** bir renk doluşuyla dolmaktadır. Buton metninin rengi, nesne rengiyle kontrast sağlamak için **HSV tamamlayıcı renk (complement)** algoritmasıyla hesaplanmaktadır (`OverlayPanel.getHsvComplement()`).

### **C. Grace Period (Jitter Toleransı)**

`OverlayPanel` içinde `MAX_MISSED_FRAMES = 5` parametresiyle küçük algılama kesintileri (jitter) tolere edilmektedir. Çizim ucu butona üzerine geldiğinde, 5 karelık bir kesinti hover sayacını sıfırlamamaktadır.

### **D. EMA (Exponential Moving Average) Pürüzsüzleştirme**

`ColorTracker.java` içinde `SMOOTHING_FACTOR = 0.3` ile çizim ucu koordinatlarına **Üstel Hareketli Ortalama** uygulanmaktadır. Bu, kamera gürültüsünden kaynaklanan titremeleri azaltır ve daha akıcı çizgi üretir.

### **E. C Tuşu ile Yeniden Kalibrasyon**

`Main.java` içinde `VK_C` tuşuna basıldığında `tracker.resetCalibration()` çağrılır ve uygulama kalibrasyon moduna geri döner. Mevcut çizim silinmez; yalnızca renk kilidi açılır.

### **F. Canlı Maske Önizleme Penceresi**

Renk kilitlendikten sonra ikinci bir `CanvasFrame` ("Pen Detection Mask") açılır ve işlenmiş ikili maskeyi (binary mask) gerçek zamanlı olarak gösterir. Bu pencere hata ayıklama (debug) ve jüri sunumu için tasarlanmıştır.

### **G. Silgi Modu Görsel Kutusu**

Silgi (ERASER) modunda aktifken `OverlayPanel.drawEraserBox()` metodu, çizim ucunun çevresine beyaz bir dikdörtgen çizer ve silme etki alanını kullanıcıya gösterir. Silgi yarıçapı `BrushManager.ERASER_RADIUS = 50` pikseldir.

### **H. Alt Durum Çubuğu (Status Bar)**

Her karede ekranın alt kısmına aktif mod (`Mode: PEN`) ve kısayol ipuçları (`C: new color | Q: quit`) yazdırılmaktadır.

### **I. Maven Shade Plugin (Fat JAR)**

`pom.xml` içinde `maven-shade-plugin` tanımlıdır. `mvn clean package` komutu tüm bağımlılıkları içeren tek bir çalıştırılabilir JAR (`magic-painter-1.0.0.jar`) üretir. Geliştirme sırasında `mvn compile exec:java` kullanılabilir.

---

## **9\. Geliştirici Ajanlar (Coding Agents) İçin Önemli Notlar**

1. **Aynalama İlk Adım Olmalıdır:** Kamera karesi CameraManager tarafından yakalandığı an, koordinat işlemlerine başlanmadan önce opencv\_core.flip(src, dest, 1\) ile yatayda aynalanmalıdır.  
2. **Kamera Boyutları Dinamik Olmalıdır:** Koordinat hesaplamalarının taşmaması için kamera çözünürlüğü (genelde ![][image6] veya ![][image7]) başlangıçta okunmalı, UI butonları ve ROI kutuları bu boyutlara göre dinamik ölçeklenmelidir.  
3. **Bellek Yönetimi (JavaCV Memory Leaks):** JavaCV nesneleri (özellikle Mat) C++ pointer'ları barındırdığı için çöp toplayıcıya (GC) bırakılmadan önce yoğun döngülerin içinde mat.release() veya try-with-resources bloğu ile kapatılarak RAM sızıntısı engellenmelidir.  
4. **Kod Standartları, Dil ve Emoji Kuralları (Coding Standards, Language & Emoji Rules):** Kod geliştiren tüm yapay zeka ajanları (coding agents) kod içerisindeki tüm yorum satırlarını (inline comments), dökümantasyonları ve değişken açıklamalarını kesinlikle İngilizce yazmalıdır; Türkçe yorum satırı kullanılmamalıdır. Ayrıca kodun içinde, yorum satırlarında veya dökümantasyon dosyalarında hiçbir şekilde emoji kullanılmamalıdır. (Coding agents must write all inline comments, variables, and documentation strictly in English without using Turkish. Additionally, no emojis are allowed anywhere in the codebase or comment sections.)

[image1]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAB8AAAAZCAYAAADJ9/UkAAACXElEQVR4XsVUTUscQRCd8QPFLISAw8J+9OzOruBNYQ8e9KYHf0BOQnLOLYgnD4LgIT8g4Dkg/gAFRVHIZSOeBRFEL4I3IbnEhM2Xr2a6Z2tqp3fHKPqg6KlXr7qqenrGcXrB7UlotHnXJukJk/g/G2TJyarp1HUyEvdS9BY7GUUM99Ur5c/4vn8D+wc7BtUnNeC/KaXewF4FQfAS62twX7mmUqlsgVsvl8s1uG61Wp2Abg/8JNfFgPgjJTD/lppQvgoMR8PoxhJWLBZLscANNc2ERilaN62nQSJ0NsXPyyQzWchhig9Y17HO8pjJRewz9lrEugFbSmoE8vn8i7RCKoWTfhqgOXTkW7dNTUDCGiaZFlxYnOelFhcbo+kDyXarHUMWgv1llHk951hPYUew36AHuAZD7MPeI9bC+slXYc67TA0QID6hQoVCYYTzxI3V60PM3+WnQQWQuw1bNRyeh0mDRuYMZwVdPBLX62OejElAO65PaCV9svgSkqbzlUWIRPT9RoXb0wn0C79Pb3xmiEajMcgFBN8PPzdRPHktwo14CP6G8fF8QXE6RsN53mhOF2+Sjx+P0v6O0RA0Z5s8FCQul+b+mGecyhX87zyORub1xgtaE5Av36/W0OWM4LKpEWiZ7qLvOzomMq12arVaGf5lOyvM+wn7ITjKiX/NaHCZOH5iMUqlUtEUkqbExpjorY5d6/ULj2vQ66NhftFnRjrP83JSZEX6zbXzD8Djbpl5N5vQxnfCrrRHNOwCt0ssG6L8h+7yVDB9Pl2/lkpu/COwCJxuERnrpnwm3AEXzqo1yoi1mQAAAABJRU5ErkJggg==>

[image2]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAoAAAAaCAYAAACO5M0mAAABKElEQVR4XpWSPW7CQBCF7YpDYM94JaemiChoUiLRggQ0SDkABQ1FcgwqGurcIHVKLsAJOAcyb/bP42QxyrNWu/Pm29lZ21lmlXcmN4fgidKYcvP/VIvq440x3DmhXbqAmM/MfAE4xXzF+NGMFTGdkLjJOlRA3GDsFZY2iegovrscVBTF0ILEMw3C24ofjaqq3sUoy3KsW4e/Fh+FXqwN4wPVGlxiFClkAC18S+4kLD7/gtaf4000KLSKhuxE868BkgsgXoofC2Dx5o+YBEgEcON7HFqjrusBM0nFpUMs7lqyt1bfxlc8aJOIvx2ohMv43SIH+83zlvJFYH5h3PA5ZW5MZXYtFJT8nbyZzIlCIu9h0tL4060P2ujEMfhNJaVoeR6rL9dRnt0BikFBUP+V33wAAAAASUVORK5CYII=>

[image3]: <data:image/png;base64,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>

[image4]: <data:image/png;base64,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>

[image5]: <data:image/png;base64,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>

[image6]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAFcAAAAZCAYAAABEmrJwAAAEaklEQVR4Xu1YW4hNURg+IyKXN2M0l73OmRkm5QETJYriQYkHnrxIEnnghQcPrk88UIhHeXCJeJDy4JLCC9JkUO5JLuNSLoPUmGZ8/97/2udf/15nn3HM5Qzz1d/Z//df1r/+vfZaq5PJDOF/QIUmvJREEXP5YNAUOoS/RV+/6hLzlxgWoqmpaZwx5jmkG9JC2dLywacdslzztbW1jUEQ3OI8V7R9IIA6DkLuax4Yns1m73Gt71F3vXYgwLYa0mZM0An/PdqeCgSsogGam5tHkI5BdkD/qv0s4L+OC3KaC34e8UKfJvWBQHV19WiulZubXzLgfkmC5gxZEztE3BnIW6Efh3yKw9JWYGNjYyUNjkaMshwXU7ApsH1kH6e5nGe94jogNyXXd0jOFGN3ca3OykWd58FNkRxBz5v14ZpD/CLJecEDOwnpbUtdAr5f+Jfi4uY2NDRMII5+897hJC7p/BayFYExm4WaQC6Xq9Kcp5cOMO7eurq6mVyr01zi8IWekhxQIWtF7ft9tXO+Z5qPIIqSA+N3Nu29eWseFIJiVtIWQjrHxc2FbbuvEPBHE7ynKfCbC78HmifU19dPhu2N5tOAL3IkYh7Rs5yjhX3pRjQJzy2QbUKnry4xJ45L8M684DCFHU9AWquqqsbg95A3MBP6t4tnipPNPeeLA3fYx2vwy1uAST+JiUy4YpsQ35b37BlMtJ/aZ6o1caAx322C8Pcxxt7ps0sujXeAZCt8jibap34q7n3G3fwpLm4ucl3XeZg/QDxuETXa5gMavBD+T+mZG/susniWewKRD2K2Ytz5luVaE82Fzyi2Wfkk7ZaXXBrvAA6L2TE+DZm/KoPJD5PeqHwoLm4unk/6BjT5LyE8FHrSorDBgXmKuA/a1gMMQ9xLSXCtes/NcV0VaHKWfUj01+mbk5d3gJVh2PGY5LPRSdpNhwHpRq1i5iiu6J4L7oiPTwP8Z0DeQV5oWzEg5oeHo1p1c7txcI+XHOZwjXi6QbFPp692zufw3kVDTmjm6UiLP6sLxNNBwvoNJXd4gIekUxgKm0Pcn9wWfDBRY8M9Fi9/lhEHjncCCp5aSajWH/RMddrrJ/nrnMSj5i38fNlXO+fr0nwC7OhcK6Df9SW14NOb4hL3XMgyxX03ai8rBPhNN2qLogZnc3zIlQiuK7FypW5BfE1NTS09o8kTfX6cb5PmXeC14dOfqhOQjsS78oz7fu0qxe9axV8E3ymo8N5Ie5rgvOA6nL3Sgu+q4bUqHXodRuBmvFZcO31VksM4S8F/kxz0rkDchw2fU9InFQjewAWE/y1A3619LGD/DHkDeUUFk+7YA9Nqok/wLOdaKO2FAN99mpPAbWOS5oqB63hrTGBr/YB6llg7mvuEaoQ8pl/ot2W8BWwd7BtuMZWVlWO1T5/Av1bKAb1VWW/lGcIQYhRZVHlzIcfCf6sW4vsE/TpYuaDsJl1iQSWGDSgGY829hl6dfJFkRcz9iLKppPCeOwjxD02lCH4DI2eBSqGiyesAAAAASUVORK5CYII=>

[image7]: <data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAGEAAAAZCAYAAAAhd0APAAAFDUlEQVR4Xu1YS4gcVRTtnqjxg4oijPTMVE33jIxmhCADguBCUdSFuBLiDxWFiJ9EBXETF4IKIoqgRhd+Fm4EI2oEjYquXAiGgAiGBEFMYgbiZ2YSP4OJyXhu1b3dt069rq5u05MmyYFLv3fueffdd9+rX1cqA48qEyfRDU6WzzCwlRjYxEqgXe7t+EHDMuS5DFMUoyCBpqtAU4nj+LHx8fH7mU9RrcD3OTRLsHnYPawQ1Ov1Kfj+UN02UCtYI4DvBdhB2O9RFF3J/n4Dc56nOW7Auu7G7234vQW2RmxycnKlaeG7F/aX6DHuUx/HA/43VLO70WhERbXOAIPe0WJIQmIPsEYgvpmZmVOljSRvVe0vfldlYnBfW39qaups0YE/t6VKYu2HPe36i4j5rNcUofTiDIEBKNSdbs05g+QU0aG9DbndpcNWmN9vkoQHdwQH8DrjRINxN1i/NHSC3CaA24KkPyTuY9GPjY3d5LglXi+4h2A7rY841+gimxgdHT2fuf5AskszxHybpLgo3GrMfxHWMYHcGuA/kEOmA4bQPySHyyIMDw+fpXU6aBzaT3H+iHUjc6WgwUOb8K/4EPhm49CeVv0+p5P+ZdZX3cPgfrK+JB9KTuPfwTxhSG4joZNtgH8tcyFgvkXmJiYmxsDvsL6cZF1TJl/mtL/da4yXmMwXQoPlNgEnZQT8255DglepXu77CSw52Cuew6V7DmkOJR1XTOER87sWEwZ0f+L0DjOvt4S9yHWSfWUhOQS4N2F14pJ1Uv8Lr3H8S8wXQgflNiEEnLjPRI/L+FLjpDiWoNphudT9OOUPeE4qGEcJ3zydBYddYvwtB4O4WWzixZ7LoChgJRn/Lexq5hm1Wu1MXcMe46SPuT/yOuNhW5gvRJQOepD5AOwBtZUd4K5QX2JIbjP5hZ+Ttq+L6R1ViNhtBNqzsEtY00SHDajoephsoRVA5iWtPDck9/d5IuV/yJCdIINwwtcxz4BuMXa3oRYfPQF+l7RR/Ps0CbG3mpr0xC+0Rimf6o4wX4Q4Lcisvxp7AWJsjd1zqx30+SYbkHntFo5fXIyHfcV8FnRCNNj6LJsFNN+jwO82iVaM4GnSQiW83rclsX8SJz0T4i5PDfRzsAX/9tILdO7nmPdAXWbiNodExsvtOctWLe7rWb4DZBAK/AjzBvjfgz1DXHJvxO9GazMkrm/7vuexkNeYbwfo5+wZgPZCbWRklDUJOtyKcBXVdN1t3+nxHLggprcpv1ZdU/DtCHHXMF8IHfQo8wIU6PFx+prWB/FGacs4udV4v4E24dXAJlSFs4/BTvAb4LgFfliXAdb1osyNtaxmn26gXOHz5OE1bec1Id7lzHWE7vYS7Hn2RfqBFTIU43rTSb/uvhqV+4RPuOjATVsfMb4Bd4APLfcF0P0mH1bMC+CbR6wLmS8CxuzQfBrsE/B6nSW3JsnRPjYx9+lunPx1k3tuBgHhJtivsJ9he/R3X5z9IrSJcwb3kOkkCXCH1fej/m4wvwEbFavvSyx+N373siaEVdOrThtpd9tRIIcnmSsC5n5ZckEhzwj4btc8c5Z5BlTlzTK6VnjMvzlO/1tr/n3TX4SOal/Q34my0cvMVUaTorzyBEOwMEGyLP7X4GOJo5d4GunoxesOx2renrC8yWZm63JqlnO/HcrqBgeUca8L6HVcKbjgfZ3nREP5YpZXdkIuUo4YIAxybt3j+FrNcY3/AIYopE4y7K0XAAAAAElFTkSuQmCC>