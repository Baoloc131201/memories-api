# Kỉ Niệm – Ghi chú dự án

Website lưu ảnh kỉ niệm, có đăng ký / đăng nhập / đăng xuất. Gồm 2 source code:

| Phần | Thư mục | Công nghệ |
|---|---|---|
| Backend (API) | `D:\My-Project-BE` | Java 21, Spring Boot 3.3.5, Spring Security, JWT (jjwt 0.12.6), Spring Data JPA, PostgreSQL (Neon) |
| Frontend | `D:\My-Project-FE` | React 18, Vite 5, Framer Motion 11, React Router 6 |

---

## 1. Cách chạy

### Backend
1. Tạo file `D:\My-Project-BE\.env` (copy từ `.env.example`):
   ```properties
   DB_URL=jdbc:postgresql://<host>/<db>?sslmode=require
   DB_USERNAME=...
   DB_PASSWORD=...
   JWT_SECRET=<chuỗi ngẫu nhiên >= 32 ký tự>
   # Tuỳ chọn: đăng nhập bằng Google / Facebook (để trống thì nút bị ẩn), xem mục 1.1
   GOOGLE_CLIENT_ID=
   FACEBOOK_APP_ID=
   FACEBOOK_APP_SECRET=
   ```
2. Chạy:
   ```bash
   cd D:\My-Project-BE
   mvn spring-boot:run
   # hoặc: mvn package  rồi  java -jar target/memories-api-1.0.0.jar
   ```
3. API chạy ở `http://localhost:8081`. Bảng trong DB được tự tạo (`ddl-auto=update`).

### 1.1 Lấy key đăng nhập Google / Facebook

**Google**
1. Vào https://console.cloud.google.com → tạo project → **APIs & Services → OAuth consent screen**: chọn *External*, điền tên app và email.
2. **Credentials → Create credentials → OAuth client ID** → loại *Web application*.
3. **Authorized JavaScript origins**: thêm `http://localhost:5173` và `http://localhost` (khi deploy thì thêm domain thật). Không cần redirect URI.
4. Copy **Client ID** vào `GOOGLE_CLIENT_ID`. Không cần client secret.

**Facebook**
1. Vào https://developers.facebook.com/apps → **Create app** → chọn use case *Authenticate and request data from users with Facebook Login*.
2. Trong **Use cases → Facebook Login → Customize**: bật quyền `email`.
3. **App settings → Basic**: copy **App ID** vào `FACEBOOK_APP_ID` và **App secret** vào `FACEBOOK_APP_SECRET` (bấm *Show* và nhập lại mật khẩu Facebook để xem secret).
   - Ô **App domains** **không nhận `localhost`** (báo lỗi *"must contain the top-level domain"*), nên để trống khi chạy ở máy. Khi deploy thì điền domain thật, ví dụ `kiniem.com`.
   - Cuối trang: **+ Add platform → Website**, điền **Site URL** `http://localhost:5173/`.
4. **Facebook Login → Settings**: bật **Login with the JavaScript SDK** = *Yes*.
5. Facebook chỉ cho phép đăng nhập qua `http://localhost`. Khi deploy bắt buộc dùng HTTPS.

#### Chế độ Development và Live (vì sao tài khoản khác không đăng nhập được)
App mới tạo ở chế độ **Development**. Ở chế độ này **chỉ tài khoản có vai trò trong app** (admin, developer, tester) đăng nhập được. Tài khoản khác bị Facebook chặn ngay ở popup đăng nhập. Đây là giới hạn của Facebook, không phải lỗi code.

**Cách 1: Thêm tester** (khi chỉ vài người dùng thử)
1. **App roles → Roles → Add People**, chọn vai trò **Testers**, nhập tên hoặc Facebook ID.
2. Người được mời chấp nhận lời mời tại https://developers.facebook.com/requests. Nếu chưa có tài khoản developer thì phải đăng ký trước (miễn phí).

