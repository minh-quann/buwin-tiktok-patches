![Buwin - Lấy lại quyền kiểm soát Bảng tin TikTok với các tính năng lọc nội dung, cử chỉ, phát lại, tải xuống và bảo vệ quyền riêng tư](assets/readme-hero.png)

<p align="center">
  <a href="CHANGELOG.md"><img alt="Phiên bản" src="https://img.shields.io/badge/phiên%20bản-0.75.0-6f42c1.svg" /></a>
  <a href="LICENSE"><img alt="Giấy phép" src="https://img.shields.io/badge/giấy%20phép-GPLv3-blue.svg" /></a>
  <a href="https://www.android.com/"><img alt="Hệ điều hành" src="https://img.shields.io/badge/nền%20tảng-Android%2010%20--%2015%2B%20%7C%20MagicOS%20%7C%20HyperOS%20%7C%20One%20UI-3ddc84.svg" /></a>
  <a href="https://github.com/MorpheApp/morphe-manager"><img alt="Morphe" src="https://img.shields.io/badge/tương%20thích-Morphe-00b894.svg" /></a>
  <a href="https://github.com/minh-quann/buwin-tiktok-patches/issues"><img alt="Thảo luận & Báo lỗi" src="https://img.shields.io/github/discussions/minh-quann/buwin-tiktok-patches?color=0969da" /></a>
  <a href="https://www.apkmirror.com/apk/tiktok-pte-ltd/tik-tok-including-musical-ly/"><img alt="TikTok 47.4.3" src="https://img.shields.io/badge/TikTok-47.4.3-ff0050.svg" /></a>
</p>

# Buwin TikTok Patches

