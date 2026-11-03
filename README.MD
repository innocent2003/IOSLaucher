# Oslaucher

Oslaucher là ứng dụng launcher Android viết bằng Kotlin. Ứng dụng hiển thị màn hình chính, lưới ứng dụng và một số màn hình cài đặt để người dùng tùy chỉnh launcher.

## Yêu cầu

- Android Studio có hỗ trợ Android Gradle Plugin 8.12.1 và Kotlin 2.3.0.
- JDK 11.
- Android SDK Platform 36.
- Thiết bị hoặc emulator chạy Android 7.0 (API 24) trở lên.

## Build và chạy

Trên Windows:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:installDebug
```

Trên macOS/Linux:

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

Chạy unit test:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Sau khi cài đặt, mở Oslaucher. Để sử dụng đầy đủ các tùy chỉnh launcher, chọn Oslaucher làm ứng dụng Home mặc định trong Android.

## Luồng hoạt động

### 1. Khởi tạo launcher

`MainActivity` là Activity điều phối launcher và nhận cả hai cách khởi chạy:

- Mở ứng dụng thông thường: tải dữ liệu ứng dụng rồi hiển thị màn hình cài đặt.
- Mở với vai trò Home: tải dữ liệu ứng dụng rồi hiển thị màn hình chính.

Trong `onCreate`, Activity đọc các tùy chỉnh đã lưu, tạo `screenContainer`, khôi phục thứ tự ứng dụng, folder, dock và widget. Danh sách ứng dụng được tải ở luồng nền; sau khi tải xong, launcher mới chuyển khỏi màn hình loading.

### 2. Tải danh sách ứng dụng

Luồng tải ứng dụng:

1. `InstalledAppRepository` yêu cầu `InstalledAppUtils` truy vấn các Activity có `ACTION_MAIN` và `CATEGORY_LAUNCHER`.
2. Với mỗi ứng dụng, lấy package name, nhãn và icon từ `PackageManager`; danh sách được sắp xếp theo tên.
3. Danh sách tên/package được lưu vào SQLite để làm cache. Icon được lấy lại từ `PackageManager`, không lưu trong database.
4. `MainActivity` dùng danh sách này để dựng lưới, dock, folder và các màn hình chọn ứng dụng.

### 3. Điều hướng màn hình

`Screen` mô tả các trạng thái chính: loading, settings, home, home thứ hai và apps. `MainActivity.showScreen()` thay màn hình trong `screenContainer`.

- `HomeFragment` hiển thị màn hình chính.
- `HomeSecondFragment` hiển thị trang chính phụ.
- Màn hình apps được dựng trong `MainActivity`, với các trang lưới ứng dụng.
- Vuốt ngang dùng các transition trong `MainActivity` và `PageTransitionAnimator`.
- Các màn hình cấu hình riêng được mở bằng Activity riêng; khi quay lại, `MainActivity` cập nhật trạng thái cần thiết.

### 4. Dựng lưới ứng dụng

Khi hiển thị màn hình apps, `MainActivity` lọc ứng dụng đã ẩn, ứng dụng nằm trong folder và ứng dụng được đặt ở dock. Các ứng dụng còn lại được sắp theo thứ tự đã lưu rồi chia thành từng trang.

- Mỗi trang dùng `GridView` 4 cột.
- Số hàng cấu hình được chọn trong `ScreenGridActivity` (5, 6 hoặc 7); mỗi trang có tối đa `4 × số hàng` ô.
- Nếu có widget trên trang, các vị trí bị widget chiếm được để trống khi sắp ứng dụng.
- `AppAdapter` tạo từng ô từ `item_app.xml`: icon, tên ứng dụng và trạng thái chọn.
- Chạm ứng dụng để mở; nhấn giữ để mở menu thao tác hoặc kéo thả/sắp xếp tùy chế độ.

### 5. Cấu hình và lưu dữ liệu

Các thiết lập đơn giản được lưu bằng `SharedPreferences`, ví dụ chế độ sáng/tối, lưới, hiệu ứng chuyển trang, wallpaper, icon tùy chỉnh và danh sách ứng dụng ẩn.

`LauncherLayoutRepository` quản lý dữ liệu bố cục launcher:

- Thứ tự ứng dụng trong lưới.
- Folder và các ứng dụng thuộc folder.
- Vị trí ứng dụng/folder trong dock và các ô dock trống.
- Vị trí widget trên các trang.

Một số dữ liệu cấu trúc được tuần tự hóa thành JSON trong `SharedPreferences`. `AppDatabase` (SQLite) chỉ lưu cache tên và package ứng dụng.

### 6. Wallpaper, icon và widget

- Wallpaper người dùng chọn được lưu dưới dạng resource hoặc URI; launcher đọc wallpaper và áp dụng lên các màn hình launcher.
- Icon ứng dụng mặc định lấy từ `PackageManager`. Màn hình đổi icon cho phép người dùng chọn ảnh trên thiết bị; URI ảnh được lưu để dùng thay icon mặc định.
- Widget hệ thống được quản lý bằng `AppWidgetHost`/`AppWidgetManager`; các widget nội bộ (đồng hồ, pin, lịch, thời tiết) được dựng bằng các View của ứng dụng.
- Dữ liệu thời tiết được tải từ Open-Meteo thông qua `WeatherForecastRepository`. Tọa độ hiện được cấu hình cố định trong URL của repository.

## Cấu trúc mã nguồn

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/example/oslaucher/
│   ├── adapters/       # Adapter cho lưới và danh sách ứng dụng
│   ├── db/             # SQLite cache ứng dụng
│   ├── models/         # Model và logic thứ tự lưới
│   ├── repositories/   # Tải ứng dụng, lưu layout, tải dự báo thời tiết
│   ├── ui/             # MainActivity, màn hình cài đặt, fragment và widget
│   └── utils/          # Cấu hình, preferences, icon, wallpaper
├── res/                # Layout, drawable, string và tài nguyên Android
└── test/               # Unit test
```