**Cách 2: Chuyển app sang Live** (để ai cũng đăng nhập được)
1. Ở **App settings → Basic**, điền đủ:
   - **Privacy Policy URL**: bắt buộc, phải là link công khai (link `localhost` không được chấp nhận).
   - **User data deletion**: link hoặc trang hướng dẫn xoá dữ liệu.
   - **App icon** và **Category**.
2. Bật **App Mode** ở thanh trên cùng từ *Development* sang **Live**.
3. Vào **Use cases → Facebook Login → Permissions**, kiểm tra `public_profile` và `email` đã có **Advanced access**. Hai quyền cơ bản này thường không cần App Review.

Khi web còn chạy ở localhost, có thể tạo trang Privacy Policy tạm bằng Google Sites, GitHub Pages hoặc trang tạo privacy policy miễn phí. Khi deploy lên domain thật thì đổi sang trang của web.

Sau khi sửa `.env` thì khởi động lại BE. FE tự hiện nút dựa theo `GET /api/auth/providers`.

### Frontend
```bash
cd D:\My-Project-FE
npm install
npm run dev     # http://localhost:5173
npm run build   # build ra thư mục dist/
```
Vite proxy mọi request `/api/*` sang `http://localhost:8081` (xem `vite.config.js`), nên FE và API chạy cùng origin, cookie đăng nhập được gửi kèm tự động.

---

## 2. Backend – API

### 2.1 Xác thực (access token + refresh token, đều trong cookie HttpOnly)
Khi register/login thành công, server đặt 2 cookie:

| Cookie | Nội dung | Path | Sống |
|---|---|---|---|
| `memories_auth` | Access token (JWT) | `/api` | **15 phút** (`app.jwt.access-expiration-ms`) |
| `memories_refresh` | Refresh token (chuỗi ngẫu nhiên 32 byte) | `/api/auth` | **7 ngày** (`app.jwt.refresh-expiration-ms`), tính từ lần refresh gần nhất |

- **Token không nằm trong body response** và JavaScript không đọc được cookie, nên script XSS không lấy cắp được. Cả hai cookie đều `SameSite=Strict`.
- Refresh token chỉ được gửi tới `/api/auth/*`, không lộ ra ở các API khác.
- Khi access token hết hạn, gọi `POST /api/auth/refresh` để nhận cặp token mới.
- **Rotation**: mỗi lần refresh, token cũ bị đánh dấu đã dùng và token mới được cấp. DB chỉ lưu **SHA-256** của refresh token (bảng `refresh_tokens`), nên lộ DB cũng không dùng được token.
- **Phát hiện dùng lại**: token cũ bị dùng lại **sau 30 giây** thì coi như bị lộ, cả phiên đăng nhập (cùng `family_id`) bị thu hồi và phải đăng nhập lại. Dùng lại trong 30 giây (nhiều tab refresh cùng lúc) chỉ trả `401`, không thu hồi.
- **Đăng nhập bằng Google/Facebook** (xem 2.1.1).
- **Logout** lưu `jti` của access token vào `revoked_tokens` và xoá toàn bộ refresh token của phiên. Mỗi giờ một job dọn các token đã hết hạn ở cả 2 bảng.
- API không nhận header `Authorization: Bearer`, chỉ nhận cookie. Nếu test bằng Postman, gọi login trước, Postman sẽ tự giữ cookie.

#### 2.1.1 Đăng nhập bằng Google / Facebook
- FE dùng SDK chính thức để lấy token: Google trả về **ID token** (JWT), Facebook trả về **user access token**. FE gửi token này lên BE.
- BE **tự xác minh** token, không tin dữ liệu FE gửi (`SocialTokenVerifier`):
  - **Google**: kiểm tra chữ ký bằng khoá công khai của Google (JWKS, được cache), cùng với `iss`, `aud` (phải đúng `GOOGLE_CLIENT_ID`) và `exp`.
  - **Facebook**: gọi `graph.facebook.com/debug_token` để chắc token được cấp cho chính app này, rồi gọi `/me` (kèm `appsecret_proof`) để lấy id, tên và email.
