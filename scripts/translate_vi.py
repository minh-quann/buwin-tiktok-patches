#!/usr/bin/env python3
"""Translate settings into Vietnamese for Buwin TikTok Patches.

Translates extensions/tiktok/src/main/l10n/en.csv into extensions/tiktok/src/main/l10n/vi.csv,
preserving all placeholder formats, matching sentence terminators, respecting domain glossaries,
and ensuring strict adherence to SettingsL10nTest rules.
"""

import csv
import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
L10N_DIR = os.path.join(ROOT, "extensions", "tiktok", "src", "main", "l10n")
EN_CSV = os.path.join(L10N_DIR, "en.csv")
VI_CSV = os.path.join(L10N_DIR, "vi.csv")
CACHE_FILE = os.path.join(ROOT, "scripts", "vi_translation_cache.json")

PLACEHOLDER_RE = re.compile(r"%(?:\d+\$)?[a-zA-Z]|\{[a-zA-Z0-9_]+\}")
TERMINATORS = ".!?:\u2026"

# Core dictionary of known settings titles and UI terms to ensure consistent high-quality terminology
EXACT_TITLES = {
    "Search within comments": "Tìm kiếm trong bình luận",
    "Download original photos": "Tải ảnh gốc",
    "Save the sound as well": "Lưu cả âm thanh",
    "Save the cover as well": "Lưu cả ảnh bìa",
    "Check for already-saved videos": "Kiểm tra video đã lưu",
    "Use system font": "Sử dụng phông chữ hệ thống",
    "Use an imported font": "Sử dụng phông chữ đã nhập",
    "Hide the status bar in LIVE rooms": "Ẩn thanh trạng thái trong phòng LIVE",
    "Hide location labels": "Ẩn nhãn vị trí",
    "Blocked creators": "Nhà sáng tạo bị chặn",
    "Creators hidden on this phone": "Nhà sáng tạo bị ẩn trên điện thoại này",
    "Creator exceptions": "Ngoại lệ nhà sáng tạo",
    "Blocked caption words": "Từ khóa chú thích bị chặn",
    "Spin the music disc": "Xoay đĩa nhạc",
    "Leave when TikTok says time is up": "Thoát khi TikTok thông báo hết giờ",
    "See-through bottom bar": "Thanh điều hướng dưới trong suốt",
    "Seek from the edges": "Tua video từ mép màn hình",
    "Ghost mode": "Chế độ ẩn danh",
    "Ghost Mode": "Chế độ ẩn danh",
    "Feature Gate Lab": "Phòng thử nghiệm tính năng",
    "Auto-advance videos": "Tự động chuyển tiếp video",
    "Auto-advance": "Tự động chuyển tiếp",
    "Playback speed": "Tốc độ phát",
    "Clear display": "Chế độ xem rõ nét",
    "Burn-in guard": "Bảo vệ chống lưu ảnh",
    "About Buwin": "Về Buwin",
    "Buwin is active": "Buwin đang hoạt động",
    "Buwin is paused": "Buwin đã tạm dừng",
    "Buwin": "Buwin",
    "Your feed": "Bảng tin của bạn",
    "Your feed. Your rules.": "Bảng tin của bạn. Quy tắc của bạn.",
    "Make it yours.": "Tùy biến theo cách của bạn.",
    "Conversations": "Trò chuyện",
    "Saving and sharing": "Lưu và chia sẻ",
    "Privacy and app": "Quyền riêng tư và ứng dụng",
    "Budgets and reminders": "Ngân sách và lời nhắc",
    "Active": "Đang hoạt động",
    "Paused": "Đã tạm dừng",
    "Restart pending": "Đang chờ khởi động lại",
}