## Trách nhiệm từng file Kotlin

Các mô tả dưới đây tập trung vào vai trò của từng file trong luồng hiện tại; phần lớn giao diện được tạo từ XML trong `res/layout`, còn các Activity/Fragment kết nối view với logic.

### Điều phối và màn hình (`ui/`)

| File | Trách nhiệm |
|---|---|
| [`MainActivity.kt`](./app/src/main/java/com/example/oslaucher/ui/MainActivity.kt) | Điều phối launcher: khởi tạo và chuyển màn hình, tải ứng dụng, dựng các trang lưới và dock, xử lý vuốt, kéo thả, chọn app, folder, widget, menu ứng dụng và các kết quả trả về từ màn hình cài đặt. |
| [`AppEffectsActivity.kt`](./app/src/main/java/com/example/oslaucher/ui/AppEffectsActivity.kt) | Hiển thị và lưu các công tắc hiệu ứng khi mở/đóng ứng dụng và tiếp tục hiệu ứng. |
| [`AppearanceActivity.kt`](./app/src/main/java/com/example/oslaucher/ui/AppearanceActivity.kt) | Cho chọn sáng/tối hoặc theo hệ thống, dựng bản xem trước và áp dụng chế độ giao diện bằng `OslauncherApp`. |
| [`ChangeAppIconActivity.kt`](./app/src/main/java/com/example/oslaucher/ui/ChangeAppIconActivity.kt) | Tải danh sách app, hiển thị icon/tên, cho chọn ảnh làm icon tùy chỉnh hoặc xóa tùy chỉnh để trở về icon gốc. |
| [`GeneralSettingsActivity.kt`](./app/src/main/java/com/example/oslaucher/ui/GeneralSettingsActivity.kt) | Hiển thị các công tắc cài đặt chung và thanh chỉnh kích thước icon; lưu trạng thái vào `SharedPreferences`. |
| [`HiddenAppsActivity.kt`](./app/src/main/java/com/example/oslaucher/ui/HiddenAppsActivity.kt) | Tải các app bị ẩn, hiển thị danh sách và cho bỏ ẩn từng app. |
| [`LanguageActivity.kt`](./app/src/main/java/com/example/oslaucher/ui/LanguageActivity.kt) | Hiển thị danh sách ngôn ngữ hỗ trợ và áp dụng lựa chọn bằng AppCompat locale. |
| [`PageTransitionActivity.kt`](./app/src/main/java/com/example/oslaucher/ui/PageTransitionActivity.kt) | Cho chọn kiểu chuyển trang, lưu lựa chọn và chạy hoạt ảnh minh họa. |
| [`PageTransitionAnimator.kt`](./app/src/main/java/com/example/oslaucher/ui/PageTransitionAnimator.kt) | Tính và chạy các kiểu chuyển cảnh giữa hai view: mặc định, xoay, lật, zoom và fade; cung cấp hàm reset trạng thái view sau animation. |
| [`ScreenGridActivity.kt`](./app/src/main/java/com/example/oslaucher/ui/ScreenGridActivity.kt) | Cho chọn số hàng của lưới ứng dụng (5–7) và lưu lựa chọn. |
| [`ScreenGridPreviewView.kt`](./app/src/main/java/com/example/oslaucher/ui/ScreenGridPreviewView.kt) | Tự vẽ hình xem trước điện thoại, lưới 4 cột theo số hàng đã chọn, dock và thanh cuộn minh họa. |
| [`WallpaperActivity.kt`](./app/src/main/java/com/example/oslaucher/ui/WallpaperActivity.kt) | Hiển thị wallpaper mẫu/ảnh người dùng chọn, xử lý vuốt preview, lưu lựa chọn và áp dụng wallpaper lên hệ thống. |
| [`WeatherSettingsActivity.kt`](./app/src/main/java/com/example/oslaucher/ui/WeatherSettingsActivity.kt) | Cho chọn đơn vị nhiệt độ và chu kỳ làm mới thời tiết; trả lại trang app liên quan để launcher khôi phục vị trí. |