- Lần đầu đăng nhập thì tự tạo user mới:
  - `username` lấy từ phần trước `@` của email (hoặc từ tên), bỏ dấu và thêm 4 số nếu bị trùng.
  - `displayName` là tên trên Google/Facebook.
  - Mật khẩu là chuỗi ngẫu nhiên, nên user này chỉ đăng nhập được bằng Google/Facebook.
- Các lần sau, BE tìm user qua bảng `social_accounts` theo (`provider`, `provider_user_id`).
- **Không tự gộp tài khoản theo email**, để tránh bị chiếm tài khoản. Vì vậy cùng một người dùng Google và Facebook sẽ có 2 user khác nhau.
- Đăng nhập thành công thì đặt cookie access + refresh token giống hệt đăng nhập thường.

### 2.2 Danh sách endpoint

Lỗi luôn có dạng `{ "status": <số>, "message": "<mô tả>" }`.

#### Auth – `/api/auth`

| Method | Path | Cần đăng nhập | Request | Response |
|---|---|---|---|---|
| POST | `/api/auth/register` | Không | JSON `{ username, displayName, password }` | `201` + `Set-Cookie` + `{ id, username, displayName }` |
| POST | `/api/auth/login` | Không | JSON `{ username, password }` | `200` + `Set-Cookie` + `{ id, username, displayName }` |
| POST | `/api/auth/google` | Không | JSON `{ credential }` (Google ID token) | `200` + `Set-Cookie` + `{ id, username, displayName }`. Token sai thì `401`, chưa cấu hình thì `404` |
| POST | `/api/auth/facebook` | Không | JSON `{ accessToken }` (Facebook user access token) | Như trên |
| GET | `/api/auth/providers` | Không | – | `200` + `{ googleClientId, facebookAppId }` (`null` nếu chưa cấu hình) |
| POST | `/api/auth/refresh` | Cookie `memories_refresh` | – | `200` + `Set-Cookie` (cặp token mới) + `{ id, username, displayName }`. Token sai, hết hạn hoặc đã dùng thì `401` |
| POST | `/api/auth/logout` | Không bắt buộc | – | `204` + xoá 2 cookie (thu hồi access token và refresh token của phiên) |
| GET | `/api/auth/me` | Có | – | `200` + `{ id, username, displayName }` |

Ràng buộc khi đăng ký:
- `username`: 3–50 ký tự, chỉ gồm chữ, số, `_` và `.`. Server tự chuyển sang chữ thường và kiểm tra trùng (trùng thì trả `409`).
- `displayName`: bắt buộc, tối đa 100 ký tự.
- `password`: 6–100 ký tự, lưu bằng BCrypt.

Sai tên đăng nhập hoặc mật khẩu thì trả `401` với message `"Sai tên đăng nhập hoặc mật khẩu"`.

#### Kỉ niệm – `/api/memories` (tất cả đều cần đăng nhập)

| Method | Path | Request | Response |
|---|---|---|---|
| GET | `/api/memories` | – | `200` + mảng `MemoryResponse` (mới nhất trước, sắp theo `memoryDate` rồi `createdAt`) |
| POST | `/api/memories` | `multipart/form-data`: `file` (bắt buộc), `title` (bắt buộc), `description`, `category`, `memoryDate` (`yyyy-MM-dd`, mặc định là hôm nay) | `201` + `MemoryResponse` |
| PUT | `/api/memories/{id}` | JSON `{ title, description, category, memoryDate }` | `200` + `MemoryResponse` |
| DELETE | `/api/memories/{id}` | – | `204` (xoá cả file ảnh trên đĩa) |
| GET | `/api/memories/{id}/image` | – | `200` + file ảnh (`Cache-Control: private, max-age=7 ngày`) |

`MemoryResponse`:
```json
{
  "id": 1,
  "title": "Đà Lạt",
  "description": "Ghi chú...",
  "memoryDate": "2025-12-24",
  "category": "Đám cưới",
  "imageUrl": "/api/memories/1/image",
  "createdAt": "2026-10-09T09:50:22Z"
}
```

