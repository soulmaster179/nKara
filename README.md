# nKara

nKara là ứng dụng karaoke mã nguồn mở dành cho Android. Một thiết bị đóng vai trò **Host** để phát video, các thiết bị khác trong cùng mạng Wi-Fi/LAN có thể làm **Controller** để tìm bài, thêm bài hát và sắp xếp hàng chờ.

## Tính năng

- Tìm kiếm video karaoke trên YouTube ngay trong ứng dụng.
- Phát video bằng Media3/ExoPlayer, không cần mở ứng dụng YouTube.
- Điều khiển Host từ thiết bị Android khác trong cùng mạng LAN.
- Thêm bài, ưu tiên bài hát và quản lý hàng chờ.
- Hiển thị mã QR để kết nối Controller nhanh hơn.
- Lưu lịch sử bài hát cục bộ bằng Room.
- Giao diện Jetpack Compose, hỗ trợ điện thoại và màn hình Android TV.

## Yêu cầu

- Android 7.0 (API 24) trở lên.
- Các thiết bị Host và Controller phải kết nối cùng mạng Wi-Fi/LAN.
- Kết nối Internet để tìm kiếm và phát video.

## Cách sử dụng

1. Cài và mở nKara trên thiết bị dùng để phát karaoke.
2. Chọn chế độ **Host**.
3. Trên thiết bị điều khiển, mở nKara và chọn **Controller**.
4. Quét mã QR hoặc kết nối tới Host trong cùng mạng.
5. Tìm bài hát, thêm vào hàng chờ và bắt đầu hát.

> Mạng khách (guest Wi-Fi), AP isolation hoặc tường lửa có thể chặn các thiết bị nhìn thấy nhau. Nếu không kết nối được, hãy kiểm tra các thiết bị đang ở cùng mạng và tắt client isolation trên router.

## Cài đặt

Tải APK mới nhất tại trang [Releases](https://github.com/soulmaster179/nKara/releases), sau đó cho phép cài ứng dụng từ nguồn không xác định nếu Android yêu cầu.

## Build từ source

Yêu cầu Android Studio có JDK 17 và Android SDK phù hợp.

```powershell
git clone https://github.com/soulmaster179/nKara.git
cd nKara
.\gradlew.bat assembleDebug
```

APK debug được tạo tại:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Trên macOS/Linux, dùng `./gradlew assembleDebug`.

## Công nghệ sử dụng

- Kotlin và Jetpack Compose
- Media3 / ExoPlayer
- NewPipe Extractor
- Hilt
- Room
- OkHttp
- Coil
- ZXing

Danh sách thư viện và thông báo bản quyền của bên thứ ba nằm trong [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Ủng hộ dự án

Nếu nKara hữu ích với bạn, hãy tặng repository một ⭐ hoặc [ủng hộ tác giả qua MoMo](https://me.momo.vn/1MIKuysyUOU9UeUmUVCj). Sự ủng hộ của bạn giúp dự án có thêm thời gian để sửa lỗi và phát triển tính năng mới.

## Đóng góp

Issue và pull request đều được chào đón. Khi báo lỗi, vui lòng cung cấp phiên bản Android, loại thiết bị, các bước tái hiện và log liên quan nếu có.

## Lưu ý

nKara không liên kết, tài trợ hay được chứng thực bởi YouTube hoặc Google. Nội dung được lấy trực tiếp từ dịch vụ bên thứ ba và có thể ngừng hoạt động khi dịch vụ đó thay đổi. Người dùng có trách nhiệm tuân thủ điều khoản sử dụng và luật bản quyền tại nơi mình sinh sống.

nKara được phát triển hướng tới mục đích **cá nhân, học tập và giải trí**. Việc sử dụng ứng dụng để kinh doanh, phân phối lại nội dung, trình chiếu công cộng có thu phí hoặc thực hiện hoạt động thương mại không đồng nghĩa với việc người dùng có quyền sử dụng nội dung; người dùng phải tự có đầy đủ quyền và sự cho phép cần thiết từ chủ sở hữu nội dung.

Người dùng tự chịu trách nhiệm đối với nội dung mình truy cập, phát hoặc chia sẻ thông qua ứng dụng, bao gồm việc bảo đảm quyền sử dụng và tuân thủ pháp luật, điều khoản dịch vụ cũng như quy định về quyền tác giả tại khu vực của mình. Tác giả nKara không lưu trữ nội dung video và không chịu trách nhiệm đối với hành vi sử dụng sai mục đích, hành vi vi phạm bản quyền, điều khoản dịch vụ hoặc pháp luật do người dùng thực hiện.

Phần mềm được cung cấp theo hiện trạng, không kèm bảo đảm về tính liên tục, độ chính xác hoặc sự phù hợp cho một mục đích cụ thể. Tuyên bố này không loại trừ hoặc giới hạn những trách nhiệm không thể được miễn trừ theo pháp luật hiện hành.

## Giấy phép

nKara là phần mềm tự do được phát hành theo [GNU General Public License v3.0 hoặc phiên bản mới hơn](LICENSE) (`GPL-3.0-or-later`). Bạn được phép sử dụng, nghiên cứu, chỉnh sửa và phân phối lại phần mềm, kể cả cho mục đích thương mại, với điều kiện tuân thủ GPL.

Khi phân phối APK hoặc một phiên bản đã chỉnh sửa, bạn phải:

- Cung cấp mã nguồn tương ứng đầy đủ dưới GPL-3.0-or-later.
- Giữ lại thông báo bản quyền và giấy phép.
- Ghi rõ những thay đổi đã thực hiện.
- Cung cấp cho người nhận bản sao giấy phép GPL.

Giấy phép phần mềm không cấp bất kỳ quyền nào đối với video, âm nhạc, nhãn hiệu hoặc nội dung của bên thứ ba được truy cập thông qua ứng dụng. Xem thêm [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