### Màn hình chính (`ui/fragments/`)

| File | Trách nhiệm |
|---|---|
| [`HomeScreenActions.kt`](./app/src/main/java/com/example/oslaucher/ui/fragments/HomeScreenActions.kt) | Hợp đồng callback giữa fragment màn hình chính và `MainActivity`: mở apps/settings, tải danh sách app, mở app và folder. |
| [`HomeScreenFragment.kt`](./app/src/main/java/com/example/oslaucher/ui/fragments/HomeScreenFragment.kt) | Logic dùng chung cho các màn hình chính: áp dụng wallpaper, cập nhật giờ/ngày/pin/lịch/thời tiết, đăng ký broadcast cập nhật và mở lịch/cài đặt. |
| [`HomeFragment.kt`](./app/src/main/java/com/example/oslaucher/ui/fragments/HomeFragment.kt) | Fragment màn hình chính đầu tiên; chọn layout `screen_home` và dùng hành vi chung từ `HomeScreenFragment`. |
| [`HomeSecondFragment.kt`](./app/src/main/java/com/example/oslaucher/ui/fragments/HomeSecondFragment.kt) | Trang chính thứ hai; tải app rồi gom app thành các nhóm/folder gợi ý và hiển thị các folder trên lưới. |

### Widget nội bộ (`ui/widgets/`)

| File | Trách nhiệm |
|---|---|
| [`LauncherWidgetFactory.kt`](./app/src/main/java/com/example/oslaucher/ui/widgets/LauncherWidgetFactory.kt) | Tạo view cho các widget nội bộ (thời tiết, lịch, pin); widget đồng hồ được tạo bằng `AnalogClockView`. |
| [`AnalogClockView.kt`](./app/src/main/java/com/example/oslaucher/ui/widgets/AnalogClockView.kt) | Tự vẽ mặt đồng hồ analog và các kim dựa theo thời gian hiện tại. |
| [`BatteryRingView.kt`](./app/src/main/java/com/example/oslaucher/ui/widgets/BatteryRingView.kt) | Tự vẽ vòng biểu thị phần trăm pin. |

### Adapter (`adapters/`)

| File | Trách nhiệm |
|---|---|
| [`AppAdapter.kt`](./app/src/main/java/com/example/oslaucher/adapters/AppAdapter.kt) | Cấp dữ liệu cho `GridView` và dựng từng ô icon/tên; xử lý ô trống, trạng thái chọn và callback chạm/nhấn giữ. |
| [`AlphabeticalAppAdapter.kt`](./app/src/main/java/com/example/oslaucher/adapters/AlphabeticalAppAdapter.kt) | Cấp dữ liệu cho danh sách app theo alphabet; lọc tên, nhảy đến chữ cái, hiển thị tiêu đề nhóm và callback mở/menu app. |