Quy tắc:
- Mỗi user chỉ xem, sửa và xoá được kỉ niệm của mình. Truy cập kỉ niệm của người khác thì trả `404`.
- Upload chỉ nhận JPG, PNG, GIF, WEBP, tối đa **15MB** (vượt thì trả `413`).
- Ảnh được lưu với tên UUID. Chạy ở máy thì lưu trong thư mục `./uploads` (`STORAGE_TYPE=local`), deploy thì lưu trên **Cloudflare R2** (`STORAGE_TYPE=r2`, bucket private). Ảnh luôn đi qua BE nên muốn xem vẫn phải đăng nhập và là chủ ảnh.
- `title` tối đa 120 ký tự, `description` tối đa 2000 ký tự.
- **Phân loại** (`category`): gửi **tên** phân loại, tối đa 50 ký tự. BE tìm phân loại cùng tên của user (không phân biệt hoa thường), chưa có thì tự tạo. Để trống hoặc `null` = chưa phân loại. Phân loại không còn ảnh nào (sau khi xoá ảnh hoặc đổi phân loại) thì BE tự xoá, nên không cần API riêng cho phân loại.

### 2.3 Database (PostgreSQL – Neon)

| Bảng | Cột chính |
|---|---|
| `users` | `id`, `username` (unique), `display_name`, `password` (BCrypt), `created_at` |
| `memories` | `id`, `title`, `description`, `memory_date`, `file_name`, `content_type`, `user_id` (FK → users), `category_id` (FK → categories, có thể null), `created_at` |
| `categories` | `id`, `name`, `user_id` (FK → users, `ON DELETE CASCADE`), `created_at`. Unique (`user_id`, `name`) |
| `revoked_tokens` | `jti` (PK), `expires_at` |
| `social_accounts` | `id`, `provider` (`GOOGLE`/`FACEBOOK`), `provider_user_id`, `user_id` (FK → users, `ON DELETE CASCADE`), `email`, `created_at`. Unique (`provider`, `provider_user_id`) |
| `refresh_tokens` | `id`, `token_hash` (SHA-256, unique), `family_id` (1 phiên đăng nhập), `user_id` (FK → users, `ON DELETE CASCADE`), `expires_at`, `used_at`, `created_at` |

### 2.4 Cấu hình (`src/main/resources/application.properties`)

| Key | Mặc định | Ý nghĩa |
|---|---|---|
| `spring.config.import` | `optional:file:.env[.properties]` | Đọc biến môi trường từ `.env` |
| `spring.datasource.*` | `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}` | Kết nối PostgreSQL |
| `server.port` | `${PORT:8081}` | Render tự đặt `PORT` |
| `server.forward-headers-strategy` | `framework` | Đọc `X-Forwarded-*` khi đứng sau proxy |
| `app.upload-dir` | `./uploads` | Thư mục lưu ảnh khi `STORAGE_TYPE=local` |
| `app.storage.type` | `${STORAGE_TYPE:local}` | `local` (ổ đĩa) hoặc `r2` (Cloudflare R2) |
| `app.storage.r2.*` | `${R2_ACCOUNT_ID}`, `${R2_ACCESS_KEY_ID}`, `${R2_SECRET_ACCESS_KEY}`, `${R2_BUCKET}` | Thông tin R2 |
| `app.storage.migrate-local` | `${MIGRATE_LOCAL_UPLOADS:false}` | `true` = lúc khởi động đẩy ảnh trong `./uploads` lên R2 (file đã có thì bỏ qua) |
| `app.jwt.secret` | `${JWT_SECRET}` | Khoá ký JWT (≥ 32 ký tự) |
| `app.jwt.access-expiration-ms` | `900000` (15 phút) | Thời hạn access token |
| `app.jwt.refresh-expiration-ms` | `604800000` (7 ngày) | Thời hạn refresh token |
| `app.oauth.google.client-id` | `${GOOGLE_CLIENT_ID:}` | Client ID Google (trống = tắt) |
| `app.oauth.facebook.app-id` | `${FACEBOOK_APP_ID:}` | App ID Facebook (trống = tắt) |
| `app.oauth.facebook.app-secret` | `${FACEBOOK_APP_SECRET:}` | App secret Facebook |
| `app.jwt.cookie-secure` | `${COOKIE_SECURE:false}` | **Đặt `true` khi chạy HTTPS** |
| `app.cors.allowed-origins` | `${CORS_ORIGINS:http://localhost:[*],http://127.0.0.1:[*]}` | Origin được phép (hỗ trợ wildcard cổng). Khi deploy thì đặt domain thật |

