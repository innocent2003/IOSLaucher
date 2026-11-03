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

## Lưu ý khi thêm layout

`app/build.gradle.kts` tạo thư mục tài nguyên build từ `src/main/res` và chỉ đưa vào các layout được liệt kê trong task `prepareActiveResources`. Khi thêm một file XML layout mới, cần thêm tên file vào danh sách `include(...)` của task này để layout được đóng gói vào ứng dụng.

## Kiểm thử

Các unit test hiện có nằm trong `app/src/test`. Có thể chạy toàn bộ bằng `:app:testDebugUnitTest`; build debug bằng `:app:assembleDebug`.
