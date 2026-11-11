# Historical Documents 0.2.0

Minecraft Java Edition 1.20.1 için Türkçe ve İngilizce belge modu. Belediye ve devlet imzaları Historical Nations Core’un güncel yetkilerine bağlıdır. Oyuncu elindeki bir evrakın içeriğini bağımsız dünya arşivinden doğrulayabilir.

## Kurulum

Java 17 ve Minecraft Forge **47.4.28** ile hem sunucunun hem de oyuncuların `mods/` klasörüne şu üç dağıtım paketini yerleştirin.

| Mod | JAR |
| --- | --- |
| Historical Documents | `historical-documents-0.2.0.jar` |
| Historical Nations Core | `historical_nations-0.2.0.jar` |
| Open Parties and Claims | `open-parties-and-claims-forge-1.20.1-0.33.0.jar` |

## Oyun içinde

1. Bir kâğıt ve bir mürekkep kesesini üretim alanında birleştirin. Operatörler (yetki seviyesi 2) test için `/document give` kullanabilir.
2. Boş belgeyi elinize alıp sağ tıklayın. Başlığı, türü ve metni girin. Alıcı isteğe bağlıdır. Düzenleyen düğmesiyle kişisel belge, üyesi olduğunuz şehir veya devlet arasında geçiş yapabilirsiniz. İlk kayıt kurum kimliğini sabitler.
3. **Taslağı kaydet** ile belgeyi oluşturun. Taslağı elinde tutan oyuncu içeriği düzenleyebilir. Vatandaş bir taslağı başkana veya lidere verebilir. Kişisel belge taslak olarak paylaşılır.
4. Kaydedilmiş taslağı yeniden açın. İlgili belediye başkanı veya devlet lideri **İmzala ve mühürle** düğmesini görür. Metni değiştirirseniz önce kaydedip yeniden açın. Mühürden sonra belge yalnızca okunur.
5. Belgeyi doğrulamak için ana elinizde tutup `/document verify` yazın. `/document info` başlık, tür, kurum, durum ve UUID’yi, imzalıysa imzalayanı ve UTC tarihini gösterir. `/document help` erişebildiğiniz komutları listeler.

Başlık 80, tür 48, alıcı 80, metin 2.000 UTF-16 kod birimiyle sınırlıdır. Metin satır sonlarını destekler. İmza anındaki kurum adı ve imzalayanın kimliği kaydedildiğinden sonradan gerçekleşen yönetim değişiklikleri arşivi etkilemez.

## Arşiv, iptal ve ek imzalar

- `/document archive town Berlin 2` belediyenin ikinci sayfasını açar. Devlet için `/document archive nation "Birleşik Krallık"` kullanın. Sayfa başına beş kayıt, en yeni ilk imzadan başlayarak gösterilir. Bir kurumun düzenlediği ve sonradan imzaladığı belgeler listelenir. Aynı adı taşıyan kurumlar UUID ile seçilebilir.
- `/document archive info <belge UUID>` herkese açık künye, durum ve imza sayısını verir. Tam metni okumak için belgeyi elinizde açın.
- İlk düzenleyen kurumun güncel başkanı veya lideri belgeyi ana elinde tutarak `/document invite <çevrimiçi oyuncu>` yazabilir. Davet edilen yetkili aynı belgenin bir kopyasını ana eline alıp `/document cosign` yazar. Davet hem kişiye hem de davet anındaki kurum UUID’sine bağlıdır. Her belge en fazla üç bekleyen davet ve ilk imza dahil dört ayrı kurumun imzasını taşır.
- İlk düzenleyen kurumun güncel yetkilisi `/document revoke <gerekçe>` ile belgeyi kalıcı olarak iptal eder. Gerekçe 1–160 karakterdir. Eski kopyalar açıldığında imzalar, iptal gerekçesi ve UTC zamanı arşivden okunur.

## Dünya kaydı

0.1.0’dan yükseltirken sunucuyu kapatıp dünya klasörünü yedekleyin. Sunucuda ve istemcilerde Documents JAR’ını birlikte güncelleyin. Arşiv yüklendiğinde v1 kayıtları aynı UUID ve ilk mühürle etkin olarak alınır. Bir sonraki dünya kaydı v2 biçimini yazar. Okunamayan arşiv biçimi imzalamayı durdurur.

Overworld’ün `data/historical_documents.dat` dosyasında sürümlü bir `SavedData` arşivi bulunur. Bellekte UUID üzerinden sorgulanır. İmza sırasında düzenleyen kurumun türü ve UUID’si, adı, belgenin bütün alanları, oyuncunun UUID’si, adı ve sunucu zamanı saklanır. Doğrulama kayıtla eşleşme gerektirir. Eşleşen fiziksel kopyalar aynı UUID ve aynı işlemi gösterir. Eşyayı kaybetmek arşiv kaydını silmez. Yedekleme ve geri yükleme sırasında dünya klasörünü bir bütün olarak taşıyın.

## Kaynaktan derleme

JDK 17 ile `JAVA_HOME` ayarlayın. Gerçek Core v0.2.0 JAR’ını `libs/historical_nations-0.2.0.jar` konumuna koyun. Kardeş `../historicalnationscore/build/libs/` dizini de otomatik bulunur. Başka bir konum için `-PcoreJar="D:/örnek/historical_nations-0.2.0.jar"` kullanabilirsiniz.

```powershell
.\gradlew.bat build
```

Linux ve macOS’ta `./gradlew build` kullanın. Dağıtım dosyası `build/libs/historical-documents-0.2.0.jar` olur. Entegrasyon testleri ayrı bir JAR kullanır.

[Entegrasyon testleri](src/integration/README.md) · [Historical Nations Core API](https://github.com/ZeorezDev/historicalnationscore/blob/main/src/main/java/dev/zeorez/historicalnations/api/HistoricalNationsApi.java) · [Forge 1.20.1 SavedData](https://docs.minecraftforge.net/en/1.20.1/datastorage/saveddata/)