### 2.5 Cấu trúc source BE
```
Dockerfile, .dockerignore, render.yaml   # Deploy lên Render
src/main/java/com/memories/
├── MemoriesApplication.java        # Entry point, bật @EnableScheduling
├── config/
│   ├── SecurityConfig.java         # Filter chain, stateless, CORS, BCrypt, UserDetailsService
│   └── GlobalExceptionHandler.java # Trả lỗi dạng { status, message }
├── controller/
│   ├── AuthController.java         # register / login / google / facebook / providers / refresh / logout / me
│   └── MemoryController.java       # CRUD kỉ niệm (kèm phân loại) + trả file ảnh
├── dto/                            # AuthDtos, MemoryResponse, ApiError
├── entity/                         # User, Memory, Category, RevokedToken, RefreshToken, SocialAccount
├── repository/                     # Spring Data JPA repositories
├── storage/
│   ├── ImageStorage.java           # Interface lưu ảnh
│   ├── LocalImageStorage.java      # STORAGE_TYPE=local: thư mục ./uploads
│   ├── R2ImageStorage.java         # STORAGE_TYPE=r2: Cloudflare R2 (AWS S3 SDK)
│   └── LocalToR2Migration.java     # MIGRATE_LOCAL_UPLOADS=true: đẩy ảnh cũ lên R2
├── security/
│   ├── AuthCookies.java            # Tạo, đọc, xoá cookie access/refresh (HttpOnly)
│   ├── JwtService.java             # Tạo và kiểm tra JWT
│   └── JwtAuthFilter.java          # Đọc cookie → xác thực, chặn token đã logout
└── service/
    ├── AuthService.java            # Đăng ký, đăng nhập, refresh, logout, dọn token hết hạn
    ├── RefreshTokenService.java    # Cấp, xoay vòng, thu hồi refresh token
    ├── SocialTokenVerifier.java    # Xác minh token Google / Facebook
    └── MemoryService.java          # Lưu file, CRUD, kiểm tra quyền sở hữu, tạo/dọn phân loại
```

---

## 3. Frontend

### 3.1 Trang & tính năng

| Route | Truy cập | Nội dung |
|---|---|---|
| `/login` | Chỉ khi chưa đăng nhập | Form đăng nhập |
| `/register` | Chỉ khi chưa đăng nhập | Form đăng ký |
| `/` | Phải đăng nhập | Album kỉ niệm |
| `*` | – | Chuyển hướng về `/` |

**Trang Login / Register (`pages/AuthPage.jsx`)**
- Bên trái: logo và 3 tấm polaroid minh hoạ. Bên phải: thẻ form có nền kính mờ.
- Báo lỗi từ server ngay trên form, nút có hiệu ứng loading 3 chấm.
- Có link chuyển qua lại giữa Đăng nhập và Đăng ký.
- Có nút **Tiếp tục với Google** và **Tiếp tục với Facebook** (`components/SocialLogin.jsx`), chỉ hiện khi BE đã cấu hình key. SDK của Google và Facebook chỉ được tải khi cần và chỉ tải một lần.