GLOSSARY_REPLACEMENTS = [
    # Replace awkward machine translations
    (r"\bnguồn cấp dữ liệu\b", "bảng tin"),
    (r"\bNguồn cấp dữ liệu\b", "Bảng tin"),
    (r"\bnguồn cấp\b", "bảng tin"),
    (r"\bNguồn cấp\b", "Bảng tin"),
    (r"\bchế độ ma quái\b", "chế độ ẩn danh"),
    (r"\bChế độ ma quái\b", "Chế độ ẩn danh"),
    (r"\bchế độ ma\b", "chế độ ẩn danh"),
    (r"\bphòng thí nghiệm cổng tính năng\b", "Phòng thử nghiệm tính năng"),
    (r"\bPhòng thí nghiệm cổng tính năng\b", "Phòng thử nghiệm tính năng"),
    (r"\bPhòng thí nghiệm tính năng\b", "Phòng thử nghiệm tính năng"),
    (r"\bcổng tính năng\b", "cổng tính năng"),
    (r"\bảnh chụp màn hình\b", "ảnh chụp màn hình"),
    (r"\bvăn bản mô tả\b", "văn bản chú thích"),
    (r"\bngười sáng tạo\b", "nhà sáng tạo"),
    (r"\bNgười sáng tạo\b", "Nhà sáng tạo"),
]

def mask_text(text):
    tokens = []
    def repl(m):
        idx = len(tokens)
        tokens.append(m.group(0))
        return f"ZZPH{idx}ZZ"
    masked = PLACEHOLDER_RE.sub(repl, text)
    return masked, tokens

def unmask_text(text, tokens):
    for idx, token in enumerate(tokens):
        # Match variations of ZZPHnZZ with spaces or casing
        pattern = re.compile(rf"ZZ\s*PH\s*{idx}\s*ZZ", re.IGNORECASE)
        text = pattern.sub(token, text)
    return text

def translate_http(text):
    if not text.strip():
        return text
    # Try dict-chrome-ex endpoint first
    try:
        url = "https://clients5.google.com/translate_a/t?client=dict-chrome-ex&sl=en&tl=vi&q=" + urllib.parse.quote(text)
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            if isinstance(data, list):
                return "".join(data)
            return str(data)
    except Exception:
        # Fallback to gtx endpoint
        url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=vi&dt=t&q=" + urllib.parse.quote(text)
        req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (X11; Linux x86_64)"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return "".join(part[0] for part in data[0] if part[0])

def translate_with_retry(text, max_retries=3):
    masked, tokens = mask_text(text)
    for attempt in range(max_retries):
        try:
            trans = translate_http(masked)
            unmasked = unmask_text(trans, tokens)
            return unmasked
        except Exception as e:
            if attempt == max_retries - 1:
                print(f"Error translating: {text[:40]}...: {e}")
                return text
            time.sleep(1 + attempt * 2)

def ends_in_abbreviation(text):
    trimmed = text.strip()
    if not trimmed.endswith("."):
        return False
    at = len(trimmed) - 1
    letters = 0
    while at > 0 and trimmed[at - 1].isalpha():
        at -= 1
        letters += 1
    return 0 < letters <= 4

def terminator(text):
    trimmed = text.strip()
    if not trimmed:
        return ' '
    last = trimmed[-1]
    return last if last in TERMINATORS else ' '

def fix_terminator_and_quotes(key, trans):
    k_term = terminator(key)
    trans = trans.strip()
    if not trans:
        return trans

    # Fix terminator
    t_term = terminator(trans)
    if k_term != ' ':
        if t_term != ' ':
            trans = trans[:-1].rstrip() + k_term
        else:
            trans = trans + k_term
    else:
        # Key has no terminator, remove trailing terminators unless it is an abbreviation
        if not ends_in_abbreviation(trans):
            while trans and terminator(trans) != ' ':
                trans = trans[:-1].rstrip()

    # Balance curly quotes
    open_q = trans.count('\u201c')
    close_q = trans.count('\u201d')
    if open_q > close_q:
        trans += '\u201d' * (open_q - close_q)
    elif close_q > open_q:
        trans = ('\u201c' * (close_q - open_q)) + trans

    return trans

def polish_vietnamese(key, trans):
    # Apply exact titles if applicable
    if key in EXACT_TITLES:
        trans = EXACT_TITLES[key]

    # Apply glossary replacements
    for pattern, replacement in GLOSSARY_REPLACEMENTS:
        trans = re.sub(pattern, replacement, trans)

    # Ensure placeholders match
    k_phs = PLACEHOLDER_RE.findall(key)
    t_phs = PLACEHOLDER_RE.findall(trans)
    all_numbered = bool(k_phs and all("$" in p for p in k_phs))
    mismatch = sorted(k_phs) != sorted(t_phs) if all_numbered else k_phs != t_phs
    if mismatch:
        # Fallback to reconstructing placeholders
        for p in k_phs:
            if p not in trans:
                trans += f" ({p})"

    # Fix punctuation & terminators
    trans = fix_terminator_and_quotes(key, trans)
    return trans

