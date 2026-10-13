# Deploy Kỉ Niệm

## Thông tin deploy hiện tại

| Phần | Dịch vụ | URL / tên |
|---|---|---|
| Frontend | **Cloudflare Pages** | https://memories-web-8q7.pages.dev (project `memories-web`) |
| Backend | **Render** (Docker, gói Free) | https://memories-api-7rc3.onrender.com (service `memories-api`) |
| Database + ảnh | **Neon** (PostgreSQL) | Ảnh nằm trong bảng `memory_images` (`STORAGE_TYPE=db`) |
| Code FE | GitHub | https://github.com/Baoloc131201/memories-web |
| Code BE | GitHub | https://github.com/Baoloc131201/memories-api |

```
Trình duyệt ──► https://memories-web-8q7.pages.dev
                  ├── /*      : file tĩnh (React build)
                  └── /api/*  : Pages Function ──► https://memories-api-7rc3.onrender.com ──► Neon (dữ liệu + ảnh)
```

**Vì sao FE gọi BE qua proxy:** file `functions/api/[[path]].js` chuyển mọi request `/api/*` sang Render. Trình duyệt chỉ làm việc với một domain (`pages.dev`), nên cookie đăng nhập (`HttpOnly`, `SameSite=Strict`) chạy giống hệt lúc dev với Vite proxy. Nếu FE gọi thẳng `onrender.com` thì trình duyệt coi đó là cookie bên thứ ba và chặn.

**Vì sao ảnh lưu trong DB:** ổ đĩa của Render gói Free bị xoá mỗi lần deploy, khởi động lại hoặc ngủ, nên không lưu ảnh vào thư mục `uploads/` được. Lưu trong Neon thì Render ngủ hay khởi động lại ảnh vẫn còn, và không tốn phí. Bù lại, ảnh chiếm dung lượng DB (xem phần *Giới hạn* ở cuối).

---

## Bước 1. Đẩy code lên GitHub

Mỗi phần một repo, tạo trên GitHub ở dạng **trống**: không tick *README*, *.gitignore* hay *license*.

```bash
cd D:\My-Project-BE
git init -b main && git add -A && git commit -m "Initial commit"
git remote add origin https://github.com/Baoloc131201/memories-api.git
git push -u origin main

cd D:\My-Project-FE
git init -b main && git add -A && git commit -m "Initial commit"
git remote add origin https://github.com/Baoloc131201/memories-web.git
git push -u origin main
```

- `.gitignore` đã loại `.env`, `uploads/`, `target/`, `data/`, `node_modules/`, `dist/`, `.idea/`. Trước khi commit, chạy `git status` để chắc chắn **không có `.env`**.
- Trong WSL chưa đăng nhập GitHub thì git báo lỗi `could not read Username`. Cách sửa: dùng Git Credential Manager có sẵn của Git for Windows, nó sẽ mở cửa sổ đăng nhập trên trình duyệt:
  ```bash
  git config credential.helper "/mnt/c/Program\ Files/Git/mingw64/bin/git-credential-manager.exe"
  ```

## Bước 2. Lấy thông tin Neon

Neon Console → **Connection Details** → chọn định dạng **Java**:
```
DB_URL=jdbc:postgresql://<host>.neon.tech/<db>?sslmode=require
DB_USERNAME=<user>
DB_PASSWORD=<password>
```
Các bảng (`users`, `memories`, `categories`, `memory_images`...) **tự tạo** khi BE khởi động lần đầu (`ddl-auto=update`), không cần chạy SQL.

> **Dữ liệu ở máy và trên web:** nếu `.env` ở máy dùng cùng `DB_URL` với Render thì dữ liệu test ở máy cũng hiện trên web. Kỉ niệm test với `STORAGE_TYPE=local` sẽ hiện **khung ảnh trống** trên web, vì file ảnh chỉ nằm trên máy. Nên tạo một DB riêng cho máy: **Neon Console → Branches → Create branch** (vd. `dev`), copy connection string của branch `dev` vào `.env` ở máy. Branch `main` để cho Render dùng.