**Trang Album (`pages/GalleryPage.jsx`)**
- Thanh trên cùng dính khi cuộn, có avatar (chữ cái đầu tên) và nút **Đăng xuất**.
- Lời chào theo giờ trong ngày, kèm số kỉ niệm và số năm.
- Ô tìm kiếm lọc theo tiêu đề và ghi chú.
- **Thanh phân loại** (dưới ô tìm kiếm, chỉ hiện khi đã có phân loại): `Tất cả`, từng phân loại kèm số ảnh, và `Chưa phân loại`. Kết hợp được với ô tìm kiếm. Danh sách phân loại được tính từ chính danh sách ảnh.
- Thẻ ảnh và lightbox hiện nhãn phân loại.
- Lưới ảnh dạng polaroid có băng dính, mỗi ảnh nghiêng nhẹ.
- Có skeleton khi đang tải, và màn hình riêng khi chưa có ảnh hoặc không tìm thấy kết quả.

**Thêm ảnh (`components/UploadModal.jsx`)**
- Kéo thả hoặc bấm để chọn ảnh, xem trước trước khi lưu.
- Kiểm tra định dạng và dung lượng (15MB) ngay ở client.
- **Nén ảnh trước khi upload** (`compressImage.js`): thu nhỏ cạnh dài về tối đa 1920px, chuyển sang WebP chất lượng 80%. Trình duyệt không encode được WebP thì dùng JPEG. GIF giữ nguyên để không mất chuyển động. Nếu bản nén lớn hơn bản gốc thì gửi bản gốc. Góc ảnh hiện dung lượng trước → sau (vd. `4.2MB → 380KB`).
- Tự điền tiêu đề từ tên file. Chọn được ngày (không quá hôm nay) và thêm ghi chú.
- **Chọn phân loại** (`components/CategoryPicker.jsx`): các phân loại đã có hiện thành chip để bấm chọn (bấm lần nữa để bỏ chọn). Nút **+ Tạo mục mới** mở ô nhập tên (Enter để thêm, Esc để huỷ). Gõ trùng tên mục có sẵn thì chọn luôn mục đó. Đang lọc theo một phân loại mà bấm *Thêm ảnh* thì phân loại đó được chọn sẵn.

**Xem ảnh lớn (`components/Lightbox.jsx`)**
- Ảnh phóng to từ thẻ ra khung xem (shared layout animation).
- Chuyển ảnh trước/sau bằng nút ‹ › hoặc phím ← →. Esc để đóng.
- Sửa tiêu đề, ngày, phân loại, ghi chú ngay tại chỗ. Xoá có bước xác nhận.

### 3.2 Animation (Framer Motion)

| Vị trí | Hiệu ứng |
|---|---|
| Nền (`Background.jsx`) | 4 đốm màu mờ trôi chậm, ký tự ✦ ♥ ✧ ❀ bay từ dưới lên, lớp grain giấy |
| Bầu trời sao (`StarField.jsx`) | Vẽ bằng canvas. 3 lớp sao (xa → gần) lấp lánh và trôi chậm lên trên, lớp gần là sao 4 cánh. Rê chuột thì các lớp lệch theo (parallax). Sao băng bay qua mỗi 1,5–4 giây. Số sao tính theo diện tích màn hình |
| Bấm chuột (`ClickSparkles.jsx`) | Bung ra chùm 8 ngôi sao nhỏ tại vị trí bấm, nằm trên cùng nhưng không chặn click. Không bung khi bấm vào ô nhập |
| Chuyển trang | Fade in/out qua `AnimatePresence` |
| Auth – polaroid | Bay từ dưới lên (spring), sau đó lơ lửng lên xuống liên tục, phóng to khi rê chuột |
| Auth – form | Từng ô hiện lần lượt (stagger), form rung lắc khi nhập sai |
| Topbar | Trượt từ trên xuống |
| Thẻ ảnh | Xuất hiện lần lượt, khi hover thì thẳng lại, nâng lên và phóng nhẹ. Khi xoá thì thu nhỏ và xoay |
| Lọc/tìm kiếm | Lưới tự sắp xếp lại có animation (`layout`, `popLayout`) |
| Modal | Trượt lên kèm spring |
| Dropzone | Phóng to khi kéo file vào, icon nhún nhảy |
| Lightbox | Ảnh phóng ra từ vị trí thẻ (`layoutId`), panel thông tin trượt từ phải vào |
| Ảnh đang tải | Hiệu ứng shimmer, sau đó fade in |

