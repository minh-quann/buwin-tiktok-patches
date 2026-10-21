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