### Dữ liệu (`models/`)

| File | Trách nhiệm |
|---|---|
| [`AppInfo.kt`](./app/src/main/java/com/example/oslaucher/models/AppInfo.kt) | Thông tin app dùng trong giao diện: package name, tên và drawable icon. |
| [`CachedApp.kt`](./app/src/main/java/com/example/oslaucher/models/CachedApp.kt) | Bản ghi cache tên và package app trong SQLite. |
| [`AppFolder.kt`](./app/src/main/java/com/example/oslaucher/models/AppFolder.kt) | Dữ liệu folder: ID, tên và danh sách package thành viên. |
| [`AppGridOrder.kt`](./app/src/main/java/com/example/oslaucher/models/AppGridOrder.kt) | Logic thuần để khôi phục thứ tự/vị trí trống của lưới và tính kết quả khi di chuyển app. |
| [`AppDragCandidate.kt`](./app/src/main/java/com/example/oslaucher/models/AppDragCandidate.kt) | Trạng thái app và view nguồn khi bắt đầu thao tác nhấn giữ/kéo. |
| [`AppPageSwipeTransition.kt`](./app/src/main/java/com/example/oslaucher/models/AppPageSwipeTransition.kt) | Trạng thái chuyển vuốt giữa hai trang app, gồm view, hướng, chiều rộng và tiến độ. |
| [`HomeSwipeTransition.kt`](./app/src/main/java/com/example/oslaucher/models/HomeSwipeTransition.kt) | Trạng thái chuyển vuốt giữa các màn hình Home và Apps, gồm fragment/view đi và đến. |
| [`HomeWidgetPlacement.kt`](./app/src/main/java/com/example/oslaucher/models/HomeWidgetPlacement.kt) | Vị trí widget: ID widget hệ thống hoặc loại widget nội bộ, trang và tọa độ tùy chọn; cũng định nghĩa các loại widget nội bộ. |
| [`Weather.kt`](./app/src/main/java/com/example/oslaucher/models/Weather.kt) | Kết quả thời tiết đã chuyển thành dữ liệu trình bày cho widget. |
| [`EffectOption.kt`](./app/src/main/java/com/example/oslaucher/models/EffectOption.kt) | Cấu hình binding của một công tắc hiệu ứng: ID hàng, ID switch, khóa preference và mặc định. |
| [`SwitchOption.kt`](./app/src/main/java/com/example/oslaucher/models/SwitchOption.kt) | Cấu hình binding của một công tắc trong màn hình cài đặt chung. |

### Repository và database (`repositories/`, `db/`)

| File | Trách nhiệm |
|---|---|
| [`InstalledAppRepository.kt`](./app/src/main/java/com/example/oslaucher/repositories/InstalledAppRepository.kt) | Nạp app từ PackageManager qua `InstalledAppUtils`, cập nhật SQLite cache; nếu truy vấn gặp lỗi thì thử khôi phục các app cache còn tồn tại. |
| [`LauncherLayoutRepository.kt`](./app/src/main/java/com/example/oslaucher/repositories/LauncherLayoutRepository.kt) | Đọc/ghi thứ tự lưới, widget, folder, vị trí dock và các ô dock trống bằng JSON/`SharedPreferences`; ghi log và dùng giá trị rỗng an toàn nếu JSON không hợp lệ. |
| [`WeatherForecastRepository.kt`](./app/src/main/java/com/example/oslaucher/repositories/WeatherForecastRepository.kt) | Gọi Open-Meteo, phân tích JSON thời tiết hiện tại/theo giờ/theo ngày, chuyển mã thời tiết thành mô tả và icon; đóng kết nối sau khi tải. |
| [`AppDatabase.kt`](./app/src/main/java/com/example/oslaucher/db/AppDatabase.kt) | Tạo bảng SQLite cache app, thay toàn bộ dữ liệu trong transaction và đọc cache theo tên. |

### Tiện ích và cấu hình (`utils/`)