Có hỗ trợ `prefers-reduced-motion`: nếu hệ điều hành bật giảm chuyển động thì animation CSS được tắt, bầu trời sao chỉ vẽ tĩnh một lần (không sao băng, không parallax) và không bung sao khi bấm chuột.

### 3.3 Xác thực phía FE
- **Không lưu token trong JS hay `localStorage`.** Token nằm trong cookie HttpOnly do BE đặt.
- `AuthContext` gọi `GET /api/auth/me` khi mở trang để khôi phục phiên đăng nhập.
- Một API trả `401` (trừ login/register/refresh/logout) thì `api/client.js` gọi `POST /api/auth/refresh` rồi gửi lại request đó 1 lần. Người dùng không thấy gì.
  - Nhiều request hết hạn cùng lúc (vd. tải nhiều ảnh) chỉ gọi refresh **một lần**.
  - Request vẫn được gửi lại kể cả khi refresh thất bại, vì tab khác có thể vừa refresh xong và cookie đã được cập nhật.
  - Gửi lại mà vẫn `401` thì phiên đã hết: FE xoá user và đưa về trang login.
- Ảnh được tải bằng `fetch` (kèm cookie) thành blob URL và cache trong bộ nhớ. Cache bị xoá khi đăng xuất.

### 3.4 Giao diện
- Font: **Be Vietnam Pro** (chữ thường), **Playfair Display** (tiêu đề).
- Tông màu kem ấm, màu nhấn cam san hô `#ff7a59`.
- **Theme sáng / tối** (`components/ThemeToggle.jsx`, `theme.js`):
  - Nút gạt ☀️/🌙 nằm trên thanh topbar ở trang Album và ở góc trên bên phải ở trang Login/Register. Khi bật tối, trong nút có vài ngôi sao nhỏ lấp lánh.
  - Đổi theme có hiệu ứng vòng tròn lan ra từ vị trí nút (View Transitions API). Trình duyệt không hỗ trợ (vd. Firefox cũ) hoặc bật giảm chuyển động thì đổi ngay.
  - Lựa chọn được lưu trong `localStorage` (key `theme`). Chưa chọn lần nào thì theo theme của hệ điều hành. Script nhỏ trong `index.html` đặt `data-theme` trên `<html>` trước khi vẽ trang nên không bị nháy sáng.
  - Toàn bộ màu nằm trong biến CSS ở `:root`, theme tối ghi đè ở `:root[data-theme="dark"]` (nền tím than `#15111d`, giữ màu nhấn cam san hô). Các đốm màu nền cũng đổi màu theo theme, sao trên canvas được cộng sáng cho rực hơn ở nền tối.
- Responsive: dưới 860px thì trang auth xếp 1 cột và lightbox xếp dọc. Dưới 520px thì lưới còn 2 cột.

### 3.5 Cấu trúc source FE
```
D:\My-Project-FE
├── index.html            # Load font Google, mount #root
├── vite.config.js        # Port 5173, proxy /api -> http://localhost:8081
├── functions/api/[[path]].js # Cloudflare Pages Function: proxy /api/* sang BE (BACKEND_URL)
├── public/
│   ├── favicon.svg
│   └── _headers          # Header cho Cloudflare Pages (cache /assets, bảo mật)
├── DEPLOY.md             # Hướng dẫn deploy
└── src/
    ├── main.jsx          # BrowserRouter + AuthProvider
    ├── App.jsx           # Routes, guard Protected / GuestOnly, splash khi đang tải
    ├── styles.css        # Toàn bộ CSS (biến màu, layout, responsive)
    ├── utils.js          # formatDate (dd/mm/yyyy), today()
    ├── theme.js          # Đọc / lưu theme, hiệu ứng đổi theme
    ├── compressImage.js  # Nén ảnh sang WebP (max 1920px) trước khi upload
    ├── api/client.js     # Hàm fetch chung + các hàm gọi API
    ├── context/AuthContext.jsx
    ├── pages/
    │   ├── AuthPage.jsx
    │   └── GalleryPage.jsx
    └── components/
        ├── Background.jsx
        ├── ThemeToggle.jsx   # Nút đổi theme sáng / tối
        ├── StarField.jsx     # Canvas bầu trời sao + sao băng
        ├── ClickSparkles.jsx # Chùm sao khi bấm chuột
        ├── MemoryCard.jsx
        ├── CategoryPicker.jsx # Chọn / tạo phân loại ảnh
        ├── UploadModal.jsx
        ├── SocialLogin.jsx   # Nút đăng nhập Google / Facebook
        ├── Modal.jsx
        ├── Lightbox.jsx
        └── SecureImage.jsx
```