**Buwin** là bộ bản vá mã nguồn mở cao cấp và toàn diện dành cho ứng dụng TikTok trên Android, được xây dựng và tối ưu hoá cho trình quản lý [Morphe Manager](https://github.com/MorpheApp/morphe-manager). Với Buwin, bạn hoàn toàn làm chủ bảng tin của mình: loại bỏ 100% quảng cáo và rác thị giác, tải đa phương tiện chất lượng gốc không logo hình mờ, tùy biến cử chỉ chống bấm nhầm, đồng bộ giao diện tràn viền mượt mà chuẩn phong cách iOS và bảo vệ tấm nền màn hình OLED tối đa.

Mọi tính năng được tích hợp trực tiếp vào menu quản trị **Cài đặt Buwin** ngay trong ứng dụng TikTok. Hầu hết các công tắc mặc định ở trạng thái tắt, tôn trọng tuyệt đối giao diện gốc cho đến khi bạn chủ động kích hoạt những tính năng mong muốn.

[Thêm Buwin vào Morphe](https://morphe.software/add-source?github=minh-quann%2Fbuwin-tiktok-patches) | [Tải gói bản vá mới nhất (.mpp)](https://github.com/minh-quann/buwin-tiktok-patches/releases/latest) | [Xem danh mục 135 bản vá](#-danh-mục-135-bản-vá-chi-tiết)

---

## 💎 Những cải tiến kỹ thuật & Giao diện độc quyền của Buwin

Phiên bản Buwin mang đến những nâng cấp phần cứng và giao diện chuyên sâu, đặc biệt tinh chỉnh cho các dòng điện thoại màn hình dài cao cấp (tỷ lệ $19.5:9$, $20:9$, độ phân giải $1.5\text{K} / 2\text{K}$, tần số quét cao $120\text{Hz}$):

### 1. 📱 Tràn viền Status Bar đỉnh cao (See-Through Status Bar & iOS Style Blend)
- **Tự động thích ứng tỷ lệ (Adaptive Viewport Scaling):** Tính toán khung hình video theo viewport cố định từ chính bộ chính sách thích ứng gốc (adaption policy) trước frame hình đầu tiên, triệt tiêu hoàn toàn hiện tượng nhấp nháy hoặc giật khung hình khi cuộn lướt.
- **4 chế độ phối hợp Status Bar linh hoạt:**
  - `Scale to fill (Balanced / iOS style)` *(Mặc định khuyên dùng)*: Phóng to tối thiểu và căn giữa video để tràn kín thanh trạng thái và cụm camera nốt ruồi mà không cắt phụ đề hay nội dung quan trọng, mang lại cảm giác mượt mà đồng nhất như trên iOS.
  - `Shift video up (Zero crop)`: Dịch chuyển video lên sát mép trên với tỷ lệ 1:1 nguyên bản, không cắt xén bất kỳ pixel nào theo chiều ngang.
  - `Shift video up (Split margin)`: Dịch chuyển video lên một nửa khoảng cách để cân bằng đều dải viền đen giữa cạnh trên và cạnh dưới.
  - `Ambient gradient (YouTube style)`: Đẩy video lên dưới thanh trạng thái kèm hiệu ứng đổ bóng mờ gradient chuyển tiếp từ đen sang trong suốt, giữ cho đồng hồ và icon hệ thống luôn rõ nét.
- **Hỗ trợ Cutout & Khử mép sub-pixel:** Tự động kích hoạt cờ `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES` trên Android 9+ và tích hợp tỷ lệ bù mép sub-pixel (`1.008f`), xóa sổ triệt để các vệt đen siêu mảnh $1\text{px} - 4\text{px}$ ở các cạnh màn hình.
- **Tôn trọng video ngang & LIVE preview:** Tự động nhận diện hướng luồng video gốc — chỉ fill video dọc, giữ nguyên video ngang và nút toàn màn hình; đối với preview phòng LIVE, tự động crop riêng SurfaceView và nâng cụm nhãn người dùng/thương mại lên vị trí tối ưu.

### 2. 🛡️ Bảo vệ màn hình OLED thông minh (Intelligent OLED Burn-In Guard)
Dành riêng cho màn hình OLED/AMOLED, ngăn chặn vĩnh viễn hiện tượng lưu ảnh (burn-in) do các icon và nút điều khiển tĩnh nằm ở cùng một vị trí trong nhiều giờ:
- **Tự động làm mờ khi nghỉ (Idle Dimming):** Khi không có thao tác chạm, toàn bộ các nút tương tác, thanh tua video, nhãn âm thanh, phụ đề và tab điều hướng sẽ tự động hạ độ sáng mờ nhẹ để bảo vệ các điểm ảnh phát quang. Chạm nhẹ ngón tay vào bất kỳ đâu trên màn hình sẽ đưa độ sáng trở lại bình thường tức thì.
- **Thanh trượt tùy chỉnh thời gian chờ (Idle delay):** Cho phép tự do thiết lập thời gian không chạm trước khi làm mờ từ **1 đến 10 giây** (mặc định 5s).
- **Thanh trượt tùy chỉnh độ mờ (Idle opacity):** Điều chỉnh độ sáng khi nghỉ linh hoạt từ **0%** *(biến mất hoàn toàn)* đến **100%** *(mặc định 25%)*.
- **Dịch chuyển điểm ảnh tiền cảnh thông minh (Smart Foreground Pixel Shift):** Khi bật chế độ *Dim when idle and shift*, các nút điều khiển tiền cảnh (foreground controls) sẽ tự động dịch chuyển $2\text{dp}$ sau mỗi 2 phút theo chu kỳ 9 vị trí quanh vị trí gốc. Cơ chế nhận diện và khử trùng lặp cha/con thông minh, tuyệt đối không dịch chuyển khung hình Activity, video surface hay nền bottom bar, xóa sổ hoàn toàn hiện tượng lệch mép làm lộ viền đen cửa sổ.
- **Đồng bộ cụm nút tự động cuộn (Auto-scroll Controls):** Bao gồm cả biểu tượng và nhãn nổi của tính năng tự động cuộn vào danh sách làm mờ đồng bộ khi nghỉ.

### 3. 💊 Đồng bộ giao diện đáy Native & Thanh điều hướng nổi (Bottom Bar & Floating Dock)
- **Nâng đồng bộ các widget Native:** Khi tăng khoảng trống hoặc sử dụng thanh nổi, toàn bộ các thành phần native của TikTok (phần mô tả caption, cột nút Thích/Bình luận/Lưu, đĩa nhạc quay, thanh tua video) được nâng đồng bộ trong pha measure/layout gốc, không bị đè che khuất hay lệch vị trí.
- **Thanh điều hướng nổi dạng viên thuốc (Floating Capsule Dock):**
  - Mở rộng chiều cao lên **$64\text{dp}$**, khoảng đệm trên/dưới **$16\text{dp}$** và lề hai bên **$16\text{dp}$**.
  - Bo cong mút hình viên thuốc mềm mại bán kính **$200\text{dp}$**.
  - Chất liệu kính mờ tối sang trọng (Dark Frosted Glass `#50000000`, độ đục 31%) đi kèm viền phản quang mỏng nhẹ (`#26FFFFFF`), nổi lơ lửng phía trên thanh cử chỉ hệ thống **$16\text{dp}$**.
- **Triệt tiêu hoàn toàn giật lag cuộn 120Hz (Khử Layout Thrashing):** Khóa cố định chiều cao spacer đáy và tối ưu hóa thời điểm cập nhật kích thước, ngăn chặn vòng lặp tính toán lại bố cục giữa thanh nổi và cell video, đem lại thao tác vuốt cuộn êm ái tuyệt đối ở tần số quét cao $120\text{Hz}$.
- **Khoảng chừa an toàn đồng bộ:** Áp dụng khoảng đệm đáy chuẩn xác cho khung chat tin nhắn, thanh nhập bình luận và bottom sheet; tự động bỏ qua phần đệm thêm khi bàn phím ảo (IME) đang mở.

### 4. 🔘 Nút gạt chuyển đổi hiện đại (Modern Fluid Switch Toggles)
- Thiết kế lại toàn bộ nút gạt trong menu cài đặt với kích thước rãnh chuẩn $51 \times 31\text{dp}$, bo tròn góc $16\text{dp}$.
- Phối màu trực quan và trang nhã: Màu kích hoạt xanh ngọc lục bảo (`#34C759`), nền tắt xám tối thanh lịch (`#39393D` ở Chế độ tối) và xám nhạt (`#E9E9EA` ở Chế độ sáng).
- Cần trượt tròn màu trắng tinh khôi (`#FFFFFF`, đường kính $27\text{dp}$) tích hợp bóng đổ ambient nhẹ $1\text{dp}$ (`#1A000000`), mang lại phản hồi xúc giác thị giác sinh động và cao cấp.

### 5. 🏷️ Nhận diện thương hiệu Buwin đồng bộ 100%
- Toàn bộ bytecode Smali, giao diện menu, trang thông tin Giới thiệu, bảng điều khiển chẩn đoán và thông báo hệ thống đều mang nhận diện thương hiệu **Buwin**.
- Liên kết mã nguồn và diễn đàn hỗ trợ trỏ thẳng về repository chính thức: [minh-quann/buwin-tiktok-patches](https://github.com/minh-quann/buwin-tiktok-patches).

---

## 🌟 Những tính năng vượt trội mà Buwin mang lại

- **Bảng tin trong lành & Không quảng cáo (Clean Feed):** Loại bỏ hoàn toàn quảng cáo tài trợ trên feed, biểu ngữ nổi, giỏ hàng TikTok Shop, phòng LIVE, bài đăng hình ảnh, tin 24h (Stories) hoặc lọc bất kỳ video nào theo từ khóa cá nhân. Video do chính bạn đăng sẽ không bao giờ bị ẩn. Cho phép đặt tab mở app mặc định (Đang theo dõi, Bạn bè, Hộp thư, Hồ sơ).
- **Cử chỉ thông minh chống chạm nhầm:** Yêu cầu xác nhận chạm lần hai trước khi Theo dõi, Thích hoặc Đăng lại nhanh. Tùy biến hoặc vô hiệu hóa cử chỉ chạm hai lần (Double-tap), nhấn giữ (Long-press) và vuốt sang trái (Swipe-left). Giữ nguyên vị trí video hiện tại khi chạm nút Trang chủ.
- **Trải nghiệm phát lại đỉnh cao (Advanced Playback):** Tự do lựa chọn tốc độ phát mặc định (lên đến $3\times$), ghi nhớ mốc thời gian đang xem dở dang khi lướt quay lại, tiếp tục phát âm thanh khi tắt màn hình hoặc chuyển sang ứng dụng khác (Background Playback), chế độ thu nhỏ góc màn hình (Picture-in-Picture), tự động phát tiếp video tiếp theo (Auto-advance).
- **Tải xuống đa phương tiện chất lượng gốc (Lossless Downloads):** Lưu video không logo hình mờ Watermark, tải ảnh gốc độ phân giải đầy đủ, tách riêng file âm thanh nền MP3/AAC, tải phụ đề SRT rời và gộp bài đăng nhiều ảnh thành video MP4 hoàn chỉnh. Hỗ trợ tải ngay cả trên các video bị chủ sở hữu khóa nút lưu.
- **Bình luận & Hộp thư tiện nghi:** Tự động dịch bình luận sang tiếng Việt bằng bộ dịch gốc, tìm kiếm bình luận theo từ khóa, lọc ẩn bình luận spam, ẩn các mục rác trong Hộp thư đến và duy trì chuỗi tia sét (Streak) tự động với bạn bè.
- **Quyền riêng tư & Chế độ ẩn danh (Ghost Mode):** Xem Story và xem Hồ sơ cá nhân của người khác mà không lưu lại dấu vết, ẩn chỉ báo đang soạn tin nhắn và trạng thái online, không lưu video vào Lịch sử xem, chặn đứng toàn bộ tiến trình thu thập dữ liệu hành vi gửi về máy chủ, bảo vệ khay nhớ tạm (Clipboard), ẩn mã quảng cáo (GAID) và khóa ứng dụng bằng vân tay/khuôn mặt/PIN.
- **Cân bằng thời gian sử dụng:** Đặt hạn mức xem video mỗi ngày theo số lượng video hoặc thời lượng phút, hỗ trợ khóa bảng tin khi hết hạn mức để bạn chỉ sử dụng TikTok cho mục đích nhắn tin.

![Các nút điều khiển lớp phủ](assets/overlay-controls.png)

---

## 🚀 Hướng dẫn cài đặt từng bước

1. **Chuẩn bị tệp APK TikTok gốc:** Tải tệp APK TikTok phiên bản **47.4.3** từ APKMirror: [TikTok 47.4.3 Release APK](https://www.apkmirror.com/apk/tiktok-pte-ltd/tik-tok-including-musical-ly/tiktok-47-4-3-release/).
   - *Kiến trúc hỗ trợ:* `arm64-v8a`, `armeabi-v7a`, `nodpi`, `minSdk 23`.
   - *Mã băm SHA-256 APK gốc:* `af7cbe2b00d33ff1339730f6abb8ed7e387a83b27ff2b16748b8f41e6fc87e0b`.
2. **Cài đặt Morphe Manager:** Sử dụng Morphe Manager phiên bản **1.34.0** trở lên. Trước khi vá, vào mục **Cài đặt trong Morphe Manager > Tiến trình thực thi (Process runtime)** và tăng dung lượng RAM lên ít nhất **1,024 MB** để quá trình biên dịch không bị gián đoạn.
3. **Thêm nguồn Buwin vào Morphe:**
   - Trên điện thoại, nhấn trực tiếp liên kết: **[Thêm Buwin vào Morphe](https://morphe.software/add-source?github=minh-quann%2Fbuwin-tiktok-patches)**.
   - Hoặc mở Morphe Manager $\to$ tab **Nguồn (Sources)** $\to$ bấm dấu **+** $\to$ dán liên kết: `https://github.com/minh-quann/buwin-tiktok-patches`.
   - Bạn cũng có thể tải tệp bundle `.mpp` từ trang [Releases mới nhất](https://github.com/minh-quann/buwin-tiktok-patches/releases/latest) để nạp dưới dạng tệp cục bộ.
4. **Chọn các bản vá:** Ở chế độ Đơn giản (Simple mode), Morphe Manager sẽ tự động tích chọn sẵn 117 bản vá tối ưu nhất. Bạn có thể chuyển sang chế độ Chuyên gia (Expert mode) để lựa chọn thêm 18 bản vá chuyên sâu theo nhu cầu.
5. **Tiến hành vá APK:** Chọn tệp APK TikTok gốc đã tải ở Bước 1 và nhấn **Patch**. Giữ nguyên khóa ký (Keystore) mặc định của Morphe để các bản cập nhật sau này có thể cài đè mà không làm mất dữ liệu.
6. **Cài đặt & Thưởng thức:** Cài đặt file APK đã vá, mở TikTok và vào mục **Cài đặt và quyền riêng tư (Settings and privacy)** $\to$ menu **Buwin** sẽ xuất hiện ngay ở đầu danh sách.

> [!NOTE]
> Các bản vá loại bỏ tệp tài nguyên thừa (như ngôn ngữ không dùng, công cụ tạo video) sẽ xóa trực tiếp trong quá trình đóng gói APK. Vì vậy tính năng Tạm dừng hay Khôi phục cài đặt không thể lấy lại các tệp này nếu bạn đã chọn xóa chúng lúc vá.

---

## ⚙️ Quản trị cài đặt Buwin & Lối tắt tiện lợi

Ngoài việc truy cập qua menu *Cài đặt và quyền riêng tư*, bạn có thể mở nhanh trang cài đặt Buwin ở bất kỳ đâu trên bảng tin bằng 2 lối tắt tiện lợi:
- **Nhấn và giữ chữ "Dành cho bạn"** ở đầu bảng tin video.
- **Nhấn và giữ biểu tượng "Trang chủ"** ở thanh điều hướng nổi phía dưới cùng *(yêu cầu bật bản vá Feed tab navigation)*.

Menu cài đặt tích hợp thanh tìm kiếm thông minh cho phép tra cứu nhanh mọi tính năng theo tên hoặc mô tả và bật/tắt trực tiếp tại chỗ. Nút **Cài đặt nhanh (Quick setups)** hỗ trợ chuyển đổi sang chế độ Yên tĩnh (Calm), Riêng tư (Private) hoặc khôi phục mặc định chỉ với một chạm.

<p align="center">
  <img src="assets/settings/settings.png" alt="Trang chủ cài đặt Buwin" width="260" />
  <img src="assets/settings/playback-sound.png" alt="Thẻ cài đặt âm thanh Chế độ tối" width="260" />
  <img src="assets/settings/playback-sound-light.png" alt="Thẻ cài đặt âm thanh Chế độ sáng" width="260" />
</p>

### Các nhóm danh mục chính trong Cài đặt Buwin:
- **Bảng tin của bạn:** Bộ lọc bảng tin, Điều hướng tab, Giao diện bảng tin, Phát lại và Thời gian sử dụng.
- **Trò chuyện:** Bình luận và Hộp thư đến.
- **Lưu & Chia sẻ:** Tải xuống và Bảng chia sẻ (Share sheet).
- **Quyền riêng tư & Ứng dụng:** Quyền riêng tư, Ứng dụng, Khu vực, Sao lưu & Khôi phục, và Feature Gate Lab.
- **Buwin:** Trạng thái hệ thống, Tạm dừng Buwin, Công cụ chẩn đoán, Giới thiệu Buwin và Báo lỗi.

---

## 📋 Danh mục 135 bản vá chi tiết

Bảng tra cứu toàn bộ 135 bản vá mã nguồn của Buwin:

| Tên bản vá (Morphe Patch) | Mô tả tính năng chi tiết |
|---|---|
| `Accent color` | Đổi màu chủ đạo của các nút bấm và chữ sang xanh dương, xanh lá, tím, cam, mã màu tùy chọn hoặc màu theo hình nền hệ thống (Material You từ Android 12 trở lên). Áp dụng lúc vá. |
| `Advanced downloads` | Chọn độ phân giải khi tải video, tải ảnh gốc HD, tách riêng file âm thanh nền, gộp bài đăng ảnh thành video MP4, tải hàng loạt từ trang cá nhân. Mặc định tắt. |
| `Allow Duet and Stitch` | Bật nút Duet và Stitch trên cả những video bị chủ bài đăng khóa. Mặc định tắt. |
| `Allow screenshots and Circle to Search` | Mở khóa chụp ảnh màn hình, quay video và Khoanh tròn để tìm kiếm (Circle to Search) trên mọi màn hình bị TikTok chặn. Mặc định tắt. |
| `Always show publish date` | Hiển thị ngày và giờ đăng video chi tiết ngay cạnh tên tác giả. Bật sẵn mặc định. |
| `Always upload in HD` | Luôn tải video lên với độ phân giải cao nhất (HD) mà không cần bật thủ công mỗi lần đăng bài. Mặc định tắt. |
| `AMOLED dark theme` | Đổi toàn bộ nền xám của Chế độ tối sang màu đen tuyền ($#000000$), tiết kiệm pin tối đa cho màn hình OLED. Áp dụng lúc vá. |
| `App lock` | Khóa bảo vệ TikTok bằng vân tay, khuôn mặt hoặc mã PIN khi mở app hoặc khi quay lại sau thời gian nhất định. Mặc định tắt. |
| `Automatic video advance` | Tự động chuyển tiếp sang video tiếp theo khi video hiện tại kết thúc rảnh tay. Mặc định tắt. |
| `Block author button` | Gắn thêm nút thao tác nhanh trên video để chặn tác giả, ẩn tác giả hoặc chặn âm thanh với một chạm. Mặc định tắt. |
| `Block contact list access` | Trả về danh bạ trống khi TikTok yêu cầu quyền đọc danh bạ điện thoại, bảo vệ sự riêng tư. Mặc định tắt. |
| `Block installed app scanning` | Trả về danh sách trống khi TikTok quét danh mục ứng dụng đã cài trên máy bạn. Mặc định tắt. |
| `Block P2P video relay` | Loại bỏ các thành phần biến điện thoại thành trạm phát lại P2P cho người khác, giảm dung lượng APK ~3.5 MB và tiết kiệm mạng. Áp dụng lúc vá. |
| `Block popups` | Chặn các cửa sổ bật lên phiền toái như gợi ý kết bạn, khuyến mãi hoặc lời mời sự kiện. Mặc định tắt. |
| `Block suggested video notifications` | Chặn thông báo gợi ý video phiền toái; thông báo tin nhắn và tương tác từ bạn bè vẫn nhận bình thường. Bật sẵn mặc định. |
| `Camera and microphone indicator` | Hiển thị chấm cảnh báo nhỏ ở góc màn hình khi TikTok đang truy cập camera hoặc micrô. Mặc định tắt. |
| `Change app name` | Đổi tên hiển thị của ứng dụng ngoài màn hình chính để phân biệt với bản TikTok gốc. Áp dụng lúc vá. |
| `Comment publish diagnostics` | Ghi lại nhật ký chẩn đoán khi gửi bình luận thất bại, tìm nguyên nhân bình luận bị ẩn. Tự động hoạt động. |
| `Comment send fix` | Sửa triệt để lỗi TikTok âm thầm hủy bình luận không lý do mà không hiện thông báo lỗi. Tự động hoạt động. |
| `Comment sort controls` | Luôn mở đầy đủ menu sắp xếp bình luận (Hàng đầu, Mới nhất, Bình luận có media, Bình luận của tác giả). Mặc định tắt. |
| `Comment tools` | Ẩn bình luận chứa từ khóa hoặc tài khoản chỉ định, thêm thanh tìm kiếm và mở liên kết trong bình luận. Mặc định tắt. |
| `Confirm feed interactions` | Yêu cầu chạm xác nhận lần hai trước khi Theo dõi, bấm Thích hoặc Đăng lại nhanh để chống chạm nhầm. Mặc định tắt. |
| `Copy bio and IDs` | Nhấn giữ tiểu sử để sao chép, thêm nút sao chép ID người dùng và ID video trong bảng chia sẻ. Bật sẵn mặc định. |
| `Copy comments without username` | Chỉ sao chép nội dung văn bản thuần túy của bình luận mà không dính kèm tên người dùng. Bật sẵn mặc định. |
| `Custom launcher icon` | Đổi biểu tượng ứng dụng ngoài màn hình chính sang biểu tượng theo chủ đề màu hệ thống (Themed icon) hoặc hình tùy chọn. Áp dụng lúc vá. |
| `Custom offline videos limit` | Mở rộng giới hạn tải video xem ngoại tuyến (từ 1 đến 10,000 video) và lưu trữ không giới hạn thời gian. Mặc định tắt. |
| `Device privacy guard` | Chặn TikTok đọc khay nhớ tạm (Clipboard), ẩn mã quảng cáo (GAID), ẩn kết nối VPN và trạng thái Root. Mặc định tắt. |
| `Diagnostic tools` | Cung cấp hệ thống ghi nhật ký và xuất báo cáo chẩn đoán chi tiết khi gặp lỗi. Mặc định tắt. |
| `Disable login requirement` | Gỡ bỏ yêu cầu bắt buộc đăng nhập tài khoản mới được xem video. Tự động hoạt động. |
| `Disable screen capture detection` | Ngăn chặn TikTok phát hiện hành động chụp ảnh màn hình hoặc quay video màn hình của bạn. Tự động hoạt động. |
| `Disable telemetry` | Vô hiệu hóa toàn bộ tiến trình gửi dữ liệu phân tích sử dụng và báo cáo lỗi về máy chủ TikTok và bên thứ ba. Mặc định tắt. |
| `Disable the long press quick share` | Vô hiệu hóa menu chia sẻ nhanh khi nhấn giữ nút Chia sẻ, tránh gửi nhầm video. Mặc định tắt. |
| `Disable the long press repost` | Vô hiệu hóa tính năng đăng lại nhanh khi nhấn giữ nút Thích. Mặc định tắt. |
| `Double-tap controls` | Tùy biến cử chỉ chạm 2 lần vào màn hình: không làm gì, mở bình luận, sao chép link, lưu âm thanh, tua nhanh hoặc hẹn giờ ngủ thay vì tự động thả tim. Mặc định tắt. |
| `Downloads` | Tải video và hình ảnh chất lượng cao không có watermark, tự do chọn thư mục lưu và cấu trúc đặt tên file. Bật sẵn mặc định. |
| `Drop the animated image cache` | Tối ưu hóa RAM bằng cách giải mã nhãn dán động và GIF theo từng khung hình phát thay vì nạp trước ồ ạt. Mặc định tắt. |
| `Enable voice comments` | Kích hoạt tính năng bình luận bằng giọng nói cho các tài khoản chưa được TikTok mở tính năng này. Mặc định tắt. |
| `Expand activity list` | Luôn tự động mở rộng danh sách Hoạt động và Người theo dõi mới trong Hộp thư đến. Mặc định tắt. |
| `Feature Gate Lab` | Trang thí nghiệm nâng cao cho phép can thiệp và bật/tắt các cờ tính năng ẩn thử nghiệm của TikTok. Mặc định tắt. |
| `Feature Gate Recorder` | Ghi lại các cờ thử nghiệm mà TikTok truy xuất trong quá trình bạn sử dụng app. Mặc định tắt. |
| `Feed filter` | Lọc sạch quảng cáo trên bảng tin, cho phép ẩn phòng LIVE, TikTok Shop, video gắn nhãn AI hoặc lọc theo từ khóa, tác giả. Chặn quảng cáo được bật sẵn. |
| `Feed tab navigation` | Tùy biến ẩn/hiện các tab trên đầu feed và thanh điều hướng bên dưới, đổi tên tab, chọn tab mặc định khi mở app. Quản lý trong Cài đặt Buwin. |
| `Feed text sizes` | Cho phép tăng/giảm kích thước phông chữ của phần mô tả video, tên tác giả và ngày đăng để dễ đọc hơn. Mặc định tắt. |
| `Fit the video to the screen` | Hiển thị trọn vẹn toàn bộ khung hình video mà không bị cắt hai bên trên màn hình gập, màn hình vuông hoặc đa nhiệm chia đôi màn hình. Mặc định tắt. |
| `Fix Google login` | Sửa lỗi không đăng nhập được bằng tài khoản Google trên các bản ứng dụng đã vá. Tự động hoạt động. |
| `Foldable split comment view` | Trên màn hình gập hoặc máy tính bảng, hiển thị bảng bình luận nằm song song bên cạnh video thay vì phủ đè lên video. Mặc định tắt. |
| `Follow diagnostics` | Hiển thị thông báo giải thích lý do cụ thể khi hành động Theo dõi bị hệ thống TikTok từ chối. Tự động hoạt động. |
| `Ghost mode` | Chế độ ẩn danh hoàn toàn: xem Story và Hồ sơ không để lại dấu vết, ẩn chỉ báo đang gõ tin nhắn và ẩn trạng thái online. Mặc định tắt. |
| `Hide already seen videos` | Ghi nhớ danh sách video đã xem và tự động ẩn khi feed phân phối lại, giúp bảng tin luôn luôn mới 100%. Mặc định tắt. |
| `Hide CAPTCHA popups` | Bỏ qua các thử thách giải đố CAPTCHA phiền toái hiện lên ngẫu nhiên khi đang lướt xem video. Mặc định tắt. |
| `Hide comment popup ads` | Chặn các hình động quảng cáo nhãn hàng tự nhảy lên khung bình luận khi gõ từ khóa được tài trợ. Mặc định tắt. |
| `Hide feed follow button` | Ẩn nút dấu cộng (+) Theo dõi nhanh bên dưới ảnh đại diện tác giả trên bảng tin để tránh bấm nhầm. Mặc định tắt. |
| `Hide feed LIVE button` | Ẩn nút LIVE và menu bên ở góc trên bên trái màn hình bảng tin. Mặc định tắt. |
| `Hide feed save button` | Ẩn nút Lưu video (Bookmark) ở cột chức năng bên phải để màn hình thoáng đãng hơn. Mặc định tắt. |
| `Hide feed search button` | Ẩn nút Tìm kiếm ở góc trên bên phải màn hình bảng tin. Mặc định tắt. |
| `Hide floating promotions` | Ẩn các huy hiệu khuyến mãi nổi, hộp quà, đồng xu và đồng hồ đếm ngược trên bảng tin. Mặc định tắt. |
| `Hide inbox items` | Ẩn các mục không dùng trong Hộp thư đến như Yêu cầu tin nhắn, TikTok Shop, hàng Stories, nút gọi điện. Mặc định tắt. |
| `Hide inbox stories` | Ẩn hàng bong bóng Stories trên đầu Hộp thư đến. Mặc định tắt. |
| `Hide comment typing suggestions` | Ẩn thanh gợi ý biểu tượng cảm xúc và nhãn dán tự nhảy lên phía trên ô nhập bình luận khi gõ phím. Mặc định tắt. |
| `Hide profile shortcuts` | Ẩn các nút lối tắt bên dưới tiểu sử hồ sơ như TikTok Studio, Đơn hàng của bạn, bong bóng Thoughts. Mặc định tắt. |
| `Hide search suggestions` | Ẩn danh sách tìm kiếm xu hướng gợi ý sẵn trước khi gõ từ khóa. Mặc định tắt. |
| `Hide suggested accounts` | Ẩn mục gợi ý "Những người bạn có thể biết" trong Hộp thư đến, Hồ sơ, tab Bạn bè và trên Bảng tin. Mặc định tắt. |
| `Hide the launcher shortcuts` | Ẩn các lối tắt xuất hiện khi nhấn giữ biểu tượng ứng dụng TikTok ngoài màn hình chính điện thoại. Mặc định tắt. |
| `Hide Play Store update offer` | Ngăn chặn Google Play đề xuất cập nhật đè bản ứng dụng chính thức lên bản đã vá. Áp dụng lúc vá. |
| `Hide the risk control CAPTCHA` | Bỏ qua các câu đố kiểm tra rủi ro tài khoản khi lướt thông thường. Dành cho người dùng chuyên sâu. |
| `Hide video overlays` | Cho phép ẩn từng thành phần chữ trên video như phần mô tả, thanh nhạc, các nút bên phải, nhãn địa điểm... để xem trọn vẹn video. Mặc định tắt. |
| `Hold-and-slide 2x lock` | Nhấn giữ video rồi kéo nhẹ ngón tay xuống dưới để khóa tốc độ phát nhanh $2\times$ rảnh tay mà không cần giữ liên tục. Mặc định tắt. |
| `In-app browser privacy guard` | Ngăn chặn các trang web mở trong trình duyệt nội bộ của TikTok truy cập trái phép vào dữ liệu ứng dụng. Mặc định tắt. |
| `Keep a streak going` | Tự động gửi tin nhắn mẫu cho những người bạn chỉ định vào khung giờ định kỳ mỗi ngày để duy trì chuỗi tia sét (Streak). Mặc định tắt. |
| `Keep playing in the background` | Cho phép video tiếp tục phát âm thanh khi chuyển sang ứng dụng khác hoặc khóa màn hình, kèm thông báo tạm dừng tiện lợi. Mặc định tắt. |
| `Keep pulled sounds` | Tiếp tục phát âm thanh đối với những video bị TikTok tắt tiếng do bản quyền hoặc hạn chế khu vực. Mặc định tắt. |
| `Keep the app language` | Khóa cố định ngôn ngữ bạn đã chọn trong cài đặt TikTok, ngăn app tự đổi theo ngôn ngữ hệ thống. Mặc định tắt. |
| `Keep the Favorites tab` | Giữ lại tab Mục yêu thích và video đã lưu trên hồ sơ khi TikTok thử nghiệm giao diện ẩn tab này. Bật sẵn mặc định. |
| `Keep the screen's refresh rate` | Ngăn TikTok tự hạ tần số quét màn hình theo khung hình video, duy trì độ mượt 90Hz hoặc 120Hz ổn định khi cuộn lướt. Mặc định tắt. |
| `Lift text length limits` | Mở rộng giới hạn ký tự tối đa cho phép viết bình luận, ghi chú đăng lại và tiểu sử cá nhân dài hơn mặc định. Mặc định tắt. |
| `Limit background traffic` | Ngăn TikTok tải trước video phía sau khi chạy nền, tiết kiệm dung lượng 4G/5G đáng kể. Mặc định tắt. |
| `LIVE controls` | Chặn phòng LIVE trên bảng tin tự phóng to sau khi đếm ngược; hiển thị số người xem chính xác, cung cấp tùy chọn chất lượng và nút Ghi hình trực tiếp buổi phát. Mặc định tắt. |
| `Location access governor` | Trả về vị trí trống khi TikTok yêu cầu tọa độ GPS, bảo vệ vị trí thực của bạn. Mặc định tắt. |
| `Long-press controls` | Tùy biến cử chỉ nhấn giữ vào video: thả tim, mở bình luận, sao chép link, lưu âm thanh, chụp ảnh khung hình hoặc hẹn giờ tắt. Mặc định tắt. |
| `Look like the store app` | Giả lập chữ ký phản hồi với hệ thống TikTok như thể ứng dụng tải từ Google Play Store, hỗ trợ chống bị nhả Follow. Áp dụng lúc vá. |
| `Mute feed videos` | Tắt tiếng video trên bảng tin mà không làm ảnh hưởng đến âm lượng phát nhạc của các ứng dụng khác đang chạy ngầm. Mặc định tắt. |
| `Network proxy` | Định tuyến toàn bộ kết nối bảng tin, tìm kiếm và bình luận qua máy chủ Proxy cá nhân của bạn mà không ảnh hưởng ứng dụng khác. Mặc định tắt. |
| `Network request report` | Thống kê số lượng yêu cầu mạng gửi đến các máy chủ và thêm vào báo cáo chẩn đoán. Dành cho nghiên cứu chuyên sâu. |
| `Not interested button` | Gắn nút "Không quan tâm" trực tiếp trên màn hình video để chặn các nội dung tương tự với một chạm. Mặc định tắt. |
| `Notification controls` | Cho phép tắt riêng lẻ thông báo có người theo dõi mới, nhắc nhở chuỗi tia sét hoặc tắt hoàn toàn mọi thông báo đẩy. Mặc định tắt. |
| `Open external links directly` | Mở liên kết trang web từ tiểu sử hoặc story trực tiếp bằng trình duyệt ngoài của điện thoại. Bật sẵn mặc định. |
| `Picture-in-picture` | Kích hoạt chế độ Hình trong hình (PiP), video thu nhỏ thành cửa sổ nổi góc màn hình khi bạn thoát ra ngoài làm việc khác. Mặc định tắt. |
| `Play SDR instead of HDR` | Luôn phát phiên bản SDR tiêu chuẩn của các video HDR, tránh việc màn hình bị đẩy độ sáng đột ngột gây chói mắt vào ban đêm. Mặc định tắt. |
| `Playback quality` | Tự do chọn chất lượng phát video mặc định (Cao nhất, Thấp nhất, 720p...) và đặt chất lượng thấp hơn khi dùng dữ liệu di động. Mặc định tắt. |
| `Playback speed` | Ghi nhớ tốc độ phát đã chọn cho các video tiếp theo, cho phép đặt tốc độ phát mặc định, mở rộng tốc độ lên đến $3\times$. Bật sẵn mặc định. |
| `Prefer H.264 playback` | Ưu tiên giải mã định dạng video H.264 giúp thiết bị chạy mát hơn và tiêu thụ ít pin hơn. Mặc định tắt. |
| `Profile background` | Mở khóa trình chỉnh sửa ảnh nền trang cá nhân và hiển thị hình nền cá nhân của người khác. Mặc định tắt. |
| `Region spoof` | Giả lập mã quốc gia và nhà mạng theo quốc gia bạn chọn để mở khóa nội dung của vùng đó. Mặc định tắt. |
| `Remember clear display` | Tự động duy trì chế độ màn hình sạch (Clear display) khi vuốt sang các video tiếp theo, hỗ trợ hẹn giờ tự bật sau vài giây. Mặc định tắt. |
| `Remove avatar rings` | Gỡ bỏ vòng hào quang Story và LIVE xung quanh ảnh đại diện, chạm vào ảnh là mở thẳng trang hồ sơ. Mặc định tắt. |
| `Remove content credential and card scanner assets` | Loại bỏ các thư viện quét thẻ thanh toán và chứng thực bản quyền thừa thãi, giảm kích thước APK ~11 MB. Áp dụng lúc vá. |
| `Remove creation tools` | Loại bỏ toàn bộ camera, trình dựng video và hiệu ứng của TikTok, giảm dung lượng cài đặt ~38 MB (chỉ nên dùng nếu bạn không quay video). Áp dụng lúc vá. |
| `Remove LIVE extras` | Loại bỏ hiệu ứng quà tặng, trò chơi mini và khách mời trong phòng LIVE, giảm dung lượng APK thêm ~3 MB. Áp dụng lúc vá. |
| `Remove unused language packs` | Loại bỏ toàn bộ các gói ngôn ngữ không sử dụng, giảm dung lượng bộ nhớ đến ~26 MB. Tiếng Anh và tiếng Việt được giữ lại an toàn. Áp dụng lúc vá. |
| `Repost diagnostics` | Ghi lại trạng thái phản hồi của máy chủ TikTok khi đăng lại video để theo dõi các trường hợp video đăng lại không hiển thị. Tự động hoạt động. |
| `Resource and battery governor` | Ngăn chặn TikTok đọc con quay hồi chuyển/chuyển động và tắt bài kiểm tra tốc độ mạng chạy ngầm để tiết kiệm pin tối đa. Mặc định tắt. |
| `Resume videos after scrolling` | Khi vuốt quay trở lại video trước đó, video sẽ tiếp tục phát từ đoạn đang xem dở thay vì phát lại từ đầu. Bật sẵn mặc định. |
| `Run beside the store app` | Cho phép cài đặt bản TikTok đã vá song song độc lập với bản TikTok gốc tải từ CH Play mà không xung đột mã thiết bị. Áp dụng lúc vá. |
| `Sanitize sharing links` | Tự động loại bỏ toàn bộ các đoạn mã theo dõi cá nhân khỏi liên kết chia sẻ video trước khi gửi đi. Bật sẵn mặc định. |
| `Settings` | Tích hợp toàn bộ hệ thống menu Cài đặt Buwin vào ứng dụng TikTok, kích hoạt phím tắt nhấn giữ Dành cho bạn. Bản vá cốt lõi bắt buộc. |
| `Share sheet tools` | Tùy biến ẩn những người và ứng dụng không dùng khỏi bảng chia sẻ, bổ sung các ứng dụng bạn hay dùng và sao chép link nhanh một chạm. Mặc định tắt. |
| `Show author region` | Hiển thị quốc gia phát hành của video ngay bên cạnh tên tác giả. Mặc định tắt. |
| `Show commenter handle and region` | Hiển thị ID người dùng (@username) và quốc gia của người bình luận ngay sau mốc thời gian. Mặc định tắt. |
| `Show engagement rate` | Hiển thị tỷ lệ tương tác (tổng tương tác so với lượt xem) ngay cạnh tên tác giả. Mặc định tắt. |
| `Show exact counts` | Hiển thị số lượt thích, bình luận và chia sẻ dưới dạng số đếm chi tiết đầy đủ (ví dụ $1,234,567$ thay vì $1.2\text{M}$). Mặc định tắt. |
| `Show follow status` | Hiển thị rõ trên trang cá nhân của người khác xem họ có đang theo dõi lại bạn hay không. Bật sẵn mặc định. |
| `Show LIVE search` | Bổ sung thanh tìm kiếm phòng phát trực tiếp chuyên biệt bên trong mục LIVE. Mặc định tắt. |
| `Show the progress bar` | Luôn hiển thị thanh thời gian tiến trình trên mọi video để dễ dàng theo dõi và tua nhanh. Bật sẵn mặc định. |
| `Show the progress bar thumbnail` | Hiển thị khung hình xem trước nhỏ gọn khi kéo thanh tiến trình để tìm chính xác phân đoạn cần xem. Bật sẵn mặc định. |
| `SIM spoof` | Giả lập thông tin thẻ SIM và nhà mạng quốc tế mà bạn chỉ định. Mặc định tắt. |
| `Skip content warnings` | Bỏ qua các màn hình cảnh báo nhạy cảm phiền toái để phát thẳng video. Mặc định tắt. |
| `Skip first-launch setup` | Tự động bỏ qua các màn hình thiết lập ban đầu khi mới cài đặt ứng dụng (chọn sở thích, hướng dẫn vuốt tay). Bật sẵn mặc định. |
| `Skip passkey sign-in` | Chuyển hướng sang các phương thức đăng nhập khác khi TikTok yêu cầu mã khóa bảo mật Passkey. Mặc định tắt. |
| `Skip the splash ad` | Chặn hoàn toàn quảng cáo toàn màn hình hiển thị mỗi khi mở ứng dụng TikTok. Mặc định tắt. |
| `Skip update checks` | Vô hiệu hóa các tác vụ ngầm kiểm tra cập nhật, ngăn chặn thông báo nhắc cập nhật trong app. Mặc định tắt. |
| `Stay on the video in full screen` | Khi video kết thúc trong chế độ toàn màn hình, giữ nguyên ở video đó thay vì tự động chuyển sang video khác. Mặc định tắt. |
| `Stop on-device AI profiling` | Ngăn chặn công cụ trí tuệ nhân tạo (AI) chạy ngầm trên máy của TikTok thu thập hồ sơ hành vi người dùng. Mặc định tắt. |
| `Stop recording watch history` | Ngăn chặn TikTok ghi nhận các video đang xem vào danh sách Lịch sử xem tài khoản. Mặc định tắt. |
| `Stop saving search history` | Không lưu lại các từ khóa tìm kiếm mới trên điện thoại của bạn. Mặc định tắt. |
| `Stop search autoplay` | Ngăn video trong kết quả tìm kiếm tự động phát âm thanh. Mặc định tắt. |
| `Stop video looping` | Tự động dừng video khi phát hết thay vì lặp lại liên tục. Mặc định tắt. |
| `Story controls` | Cho phép phát lại Story khi kết thúc và giữ bài đăng ảnh trên màn hình cho đến khi bạn chạm hoặc vuốt. Mặc định tắt. |
| `Subtitle tools` | Tùy biến phóng to phụ đề, đổi màu nền phụ đề, giữ hiển thị trong chế độ màn hình sạch và lưu kèm file SRT khi tải video. Mặc định tắt. |
| `Swipe-left controls` | Tùy biến cử chỉ vuốt sang trái trên video: mở bình luận hoặc không làm gì, thay vì tự mở trang cá nhân của tác giả. Mặc định tắt. |
| `Translate comments` | Tự động dịch bình luận sang tiếng Việt ngay khi cuộn tới bằng chính bộ dịch gốc của TikTok. Mặc định tắt. |
| `Trust user certificates` | Cho phép TikTok tin cậy các chứng chỉ bảo mật người dùng tự cài đặt trên máy để phân tích gói tin mạng. Áp dụng lúc vá. |
| `Turn off haptics` | Tắt phản hồi rung xúc giác của TikTok khi chạm hoặc nhấn giữ nút. Mặc định tắt. |
| `Turn off screen transitions` | Vô hiệu hóa hiệu ứng chuyển cảnh giữa các màn hình giúp thao tác điều hướng nhanh và nhạy hơn. Mặc định tắt. |
| `Use non-personalized search` | Hiển thị kết quả tìm kiếm tiêu chuẩn khách quan, không bị thuật toán cá nhân hóa theo tài khoản. Mặc định tắt. |
| `Use system font` | Sử dụng phông chữ mặc định của hệ thống điện thoại hoặc file `.ttf`/`.otf` tùy chỉnh thay vì font mặc định của TikTok. Mặc định tắt. |

---

## ❓ Hướng dẫn trợ giúp & Xử lý sự cố thường gặp

### 1. Đăng nhập tài khoản thất bại
- **Tắt DNS riêng tư và AdGuard:** Tạm thời tắt Private DNS, AdGuard, NextDNS hoặc VPN khi đăng nhập, vì chúng có thể chặn nhầm các máy chủ xác thực tài khoản của ByteDance.
- **Đăng nhập bằng Facebook:** Facebook kiểm tra chữ ký gốc của ứng dụng khi cấp quyền, trong khi bản vá sử dụng khóa ký riêng của bạn. Hãy đăng nhập bằng **Google**, **Email**, **Số điện thoại** hoặc **Tên người dùng** kèm mã SMS/Email.
- **Nếu bị kẹt ở màn hình CAPTCHA:** Tạm thời tắt bản vá *Hide CAPTCHA popups*, khởi động lại TikTok để vượt qua câu đố xác minh ban đầu.

### 2. Quá trình vá bị đứng ở 24% hoặc 25%
Nguyên nhân do Morphe Manager bị thiếu bộ nhớ RAM:
- Hủy tiến trình vá.
- Vào mục **Cài đặt trong Morphe Manager (Settings) > Tiến trình thực thi (Process runtime)** $\to$ nâng mức RAM lên **1,024 MB** rồi tiến hành vá lại. Bạn có thể chọn tất cả bản vá cùng lúc mà không lo xung đột.

### 3. Cài đặt 2 ứng dụng TikTok chạy song song độc lập
- Trong Morphe Manager, chọn thêm bản vá **Clone app** của Morphe (giữ nguyên tùy chọn Update permissions và Update providers).
- Đồng thời tích chọn thêm bản vá **Run beside the store app** của Buwin.
- Bạn có thể đổi tên ứng dụng qua bản vá **Change app name** để dễ phân biệt hai icon ngoài màn hình chính.

### 4. Kiểm tra nghi ngờ lỗi do bản vá (Tính năng Pause Buwin)
Vào **Cài đặt Buwin > Tạm dừng Buwin (Pause Buwin)** rồi khởi động lại app. Thao tác này sẽ tạm ngưng toàn bộ can thiệp smali thời gian chạy nhưng vẫn bảo toàn nguyên vẹn mọi cấu hình bạn đã chọn. Nếu lỗi vẫn xảy ra khi Buwin đang tạm dừng, nguyên nhân xuất phát từ chính phiên bản APK TikTok gốc.

### 5. Cơ chế tự cứu khi ứng dụng bị Crash (Chế độ Safe Mode)
Nếu TikTok bị đóng đột ngột trong vòng 1 phút sau khi mở liên tiếp 3 lần, Buwin sẽ tự động kích hoạt **Safe Mode** và tạm dừng hoạt động ở lần mở tiếp theo để bạn có thể vào cài đặt hoàn tác tùy chọn gây lỗi.
Nếu ứng dụng bị văng liên tục không thể mở, bạn chỉ cần tạo một tệp tin trống có tên:
- `buwin-safe-mode` tại thư mục `/sdcard/Android/data/com.zhiliaoapp.musically/files/`
- Hoặc chạy qua máy tính bằng adb:
  ```bash
  adb shell touch /sdcard/Android/data/com.zhiliaoapp.musically/files/buwin-safe-mode
  ```
Buwin sẽ tự động tạm dừng mọi can thiệp khi phát hiện tệp này. Sau khi khắc phục xong, nhấn nút **Bật lại Buwin** trong menu để hệ thống tự xóa tệp cờ.

---

## 🔒 Cam kết quyền riêng tư & An toàn dữ liệu

Buwin **không sở hữu máy chủ trung gian và không thu thập bất kỳ dữ liệu cá nhân nào của bạn**. Toàn bộ mã nguồn bổ sung hoạt động 100% cục bộ trên thiết bị của bạn:
- **Tải tệp trực tiếp an toàn:** Khi bấm tải video, hình ảnh, âm thanh, phụ đề hoặc nhãn dán, ứng dụng kết nối trực tiếp qua HTTPS bảo mật đến máy chủ CDN chính thức của TikTok.
- **Làm sạch liên kết:** Loại bỏ toàn bộ các tham số tiếp thị và theo dõi cá nhân trong liên kết chia sẻ trực tiếp trên máy trước khi tạo link.
- **Bảo vệ chẩn đoán:** Báo cáo chẩn đoán chỉ lưu cục bộ trên bộ nhớ máy và tự động loại trừ mọi Token đăng nhập, Cookie, ID thiết bị và thông tin nhạy cảm.

---

## ❤️ Lời cảm ơn & Bản quyền

Buwin được phát triển và kế thừa từ những cống hiến to lớn của cộng đồng mã nguồn mở:
- Dự án [icysymmetra/tiktok-patches-for-morphe](https://github.com/icysymmetra/tiktok-patches-for-morphe) về cấu trúc smali nền tảng và khung cài đặt trực quan.
- Dự án [kveld9/kveld-morphe-patches](https://github.com/kveld9/kveld-morphe-patches) về các giải pháp tối ưu hóa thời gian chạy.
- Cộng đồng [ReVanced](https://gitlab.com/revanced/revanced-patches) và [RookieEnough/De-Vanced](https://github.com/RookieEnough/De-Vanced) về kỹ thuật vá ứng dụng di động.
- Dự án [hxreborn/hxreborn-tiktok-patches](https://github.com/hxreborn/hxreborn-tiktok-patches), [BlueDragon4251](https://github.com/BlueDragon4251/tiktok-patches-for-morphe), [eduardo3677-ai](https://github.com/eduardo3677-ai/tiktok-patches-for-morphe), [na-x-ril](https://github.com/na-x-ril/onlynazril-patches), [@lyyako](https://github.com/lyyako), [@oscski](https://github.com/oscski).
- Đội ngũ phát triển [Morphe](https://github.com/MorpheApp) về trình quản lý Morphe Manager và bộ công cụ mã nguồn mở tuyệt vời.

### Miễn trừ trách nhiệm & Bản quyền
- Buwin là dự án tùy biến độc lập mã nguồn mở và không có bất kỳ liên kết thương mại nào với ByteDance, TikTok hay Morphe.
- Mã nguồn chính thức của dự án được lưu trữ tại: [minh-quann/buwin-tiktok-patches](https://github.com/minh-quann/buwin-tiktok-patches).
- Phát hành dưới giấy phép mã nguồn mở [GNU General Public License v3.0](LICENSE).