## Bước 3. Render (Backend)

**New → Web Service** → kết nối GitHub → chọn repo `memories-api`.

| Mục | Giá trị |
|---|---|
| Name | `memories-api` |
| Language | **Docker** (Render không có sẵn Java, BE build bằng `Dockerfile`) |
| Branch | `main` |
| Region | Gần region của Neon nhất (Neon ở Singapore thì chọn **Singapore**) |
| Root Directory | để trống |
| **Instance Type** | **Free**. ⚠️ Render **mặc định chọn Starter $7/tháng**. Phải đổi sang Free và kiểm tra thanh dưới cùng ghi `$0 / month` rồi mới bấm Deploy |
| Advanced → Health Check Path | `/api/auth/providers` |

**Environment Variables:**

| Key | Value |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Lấy ở bước 2 |
| `JWT_SECRET` | Bấm **Generate** để Render tạo chuỗi ngẫu nhiên. **Không** dùng lại secret ở máy |
| `COOKIE_SECURE` | `true` |
| `STORAGE_TYPE` | `db`. ⚠️ **Bắt buộc.** Thiếu biến này thì ảnh lưu ra ổ đĩa tạm của Render và mất khi Render ngủ |
| `CORS_ORIGINS` | `https://memories-web-8q7.pages.dev,https://*.memories-web-8q7.pages.dev`. Lần tạo đầu chưa có domain Pages thì tạm để `https://example.pages.dev`, sửa lại ở bước 5 |
| `GOOGLE_CLIENT_ID` | Tuỳ chọn, để trống thì nút Google tự ẩn |
| `FACEBOOK_APP_ID`, `FACEBOOK_APP_SECRET` | Tuỳ chọn, để trống thì nút Facebook tự ẩn |

Bấm **Deploy Web Service**:
- Build lần đầu mất khoảng **5–8 phút** (tải Maven dependencies).
- Sau khi build xong, Spring Boot cần thêm khoảng **1 phút** để khởi động, vì gói Free chỉ có 0,1 CPU. Trong lúc này Render hiện *"Waiting for internal health check..."*, đó là bình thường.
- Xong khi log có dòng `Started MemoriesApplication` và trạng thái chuyển **Live**.
- Kiểm tra: mở https://memories-api-7rc3.onrender.com/api/auth/providers, thấy JSON `{"googleClientId":...,"facebookAppId":...}` là BE đã chạy và đã kết nối được Neon.

Cũng có thể dùng **New → Blueprint**, Render sẽ đọc file `render.yaml` trong repo (đã có sẵn Free, Docker, health check, `STORAGE_TYPE=db`, tự sinh `JWT_SECRET`).

## Bước 4. Cloudflare Pages (Frontend)

> ⚠️ **Phải chọn Pages, không chọn Workers.** Trang **Create an application** mặc định là **Workers**: có các bước *Select a method → Select a repository → Create and deploy* và ô *API token*. Code FE dùng thư mục `functions/` mà chỉ **Pages** mới hiểu. Nếu deploy bằng Workers thì giao diện vẫn lên nhưng mọi request `/api` lỗi, không đăng nhập được.

1. https://dash.cloudflare.com → **Workers & Pages** → **Create**.
2. Kéo xuống **cuối trang** và bấm dòng nhỏ **"Looking to deploy Pages? Get started"**.
3. **Import an existing Git repository** → chọn repo `memories-web` → **Begin setup**.
4. Màn hình đúng có tiêu đề **Set up builds and deployments**:

   | Ô | Giá trị |
   |---|---|
   | Project name | `memories-web` (Cloudflare tự thêm đuôi nếu tên đã bị dùng, nên domain là `memories-web-8q7.pages.dev`) |
   | Production branch | `main` |
   | Framework preset | **React (Vite)** |
   | Build command | `npm run build` |
   | Build output directory | `dist` |
   | Root directory | để trống |

5. **Environment variables (advanced)**:

   | Variable name | Value |
   |---|---|
   | `BACKEND_URL` | `https://memories-api-7rc3.onrender.com` (**không** có `/` ở cuối) |
   | `NODE_VERSION` | `20` |