---

## 4. Lịch sử thay đổi

1. **Khởi tạo**: BE Spring Boot dùng JWT trong body và H2. FE React với đầy đủ trang và animation.
2. **Chuyển sang PostgreSQL Neon**: bỏ H2, thông tin kết nối để trong `.env` (đã gitignore).
3. **Sửa lỗi "Invalid CORS request"**: lỗi xảy ra khi Vite chạy ở cổng khác 5173. Đã đổi sang `setAllowedOriginPatterns`, cho phép localhost ở mọi cổng.
4. **Bảo mật token**: chuyển JWT từ body + `localStorage` sang cookie HttpOnly với `SameSite=Strict`. Logout xoá cookie và thu hồi token.
5. **Nén ảnh ở FE**: ảnh được resize về 1920px và chuyển sang WebP trước khi gửi lên BE, giảm dung lượng khoảng 80–90%.
6. **Refresh token**: access token giảm từ 24h xuống 15 phút, thêm refresh token 7 ngày có rotation và phát hiện dùng lại. FE tự refresh khi gặp `401`.
7. **Đăng nhập Google / Facebook**: thêm bảng `social_accounts`, BE tự xác minh token, lần đầu đăng nhập thì tự tạo user.
8. **Phân loại ảnh**: thêm bảng `categories` và cột `memories.category_id`. Khi thêm/sửa ảnh chọn phân loại có sẵn hoặc tạo mới. Trang album có thanh lọc theo phân loại.
9. **Animation bầu trời sao**: thêm canvas sao lấp lánh, parallax theo chuột, sao băng, và chùm sao khi bấm chuột.
10. **Theme sáng / tối**: chuyển màu sang biến CSS, thêm nút đổi theme có hiệu ứng vòng tròn lan ra, lưu lựa chọn trong `localStorage`.
11. **Chuẩn bị deploy**: tách phần lưu ảnh thành `ImageStorage` (local / Cloudflare R2), thêm `Dockerfile` + `render.yaml` cho Render, Pages Function proxy `/api/*` cho Cloudflare Pages, `public/_headers` (cache asset, header bảo mật), viết `DEPLOY.md`.

## 5. Deploy
Hướng dẫn từng bước nằm trong **`DEPLOY.md`** (có ở cả 2 repo). Tóm tắt:
- **FE → Cloudflare Pages.** Build `npm run build`, output `dist`. Pages Function `functions/api/[[path]].js` chuyển `/api/*` sang BE (biến `BACKEND_URL`), nên trình duyệt chỉ thấy một domain và cookie `SameSite=Strict` vẫn chạy.
- **BE → Render** bằng Docker (`Dockerfile`, `render.yaml`). Biến môi trường: `COOKIE_SECURE=true`, `STORAGE_TYPE=r2`, `CORS_ORIGINS=https://<project>.pages.dev,...`, `JWT_SECRET` ngẫu nhiên.
- **DB → Neon**, **ảnh → Cloudflare R2.** Ảnh cũ trong `./uploads` được chuyển lên R2 bằng `MIGRATE_LOCAL_UPLOADS=true` (chạy 1 lần ở máy).
- Việc còn phải làm:
  - Đổi mật khẩu Neon (mật khẩu hiện tại đã bị chia sẻ qua chat).
  - Thêm domain Pages vào Google và Facebook.
  - Chuyển app Facebook sang *Live*.
  - Xoá các user test.
