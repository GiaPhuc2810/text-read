# text-read (Java 21)

Cong cu doc van ban tren man hinh:
- OCR: Tess4J (Tesseract)
- TTS: Google Translate TTS (online, ho tro doc tieng Viet dung am)
- Auto scroll: `java.awt.Robot`
- UI: JavaFX (always-on-top controller)
- SQL Server: luu/cai dat session

## Yeu cau
- JDK 21
- Maven 3.9+
- SQL Server local co DB `text-read`
- Thu muc `tessdata` (co file ngon ngu, vi du `vie.traineddata`)

## Cau hinh nhanh
1. Chinh thong tin SQL Server trong:
   - `src/main/java/com/textread/TextReadApplication.java`
   - Mac dinh dang de san:
     - `jdbc:sqlserver://LAPTOP-ECBL64KP;instanceName=SQLEXPRESS;databaseName=text-read;encrypt=false;trustServerCertificate=true`
     - User/Password mac dinh: `phuc` / `123`
   - Hoac set bien moi truong:
     - `TEXT_READ_DB_URL`
     - `TEXT_READ_DB_USER`
     - `TEXT_READ_DB_PASSWORD`
2. Dam bao duong dan `tessdata` dung:
   - Mac dinh: `./tessdata`
   - Bat buoc co: `./tessdata/vie.traineddata`
   - Link tai: `https://github.com/tesseract-ocr/tessdata/blob/main/vie.traineddata`
3. Tao DB neu chua co:
```sql
CREATE DATABASE [text-read];
```

## Chay app
```bash
mvn clean javafx:run
```

## Chuc nang hien co (MVP)
- Start/Stop pipeline OCR -> loc ad -> TTS -> auto-scroll
- Dieu chinh toc do doc
- Dieu chinh toc do cuon
- Dieu chinh am luong
- Mute/Unmute
- Test Voice
- Advanced settings:
  - Chon vung quet bang keo chuot (snip-style)
  - Tessdata path + language
  - TTS language
  - Ad filter bang choice box
- Luu setting xuong SQL Server

## Ghi chu
- TTS can internet de goi Google Translate TTS.
- OCR chat luong phu thuoc font, do tuong phan va do dung vung quet.

## Neu app "khong hoat dong"
- Kiem tra da co `./tessdata/vie.traineddata`.
- Trong Advanced Settings: `OCR Language = vie`.
- Thu chon lai `Region` vao dung khu vuc co text.