6. **Save and Deploy**, khoảng 1–2 phút. Xong có URL https://memories-web-8q7.pages.dev.

Thư mục `functions/` ở gốc repo được Pages tự nhận thành Pages Function. Mọi route không phải `/api` và không phải file tĩnh sẽ trả về `index.html` (vd. `/login`), nên React Router chạy đúng.

## Bước 5. Nối lại các phần

### 5.1 Render: sửa `CORS_ORIGINS`
**memories-api → Environment** → sửa `CORS_ORIGINS`:
```
https://memories-web-8q7.pages.dev,https://*.memories-web-8q7.pages.dev
```
→ **Save, rebuild and deploy**.

### 5.2 Google (nút "Tiếp tục với Google")
https://console.cloud.google.com → **APIs & Services → Credentials** → bấm vào OAuth client đang dùng → **Authorized JavaScript origins** → **+ Add URI**:
```
https://memories-web-8q7.pages.dev
```
→ **Save**. Có thể phải chờ 5 phút đến vài giờ thì Google mới áp dụng. Giữ `http://localhost:5173` và `http://localhost` để vẫn test được ở máy.

### 5.3 Facebook (nút "Tiếp tục với Facebook")
Vào https://developers.facebook.com/apps → chọn app.

**a) App settings → Basic**
- **App domains**: `memories-web-8q7.pages.dev` (không có `https://`, không có `/` ở cuối).
- **Kéo xuống cuối trang**, khung **Website → Site URL**: `https://memories-web-8q7.pages.dev/`.
  - Chưa có khung Website thì bấm **+ Add platform → Website**.
  - Site URL chỉ nhận **một** địa chỉ. Đổi sang domain Pages thì đăng nhập Facebook ở máy (`localhost`) sẽ không dùng được. Muốn test ở máy thì tạm đổi lại.
- **Save changes**.

**b) Use cases → Authenticate and request data from users with Facebook Login → Customize → Settings**, khung **Client OAuth settings**:

| Mục | Giá trị |
|---|---|
| **Log in using JavaScript SDK** | ⚠️ Bật **Yes**. Nếu đang là *"Are not"* (tắt) thì nút Facebook trên web không đăng nhập được |
| **Allowed domains for the JavaScript SDK** | Gõ `https://memories-web-8q7.pages.dev` rồi nhấn **Enter**. Thêm `http://localhost` nếu muốn test ở máy. Ô này chỉ hiện khi đã bật mục trên |
| Strict usage mode for redirect URIs | Giữ bật |
| Valid OAuth redirect URI | Để trống. Nếu popup báo *"URL blocked"* thì thêm `https://memories-web-8q7.pages.dev/` |
| Log in from your device | Để tắt (dành cho Smart TV) |

→ **Save changes**.

**c) Chế độ Live**
App đang ở **Development** nên chỉ admin và tester của app đăng nhập được. Muốn mọi người dùng được thì chuyển sang **Live** (cần link Privacy Policy công khai), xem mục 1.1 trong `PROJECT_NOTES.md`.

## Bước 6. Kiểm tra

Đã kiểm tra bằng `curl` sau khi deploy:
- [x] `https://memories-web-8q7.pages.dev/` → 200
- [x] `/login` (route React) → 200, không bị 404
- [x] `/api/auth/providers` qua proxy → 200, trả đúng JSON từ Render
- [x] `/api/auth/me` khi chưa đăng nhập → 401
- [x] `POST /api/auth/login` sai mật khẩu → 401 *"Sai tên đăng nhập hoặc mật khẩu"* (không bị chặn CORS)

Tự kiểm tra trên trình duyệt:
- [ ] Đăng ký tài khoản, F5 vẫn còn đăng nhập
- [ ] Thêm ảnh có phân loại, ảnh hiện ra
- [ ] Render → **Manual Deploy → Restart service**, chờ khoảng 1 phút rồi F5: ảnh vẫn còn
- [ ] Đăng xuất rồi đăng nhập lại
- [ ] Đăng nhập bằng Google và Facebook (sau khi làm bước 5.2, 5.3)
- [ ] Mở tab ẩn danh, đăng nhập user khác: không thấy ảnh của user đầu