| File | Trách nhiệm |
|---|---|
| [`InstalledAppUtils.kt`](./app/src/main/java/com/example/oslaucher/utils/InstalledAppUtils.kt) | Truy vấn app có launcher activity, lấy tên/icon từ PackageManager và sắp theo locale; cung cấp cách dựng lại app cache. |
| [`AppIconUtils.kt`](./app/src/main/java/com/example/oslaucher/utils/AppIconUtils.kt) | Trả icon tùy chỉnh nếu URI còn đọc được, nếu không dùng icon gốc; lưu, kiểm tra và xóa URI icon tùy chỉnh. |
| [`LauncherConfig.kt`](./app/src/main/java/com/example/oslaucher/utils/LauncherConfig.kt) | Kiểm tra Oslaucher có phải Home mặc định không; chọn cấu hình lưới, hiệu ứng và wallpaper theo trạng thái này. |
| [`LauncherWallpaper.kt`](./app/src/main/java/com/example/oslaucher/utils/LauncherWallpaper.kt) | Đọc wallpaper đã lưu hoặc wallpaper hệ thống, crop để áp dụng cho view, lưu resource/URI và phát broadcast báo wallpaper đổi. |
| [`CenterCropDrawable.kt`](./app/src/main/java/com/example/oslaucher/utils/CenterCropDrawable.kt) | Bọc drawable và vẽ phủ kín bounds theo kiểu center-crop, giữ tỉ lệ ảnh. |
| [`HiddenAppsPreferences.kt`](./app/src/main/java/com/example/oslaucher/utils/HiddenAppsPreferences.kt) | Đọc, lưu, ẩn và bỏ ẩn package ứng dụng bằng `SharedPreferences`. |
| [`WeatherWidgetPreferences.kt`](./app/src/main/java/com/example/oslaucher/utils/WeatherWidgetPreferences.kt) | Lưu đơn vị nhiệt độ, chu kỳ làm mới và thời điểm refresh cuối; xác định khi nào cần tải lại thời tiết. |
| [`Screen.kt`](./app/src/main/java/com/example/oslaucher/utils/Screen.kt) | Enum các trạng thái màn hình được `MainActivity` điều phối. |

### Khởi tạo ứng dụng

| File | Trách nhiệm |
|---|---|
| [`OslauncherApp.kt`](./app/src/main/java/com/example/oslaucher/OslauncherApp.kt) | Application class; đọc preference giao diện lúc khởi động và đặt chế độ ngày/đêm cho AppCompat. |

## Cấu hình, tài nguyên và kiểm thử

- [`settings.gradle.kts`](./settings.gradle.kts): khai báo repository plugin/phụ thuộc và module `:app`.
- [`build.gradle.kts`](./build.gradle.kts): khai báo plugin Gradle cấp project.
- [`app/build.gradle.kts`](./app/build.gradle.kts): cấu hình Android/Kotlin, SDK, dependencies và task `prepareActiveResources`.
- [`gradle/libs.versions.toml`](./gradle/libs.versions.toml): quản lý tập trung phiên bản plugin và thư viện.
- [`AndroidManifest.xml`](./app/src/main/AndroidManifest.xml): khai báo quyền, package visibility và các Activity; `MainActivity` có intent filter cho launcher thông thường và vai trò Home.
- `app/src/main/res/layout/`: định nghĩa cấu trúc giao diện. Các file layout được đóng gói phải nằm trong danh sách `include(...)` của `prepareActiveResources`.
- `app/src/main/res/values*/`: chuỗi dịch, màu, theme và các giá trị tài nguyên theo cấu hình/ngôn ngữ.
- [`AppGridOrderTest.kt`](./app/src/test/java/com/example/oslaucher/models/AppGridOrderTest.kt): kiểm tra giữ thứ tự/vị trí trống, di chuyển app vào ô trống, chèn app trước app khác và trường hợp không có di chuyển.
- [`ExampleUnitTest.kt`](./app/src/test/java/com/example/oslaucher/ExampleUnitTest.kt): unit test mẫu của module.

## Lưu ý khi thêm layout

`app/build.gradle.kts` tạo thư mục tài nguyên build từ `src/main/res` và chỉ đưa vào các layout được liệt kê trong task `prepareActiveResources`. Khi thêm một file XML layout mới, cần thêm tên file vào danh sách `include(...)` của task này để layout được đóng gói vào ứng dụng.

## Kiểm thử

Các unit test hiện có nằm trong `app/src/test`. Có thể chạy toàn bộ bằng `:app:testDebugUnitTest`; build debug bằng `:app:assembleDebug`.
