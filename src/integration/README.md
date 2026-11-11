# Forge dedicated server testleri

JDK 17 ile proje kökünde `./gradlew build integrationJar` çalıştırın. Ayrı bir Forge **1.20.1-47.4.28** test kurulumunun `mods/` dizinine Documents dağıtım JAR’ını, `build/integration/historical-documents-tests-0.3.0.jar` test paketini, Core 0.2.0 ve OPAC 0.33.0 dağıtım JAR’larını koyun. Test paketi yalnızca geçici test dünyaları içindir. Test kurulumu için Minecraft EULA koşullarını tamamlayın.

## 0.3.0 baskı testi

Sunucunun `mods/` klasörüne Historical Linotype 1.2.0 dağıtım JAR’ını ekleyin. Aşağıdaki iki istemcili ağ testi ayarlarıyla yeni bir dünyada `-Ddocuments.test=printing` seçin. Her iki istemcinin Gradle komutuna `-PprintingTest` ekleyin. Test, gerçek makine menüsünden gönderilen çift tasdik isteğini, iki kitap türünü, normal sureti, makam ve kaynak değişimini, sahte kimlikleri, iptali ve malzeme tüketimini sınar. Sunucu kapandıktan sonra aynı dünyayı `-Ddocuments.test=printRestart` ile açın. Bu aşama sandıktaki tasdikli sureti, özgün belgeyi, imzaları ve iptal gerekçesini diskten doğrular.

Linotype olmadan yüklemeyi sınamak için ayrı bir kurulumda makine modunu çıkartıp yeni bir dünyada `setup` çalıştırın. Eski sürümlerin arşivlerini sınamak için v0.1.0 test dünyasının kopyasında `upgrade`, v0.2.0 iki istemcili ağ testi dünyasının kopyasında `networkRestart` kullanın.

### 0.3.0 için gözlenen sonuçlar

Forge 47.4.28 ve Java 17 ile iki bağlı istemcinin kullandığı kurulumda `PASS printing` ve sunucu yeniden başlatıldığında `PASS printRestart` alındı. Eski belediye sureti, baskıdan sonra başka bir belediyenin attığı imzayı gösterdi. Makam devri, kaynak değişimi, iptal ve yetkisiz baskı sırasında makinenin çıktı yuvası boş, kâğıt sayısı ile mürekkep ve yakıt bakiyeleri sabit kaldı. İlk iki istemcili denemelerde test istemcisinin isteğe bağlı Linotype yüklemesi ve çift tıklama isteğinin makineye ulaşması için bekleme süresi düzeltildi.

Linotype bulunmayan ayrı kurulumda `PASS setup`, v0.1.0 dünya kopyasında `PASS upgrade` ve v0.2.0 dünya kopyasında `PASS networkRestart` alındı. Linotype'ın kendi GameTest sunucusundaki dört zorunlu test de geçti. Yeniden başlatma testi düzenli sunucu kapanışıyla yapıldı. Elektrik kesintisi ve büyük arşivlerde baskı performansı sınanmadı.

## Yetkiler, doğrulama ve disk kaydı

Aşağıdakileri dedicated server dizininde sırasıyla çalıştırın. İlk süreç için yeni bir dünya adı seçin ve ikinci süreçte aynı adı kullanın. Linux üzerinde `unix_args.txt` kullanılır.

```powershell
java -Xmx2G "-Ddocuments.test=setup" "@libraries/net/minecraftforge/forge/1.20.1-47.4.28/win_args.txt" --world documents-test nogui
java -Xmx2G "-Ddocuments.test=restart" "@libraries/net/minecraftforge/forge/1.20.1-47.4.28/win_args.txt" --world documents-test nogui
```

Test oyuncuları normal Core komutlarıyla şehir kurar, vatandaş davet eder, başkanlığı devreder ve devleti dağıtır. Belge istekleri üretim modunun sunucu işleyicilerinden geçer. İki imzalı eşya bir sandıkta saklanır. İkinci süreç sandığı ve arşivi diskten yükler. Başarı işaretleri `DOCUMENTS PASS setup` ve `DOCUMENTS PASS restart` satırlarıdır.

| Senaryo | Kontrol |
| --- | --- |
| 1. Belediye başkanı | Vatandaşın Berlin taslağına doğru kurum ve oyuncu UUID’siyle imza atılır. |
| 2. Vatandaş | Taslak hazırlayabilir. Mühürleme isteği reddedilir. |
| 3. Devlet lideri | Almanya adına mühürleme ve doğrulama başarılıdır. |
| 4. Yanlış devlet | Başka bir devletin lideri aynı Almanya taslağını imzalayamaz. |
| 5. Başkan değişimi | Devir öncesinde açılmış ekrandan gelen yeni imza reddedilir. Eski imza geçerlidir. |
| 6. Devletin dağılması | Kurum dağıldıktan sonra imzalı belge doğrulanır. |
| 7. Kalıcılık | Yeni Java sürecinde belediye ve devlet belgeleri doğrulanır. Nether aynı arşive erişir. |
| 8. Sahtecilik | İçerik, kurum, imzalayan, zaman, durum ve UUID uyuşmazlıkları reddedilir. |
| 9. Multiplayer | Farklı oyuncu belgeyi doğrular. Aşağıdaki ağ testi iki bağlı oyun istemcisi kullanır. |
| 10. Eksik bağımlılık | Aşağıdaki ayrı yükleme denemesi Core eksikliğini bildirir. |