def main():
    if not os.path.exists(EN_CSV):
        sys.exit(f"Source file {EN_CSV} does not exist.")

    with open(EN_CSV, encoding="utf-8") as f:
        reader = csv.reader(f)
        header = next(reader)
        en_rows = list(reader)

    en_keys = [r[0] for r in en_rows]
    print(f"Loaded {len(en_keys)} keys from {EN_CSV}")

    # Load cache if available
    cache = {}
    if os.path.exists(CACHE_FILE):
        try:
            with open(CACHE_FILE, encoding="utf-8") as f:
                cache = json.load(f)
            print(f"Loaded {len(cache)} cached translations.")
        except Exception as e:
            print(f"Could not load cache: {e}")

    def is_real_english(s):
        clean = re.sub(r"%(?:\d+\$)?[a-zA-Z]|\{[^}]+\}|[0-9%:,. \-()_/+]", "", s)
        if clean in ["Buwin", "TikTok", "GIF", "HTTP", "HTTPS", "JSON", "CSV", "URL", "ID", "MB", "KB", "dp", "px", "ms", "s"]:
            return False
        return len(clean) > 3

    # Identify missing translations
    missing = [k for k in en_keys if k not in cache or (cache[k] == k and is_real_english(k))]
    print(f"{len(missing)} translations to fetch.")

    if missing:
        # Translate concurrently with rate control
        def worker(key):
            if key in EXACT_TITLES:
                return key, EXACT_TITLES[key]
            time.sleep(0.1)
            trans = translate_with_retry(key)
            return key, trans

        with ThreadPoolExecutor(max_workers=3) as executor:
            future_to_key = {executor.submit(worker, k): k for k in missing}
            count = 0
            for future in as_completed(future_to_key):
                key, trans = future.result()
                cache[key] = trans
                count += 1
                if count % 20 == 0 or count == len(missing):
                    print(f"Progress: {count}/{len(missing)} translated...")
                    # Save intermediate cache
                    with open(CACHE_FILE, "w", encoding="utf-8") as f:
                        json.dump(cache, f, ensure_ascii=False, indent=2)

        # Save final cache
        with open(CACHE_FILE, "w", encoding="utf-8") as f:
            json.dump(cache, f, ensure_ascii=False, indent=2)

    # Post-process all translations
    translations = {}
    for key in en_keys:
        trans = cache.get(key, key)
        translations[key] = polish_vietnamese(key, trans)

    # Polished exact translations for quantities, numbers, and grammar
    POLISHED_EXACT = {
        "%1$d already matched": "%1$d đã khớp",
        "%1$d areas were checked and everything they need is here.": "Đã kiểm tra %1$d khu vực và mọi thứ cần thiết đều có tại đây.",
        "%1$d comments and replies loaded": "Đã tải %1$d bình luận và câu trả lời",
        "%1$d fields overridden": "Đã ghi đè %1$d trường",
        "%1$d fields will be overridden when requested": "%1$d trường sẽ bị ghi đè khi được yêu cầu",
        "%1$d gates selected": "Đã chọn %1$d cổng",
        "%1$d minutes left": "Còn lại %1$d phút",
        "%1$d overrides were turned off because the Lab couldn't confirm their gates are unchanged in TikTok %2$s. Review them before turning them back on.": "%1$d mục ghi đè đã bị tắt vì Phòng thử nghiệm tính năng không thể xác nhận các cổng đó không thay đổi trong TikTok %2$s. Hãy xem lại trước khi bật lại.",
        "%1$d rejected": "%1$d bị từ chối",
        "%1$d results": "%1$d kết quả",
        "%1$d results so far": "%1$d kết quả cho đến nay",
        "%1$d results so far, and more not counted": "%1$d kết quả tính đến thời điểm hiện tại và còn nhiều kết quả chưa được tính",
        "%1$d results, filtered to %2$s": "%1$d kết quả, được lọc theo %2$s",
        "%1$d setting in that file can't be restored here and was skipped": "%1$d cài đặt trong tệp đó không thể khôi phục tại đây và đã bị bỏ qua",
        "%1$d setting wasn't in that file and was left as it is": "%1$d cài đặt không có trong tệp đó và được giữ nguyên",
        "%1$d settings in that file can't be restored here and were skipped": "%1$d cài đặt trong tệp đó không thể khôi phục tại đây và đã bị bỏ qua",
        "%1$d settings weren't in that file and were left as they are": "%1$d cài đặt không có trong tệp đó và được giữ nguyên",
        "%1$d unavailable": "%1$d không khả dụng",
        "%1$s day": "%1$s ngày",
        "%1$s days": "%1$s ngày",
        "%1$s destinations must start with %2$s": "Đích đến %1$s phải bắt đầu bằng %2$s",
        "%1$s entries skipped.": "Đã bỏ qua %1$s mục.",
        "%1$s entry skipped.": "Đã bỏ qua %1$s mục.",
        "%1$s items loaded": "Đã tải %1$s mục",
        "%1$s left": "Còn lại %1$s",
        "%1$s millisecond": "%1$s mili-giây",
        "%1$s milliseconds": "%1$s mili-giây",
        "%1$s min left": "Còn lại %1$s phút",
        "%1$s minute": "%1$s phút",
        "%1$s minutes": "%1$s phút",
        "%1$s minutes left": "Còn lại %1$s phút",
        "%1$s older seen video was dropped to keep the newest %2$s.": "Đã loại bỏ %1$s video đã xem cũ hơn để giữ lại %2$s video mới nhất.",
        "%1$s older seen videos were dropped to keep the newest %2$s.": "Đã loại bỏ %1$s video đã xem cũ hơn để giữ lại %2$s video mới nhất.",
        "%1$s point": "%1$s điểm",
        "%1$s points": "%1$s điểm",
        "%1$s time": "%1$s lần",
        "%1$s video": "%1$s video",
        "%1$s videos": "%1$s video",
        "%1$s video didn't fit in the newest %2$s.": "%1$s video không vừa trong %2$s mới nhất.",
        "%1$s video had a date later than this phone's clock.": "%1$s video có ngày giờ muộn hơn đồng hồ của điện thoại này.",
        "%1$s video was already recorded.": "%1$s video đã được ghi lại trước đó.",
        "%1$s video was older than Forget seen videos after allows.": "%1$s video cũ hơn mức mà Quên các video đã xem sau cho phép.",
        "%1$s videos didn't fit in the newest %2$s.": "%1$s video không vừa trong %2$s mới nhất.",
        "%1$s videos had dates later than this phone's clock.": "%1$s video có ngày giờ muộn hơn đồng hồ của điện thoại này.",
        "%1$s videos left": "Còn lại %1$s video",
        "%1$s videos were already recorded.": "%1$s video đã được ghi lại trước đó.",
        "%1$s videos were older than Forget seen videos after allows.": "%1$s video cũ hơn mức mà Quên các video đã xem sau cho phép.",
        "%1$s videos were recorded before each account kept its own list. They hide nothing until you add them here.": "%1$s video đã được ghi lại trước khi mỗi tài khoản lưu danh sách riêng. Chúng sẽ không ẩn video nào cho đến khi bạn thêm chúng vào đây.",
        "%1$s watched video added.": "Đã thêm %1$s video đã xem.",
        "%1$s watched videos added.": "Đã thêm %1$s video đã xem.",
        "1 result": "1 kết quả",
        "1 result so far": "1 kết quả cho đến nay",
        "1 minute": "1 phút",
        "1 day": "1 ngày",
        "1 video": "1 video",
        "Hide the caption": "Ẩn chú thích",
        "Hide the description under the creator's name on the feed.": "Ẩn phần mô tả dưới tên nhà sáng tạo trên bảng tin.",
        "Current: %1$s": "Hiện tại: %1$s",
    }
    translations.update(POLISHED_EXACT)

    # Specific summaries that must contain specific titles verbatim:
    title_summary_pairs = [
        ("Search within comments", "Tìm kiếm trong bình luận",
         "Adds Export CSV and Export JSON buttons under the search box, so Search within comments must be on. They save the comments and replies loaded so far to a file you pick. Open the reply threads you want first.",
         "Thêm các nút Xuất CSV và Xuất JSON dưới ô tìm kiếm, vì vậy Tìm kiếm trong bình luận phải được bật. Các nút này lưu các bình luận và phản hồi đã tải vào tệp bạn chọn. Hãy mở trước các chuỗi phản hồi bạn muốn."),
        ("Download original photos", "Tải ảnh gốc",
         "Use {creator}, {date}, {video_id} and {index} to build the name. {index} numbers the photos of a slideshow saved with Download original photos. TikTok's own save button numbers files by folder instead. The file ending is added for you.",
         "Sử dụng {creator}, {date}, {video_id} và {index} để tạo tên. {index} đánh số các ảnh trong trình chiếu được lưu bằng Tải ảnh gốc. Nút lưu mặc định của TikTok đánh số tệp theo thư mục. Đuôi tệp sẽ được tự động thêm vào."),
        ("Save the sound as well", "Lưu cả âm thanh",
         "Leave the sound out of the saved video. Save the sound as well still writes the sound file beside it if you want both.",
         "Bỏ âm thanh khỏi video đã lưu. Lưu cả âm thanh vẫn ghi tệp âm thanh bên cạnh nếu bạn muốn cả hai."),
        ("Save the cover as well", "Lưu cả ảnh bìa",
         "Adds Save cover next to Download when you share a video, so you save the cover only when you want it. While this is on, Download saves just the video, even with Save the cover as well on.",
         "Thêm nút Lưu ảnh bìa bên cạnh Tải xuống khi bạn chia sẻ video, giúp bạn chỉ lưu ảnh bìa khi muốn. Khi bật tùy chọn này, Tải xuống chỉ lưu video, ngay cả khi Lưu cả ảnh bìa đang bật."),
        ("Check for already-saved videos", "Kiểm tra video đã lưu",
         "Puts a ✓ by videos you saved with Buwin, on profile grids and next to the time in the feed. Works only while Check for already-saved videos is on. Photo posts aren't marked.",
         "Hiển thị dấu ✓ cạnh các video bạn đã lưu bằng Buwin, trên lưới hồ sơ và cạnh thời gian trong bảng tin. Chỉ hoạt động khi bật Kiểm tra video đã lưu. Bài đăng ảnh không được đánh dấu."),
        ("Use system font", "Sử dụng phông chữ hệ thống",
         "Shows TikTok's text in the font file you import below. While both are on, this one wins over Use system font. If the file is removed or can't be read, the text goes back to what Use system font shows.",
         "Hiển thị văn bản TikTok theo tệp phông chữ bạn nhập bên dưới. Khi bật cả hai, tùy chọn này được ưu tiên hơn Sử dụng phông chữ hệ thống. Nếu tệp bị xóa hoặc không đọc được, văn bản sẽ quay về phông chữ do Sử dụng phông chữ hệ thống hiển thị."),
        ("Hide the status bar in LIVE rooms", "Ẩn thanh trạng thái trong phòng LIVE",
         "Let a LIVE reach the top edge with the clock and status icons over it, instead of a black strip. Hide the status bar in LIVE rooms takes them away instead.",
         "Cho phép phòng LIVE tràn lên cạnh trên cùng với đồng hồ và biểu tượng trạng thái hiển thị bên trên, thay vì dải đen. Ẩn thanh trạng thái trong phòng LIVE sẽ loại bỏ chúng."),
        ("Hide location labels", "Ẩn nhãn vị trí",
         "Hide feed videos with place badges, even when they aren't paid ads. To keep the video and hide only its badge, use Hide location labels in Feed screen.",
         "Ẩn các video trên bảng tin có huy hiệu địa điểm, ngay cả khi không phải quảng cáo trả phí. Để giữ video và chỉ ẩn huy hiệu của nó, hãy dùng Ẩn nhãn vị trí trong Giao diện bảng tin."),
        ("Spin the music disc", "Xoay đĩa nhạc",
         "Hold the music disc at the bottom of the right column still, even where TikTok turns it. This overrides Spin the music disc.",
         "Giữ đĩa nhạc ở dưới cùng cột bên phải đứng yên, ngay cả khi TikTok xoay nó. Tùy chọn này ghi đè Xoay đĩa nhạc."),
        ("Leave when TikTok says time is up", "Thoát khi TikTok thông báo hết giờ",
         "Stops the bedtime wind-down, the breathing exercise and the daily limit screen TikTok puts over the feed. Works only on accounts TikTok knows belong to adults. Teen accounts, and ones a parent links with Family Pairing, keep them. With Leave when TikTok says time is up on, the daily limit screen still shows so that switch can work.",
         "Chặn màn hình thư giãn trước khi ngủ, bài tập thở và màn hình giới hạn hàng ngày mà TikTok hiển thị trên bảng tin. Chỉ hoạt động trên tài khoản người lớn. Tài khoản thanh thiếu niên và tài khoản được phụ huynh liên kết qua Gia đình thông minh vẫn giữ nguyên. Khi bật Thoát khi TikTok thông báo hết giờ, màn hình giới hạn hàng ngày vẫn hiển thị để công tắc đó có thể hoạt động."),
        ("See-through bottom bar", "Thanh điều hướng dưới trong suốt",
         "Show the main feed's tabs on a rounded, see-through dark pill that floats over the video. Needs See-through bottom bar.",
         "Hiển thị các tab của bảng tin chính trên thanh viên thuốc bo tròn, trong suốt nổi trên video. Cần bật Thanh điều hướng dưới trong suốt."),
        ("Seek from the edges", "Tua video từ mép màn hình",
         "A long press on Comment plays the video at the hold speed instead of opening the emoji row. It needs Long press on TikTok default and Seek from the edges off.",
         "Nhấn giữ vào nút Bình luận sẽ phát video ở tốc độ nhấn giữ thay vì mở hàng biểu tượng cảm xúc. Cần bật Nhấn giữ mặc định của TikTok và tắt Tua video từ mép màn hình."),
        ("Seek from the edges", "Tua video từ mép màn hình",
         "A long press on Share plays the video at the hold speed instead of opening the quick share row. It needs Long press on TikTok default and Seek from the edges off.",
         "Nhấn giữ vào nút Chia sẻ sẽ phát video ở tốc độ nhấn giữ thay vì mở hàng chia sẻ nhanh. Cần bật Nhấn giữ mặc định của TikTok và tắt Tua video từ mép màn hình."),
        ("Seek from the edges", "Tua video từ mép màn hình",
         "A long press on Favorites plays the video at the hold speed instead of offering a new collection. It needs Long press on TikTok default and Seek from the edges off.",
         "Nhấn giữ vào nút Yêu thích sẽ phát video ở tốc độ nhấn giữ thay vì đề xuất bộ sưu tập mới. Cần bật Nhấn giữ mặc định của TikTok và tắt Tua video từ mép màn hình."),
        ("Blocked creators", "Nhà sáng tạo bị chặn",
         "Hides rooms in the LIVE feed you swipe through, using the rules below. Blocked creators, Creators hidden on this phone and Creator exceptions apply here too, and Blocked caption words are checked against each LIVE's title. The first room the LIVE button opens isn't filtered. If every room would be hidden, one gets through so the feed keeps loading.",
         "Ẩn các phòng trong bảng tin LIVE khi bạn lướt qua theo các quy tắc bên dưới. Nhà sáng tạo bị chặn, Nhà sáng tạo bị ẩn trên điện thoại này và Ngoại lệ nhà sáng tạo cũng áp dụng tại đây, đồng thời Từ khóa chú thích bị chặn được đối chiếu với tiêu đề của từng phòng LIVE. Phòng đầu tiên mở bằng nút LIVE không bị lọc. Nếu mọi phòng đều bị ẩn, một phòng sẽ được hiển thị để bảng tin tiếp tục tải."),
    ]

    for title, trans_title, summary, trans_summary in title_summary_pairs:
        translations[title] = trans_title
        translations[summary] = trans_summary

    # Write vi.csv
    with open(VI_CSV, "w", encoding="utf-8", newline="\n") as f:
        writer = csv.writer(f, lineterminator="\n")
        writer.writerow(["source", "target"])
        for key in en_keys:
            target = translations[key]
            writer.writerow([key, target])

    print(f"Successfully wrote {len(translations)} entries to {VI_CSV}")

if __name__ == "__main__":
    main()
