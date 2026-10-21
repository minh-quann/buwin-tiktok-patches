# Quy trình sau khi sửa patch

Theo yêu cầu của chủ repo: sau khi sửa và kiểm tra patch, phải:

1. Biên dịch lại toàn bộ Java extension bằng `javac`, chuyển thành `classes.dex` bằng Android D8; không tái sử dụng bytecode cũ cho source đã thay đổi.
   Lệnh hỗ trợ: `python scripts/build-local-extension.py --cache <đường-dẫn-mpp>`.
2. Cập nhật entry `extensions/tiktok.mpe` (DEX) trong cache Morphe Desktop đang dùng. Hiện tại: `/home/quan/.local/share/morphe-desktop/morphe-data/patches/minh-quann-buwin-tiktok-patches/v0.75.0__buwin-patches-0.75.0.mpp`. Sao lưu trước khi thay, xác minh DEX trong bundle trùng với DEX vừa build. Khi đổi phiên bản phải dùng cache tương ứng.
3. Commit thay đổi và push lên `origin/main`. Không ghi đè thay đổi của người khác hoặc force push. Chỉ báo hoàn thành bước nào khi đã có kết quả xác minh.

Nếu môi trường chặn một bước, báo rõ bước chưa hoàn tất; không bỏ qua âm thầm.

## CodeGraph

Nếu repo có `.codegraph/`, dùng CodeGraph trước khi tìm/đọc code. Không tự tạo index.

## Hiển thị feed

Giữ video và giao diện tràn lên status bar như iOS theo yêu cầu của chủ repo. Khi sửa giật lúc lướt, sửa thời điểm tính/áp dụng kích thước; không tắt fill hoặc thu video xuống dưới status bar để né lỗi.

Khoảng kê/nền đen ở đáy phải thuộc component bottom tabs gốc; không dành khoảng bằng padding/background của Activity và không thêm overlay. Khi sửa hook Kotlin, phải cập nhật cả JVM classes và patch DEX trong MPP cùng với extension; `scripts/build-local-native-bottom-bar.py` hỗ trợ cache local khi registry Gradle chưa khả dụng.

Khi tăng chiều cao bottom tabs, phải nâng đồng bộ widget native (caption, cột nút, đĩa nhạc) và thanh tua trong bước measure/layout; không chỉ nâng riêng bar. Tính viewport video theo khung pager cố định, tuyệt đối không lấy tọa độ màn hình của cell đang cuộn hoặc cell được preload. Scale/crop phải được quyết định từ adaption policy gốc trước frame đầu, giữ fill lên status bar.

Trang cá nhân phải giữ khoảng status bar gốc cho header/nút, không dịch TuxNavBar lên y=0. Khoảng chừa đáy khi ẩn thanh điều hướng áp dụng ở component native của chat, thanh nhập bình luận và bottom sheet; đồng bộ spacer của video mở riêng, không cộng đệm hai lần khi sheet lồng nhau và không cộng phần đệm thêm khi IME đang mở.

Crop phải dựa trên kích thước luồng gốc: chỉ fill video dọc, giữ nguyên video ngang và nút toàn màn hình. Trong chế độ fill, chặn native comment-shrink trước animation để đóng sheet không nhảy từ khung nhỏ sang fill. LIVE preview dùng SurfaceView riêng: sửa onMeasure native, chỉ crop surface, nâng cả nhóm tips/user và card thương mại ở đáy; không scale card hoặc áp dụng vào phòng LIVE đã mở.

Burn-in pixel shift chỉ được dịch foreground controls (khử trùng lặp cha/con); tuyệt đối không dịch android.R.id.content, player/surface hoặc nền bottom bar. Dịch Activity làm lộ nền cửa sổ thành viền đen đổi bên mỗi 2 phút; tăng crop không sửa được lỗi đó.