Ayrıca kişisel taslak düzenleme, 2.000 birimi aşan içeriğin reddi, eldeki eşyanın değişmesi, yinelenen imza, imzalı belgeyi düzenleme ve resmî kayda dönüşmüş taslağın eski kopyası sınanır.

## İki gerçek istemci

Test sunucusunu `server-ip=127.0.0.1`, `server-port=25761`, `online-mode=false` ve `enforce-secure-profile=false` ile çalıştırın. Bu ayarlar yerel test profilleri içindir. Yeni bir dünya adıyla yukarıdaki sunucu komutunda `-Ddocuments.test=network` seçin. Test istemcileri için Core JAR’ı özgün dosya adını taşımalıdır.

Proje kökünde iki terminalden çalıştırın.

```powershell
.\gradlew.bat runClient -PdocumentTestClient=DocWriter --project-cache-dir run-DocWriter/gradle-cache
.\gradlew.bat runClient -PdocumentTestClient=DocReader --project-cache-dir run-DocReader/gradle-cache
```

İlk açılışta erişilebilirlik ekranı gösterilirse ana menüye geçin. Her istemci ana menüden yerel test sunucusuna bağlanır. Yazar boş belgeyi kullanır, düzenleyicide kurum seçer, iki satırlı metni kaydeder ve yeniden açarak mühürler. Yazar ve alıcı ayrı devletlerin liderleridir. Sunucu alıcının davetsiz imza isteğini reddeder, daveti kaydeder ve başkanlığı geçici olarak devrederek eski liderin isteğini reddeder. Alıcı yeniden lider olduğunda özgün belge kopyasını kullanır. Alıcı salt okunur ekranı açar ve `/document cosign` ile imzalar. Ardından `/document verify` yanıtını alır. Başarıda sunucu `DOCUMENTS PASS network` yazar ve kapanır. Test istemcileri kendi `screenshots/` dizinlerine taslak ve imzalı ekranın görüntüsünü yazar.

## Zorunlu bağımlılık

Ayrı bir Forge test kurulumunda yalnız Documents dağıtım JAR’ı ile başlatın. Başarı ölçütü Forge’un yüklemeyi durdurması ve bağımlılık hatasında `historical_documents` için `historical_nations` sürüm 0.2.0 gerektiğini ve mevcut sürümün `[MISSING]` olduğunu bildirmesidir.

## 0.2.0 geçiş ve diplomatik imza testleri

Eski dağıtım ve test JAR’larıyla `setup` çalıştırılmış v0.1.0 dünyasının kapalı bir kopyasını alın. Kopyayı v0.2.0 sunucusunda önce `-Ddocuments.test=upgrade`, ardından aynı dünya adıyla ayrı bir süreçte `-Ddocuments.test=upgradeRestart` kullanarak açın. Her aşamada günlükte ilgili `DOCUMENTS PASS` satırını arayın. Bu senaryo eski sandıktaki özgün UUID, içerik ve ilk imzayı arşivle karşılaştırır. Berlin’in yeni başkanı iptal eder, eski başkan ve yabancı başkan reddedilir. Eski kopyanın iptal gerekçesi yeniden açılışta okunur. Değiştirilmiş ek mühür reddedilir. Bozuk bir arşiv örneğinin yeniden kayıtta korunduğu ayrıca sınanır.

Yeni bir dünyada yukarıdaki iki istemcili `network` testini çalıştırın. İlk imza Germany, ikinci imza France adına kaydedilir. İki oyuncunun işlemleri bittikten sonra aynı dünyayı `-Ddocuments.test=networkRestart` ile yeniden açın. Sandıktaki özgün kopyanın geçerli olduğu ve arşivde tam iki imzasının bulunduğu denetlenir.

Forge 47.4.28 ve Java 17 ile dört ayrı sunucu çalıştırmasında `PASS upgrade`, `PASS upgradeRestart`, `PASS network` ve `PASS networkRestart` gözlendi. İlk geçiş denemesinde test JAR’ının `SavedData.save` yöntemine çağrısı dağıtım adlarına çevrilmemişti. Test, Forge sınıfı üzerinden çağıracak şekilde düzeltildi ve özgün v0.1.0 yedeğinin yeni kopyası üzerinde tekrar çalıştırıldı.

## 0.1.0 için gözlenen sonuçlar

Java 17.0.12 ve Forge 47.4.28 ile dağıtım JAR’ı dedicated server üzerinde çalıştırıldı. İki ayrı sunucu sürecinden `DOCUMENTS PASS setup` ve `DOCUMENTS PASS restart` alındı. Core çıkarıldığında Forge yüklemeyi durdurdu ve `historical_nations` için beklenen aralığı `[0.2.0,0.2.0]`, mevcut sürümü `[MISSING]` olarak bildirdi.

İlk iki istemcili deneme Forge açılış ekranında kaldı. Test paketinin kaynak meta verisi eklendikten sonra Türkçe DocWriter ve İngilizce DocReader aynı sunucuya bağlandı. Alıcı, Berlin Belediyesi tarafından düzenlenmiş iki satırlı belgeyi açtı ve doğrulama yanıtını aldı. Sunucu `DOCUMENTS PASS network` yazdı.