---

## Giới hạn của gói free

| Dịch vụ | Giới hạn | Ảnh hưởng |
|---|---|---|
| Render Free | Ngủ sau **15 phút** không có request, 512MB RAM, 0,1 CPU, 750 giờ/tháng | Lần mở đầu tiên sau khi ngủ chờ khoảng **30–60 giây**. **Ảnh và dữ liệu không mất** vì nằm trong Neon |
| Cloudflare Pages Functions | 100.000 request/ngày | Mỗi lần tải ảnh là 1 request. Ảnh được cache trong trình duyệt 7 ngày nên khó chạm giới hạn |
| Neon Free | **0,5GB** cho cả DB, có giới hạn lưu lượng ra mỗi tháng, tự ngủ khi không dùng | Ảnh đã nén khoảng 200–400KB nên chứa được khoảng **1.500–2.500 ảnh**. Lần query đầu sau khi ngủ chậm khoảng 1 giây |

### Giữ Render luôn thức (tuỳ chọn)
Tạo job miễn phí trên https://cron-job.org:
- URL: `https://memories-api-7rc3.onrender.com/api/auth/providers`
- Lịch: mỗi 10 phút

750 giờ/tháng đủ cho 1 service chạy suốt cả tháng.

### Xem DB đã dùng bao nhiêu
**Neon Console → SQL Editor**:
```sql
SELECT pg_size_pretty(pg_database_size(current_database())) AS tong,
       pg_size_pretty(pg_total_relation_size('memory_images')) AS anh,
       (SELECT count(*) FROM memory_images) AS so_anh;
```
Khi gần đầy 0,5GB thì có 2 lựa chọn:
- Nâng gói Neon.
- Chuyển ảnh sang Cloudflare R2 (free 10GB, cần khai báo thẻ): đặt `STORAGE_TYPE=r2` và các biến `R2_*` (xem `.env.example`). Code lưu trên R2 đã có sẵn, nhưng bước chuyển ảnh từ DB sang R2 **chưa có**, cần viết thêm.

### Chuyển ảnh cũ trong `./uploads` vào DB (khi cần)
Đặt trong `.env` ở máy: `STORAGE_TYPE=db` và `MIGRATE_LOCAL_UPLOADS=true`. Chạy `mvn spring-boot:run`, chờ log `Đã chuyển X / Y ảnh ...`, rồi đặt lại `MIGRATE_LOCAL_UPLOADS=false`. Chạy nhiều lần cũng không sao: ảnh đã có trong DB thì được bỏ qua.

---

## Cập nhật sau này

| Việc | Cách làm |
|---|---|
| Sửa code | Push lên nhánh `main`. Render và Cloudflare Pages **tự build và deploy lại** |
| Đổi biến môi trường trên Render | **Environment** → sửa → **Save, rebuild and deploy** |
| Đổi biến môi trường trên Pages | **Settings → Variables and Secrets** → sửa → **Deployments → Retry deployment** (biến mới chỉ có hiệu lực ở lần deploy sau) |
| Xem log BE | Render → `memories-api` → **Logs** |
| Xem log proxy | Cloudflare → `memories-web` → **Functions → Real-time logs** |
| Đổi URL Render hoặc domain Pages | Cập nhật `BACKEND_URL` (Pages), `CORS_ORIGINS` (Render), Google origins, Facebook domains |

## Bảo mật cần nhớ
- **Không commit `.env`.** Secret thật chỉ nằm trong Environment của Render.
- **Nên đổi mật khẩu Neon**, vì mật khẩu hiện tại đã bị chia sẻ qua chat. Đổi ở **Neon Console → Roles → Reset password**, rồi cập nhật `DB_PASSWORD` trên Render và trong `.env` ở máy.
- `JWT_SECRET` trên Render khác với ở máy, nên cookie đăng nhập ở máy không dùng được trên web và ngược lại.
