# Deploy Kỉ Niệm

| Phần | Nơi chạy | Ghi chú |
|---|---|---|
| Frontend | **Cloudflare Pages** (`https://<project>.pages.dev`) | Pages Function `functions/api/[[path]].js` chuyển `/api/*` sang Render |
| Backend | **Render** (Docker, gói free) | `Dockerfile` + `render.yaml` trong repo BE |
| Database + ảnh | **Neon** (PostgreSQL) | Bảng tự tạo khi BE khởi động (`ddl-auto=update`). File ảnh nằm trong bảng `memory_images` (`STORAGE_TYPE=db`) |

```
Trình duyệt ──► https://<project>.pages.dev
                  ├── /*      : file tĩnh (React build)
                  └── /api/*  : Pages Function ──► https://<service>.onrender.com ──► Neon (dữ liệu + ảnh)
```

**Vì sao ảnh lưu trong DB:** ổ đĩa của Render gói free bị xoá mỗi lần deploy, khởi động lại hoặc ngủ, nên không lưu ảnh vào thư mục `uploads/` được. Lưu trong Neon thì miễn phí và không cần thêm dịch vụ nào. Bù lại, ảnh chiếm dung lượng DB (xem phần *Giới hạn* ở cuối). Khi cần thêm chỗ thì chuyển sang Cloudflare R2. Code lưu ảnh trên R2 đã có sẵn (`STORAGE_TYPE=r2`), chỉ cần viết thêm bước chuyển ảnh từ DB sang R2.

**Vì sao FE gọi BE qua proxy:** trình duyệt chỉ làm việc với một domain duy nhất, nên cookie đăng nhập (`HttpOnly`, `SameSite=Strict`) chạy giống hệt lúc dev với Vite proxy. Nếu FE gọi thẳng `onrender.com` thì cookie bị coi là cookie bên thứ ba và bị trình duyệt chặn.

---

## Bước 0. Chuẩn bị

1. **Đổi mật khẩu DB Neon**, vì mật khẩu cũ đã bị chia sẻ qua chat. Vào Neon Console → **Roles** → **Reset password**, rồi cập nhật lại `.env` ở máy.
2. **Đẩy code lên GitHub**, mỗi phần một repo (có thể để private):
   ```bash
   cd D:\My-Project-BE
   git init && git add . && git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<user>/kiniem-api.git
   git push -u origin main

   cd D:\My-Project-FE
   git init && git add . && git commit -m "Initial commit"
   git branch -M main
   git remote add origin https://github.com/<user>/kiniem-web.git
   git push -u origin main
   ```
   `.gitignore` đã loại `.env`, `uploads/`, `target/`, `node_modules/`, `dist/`. Trước khi push, chạy `git status` để chắc chắn **không có file `.env`** trong danh sách.

## Bước 1. Neon

1. Neon Console → **Connection Details** → chọn định dạng **Java** (hoặc copy chuỗi `postgresql://...` rồi đổi thành dạng JDBC).
2. Ghi lại 3 giá trị:
   ```
   DB_URL=jdbc:postgresql://<host>.neon.tech/<db>?sslmode=require
   DB_USERNAME=<user>
   DB_PASSWORD=<password>
   ```
3. (Tuỳ chọn) Xoá các user test:
   ```sql
   DELETE FROM users WHERE username LIKE 'smoketest%' OR username LIKE 'corstest%'
     OR username LIKE 'cookietest%' OR username LIKE 'refreshtest%';
   ```

## Bước 2. Chuyển ảnh cũ trong `./uploads` vào Neon (làm một lần ở máy)

Ảnh đã upload lúc chạy ở máy đang nằm trong `D:\My-Project-BE\uploads`, còn DB chỉ lưu tên file. Cần đưa file vào DB trước khi deploy, nếu không web deploy sẽ hiện khung ảnh trống.

1. Thêm vào `D:\My-Project-BE\.env`:
   ```properties
   STORAGE_TYPE=db
   MIGRATE_LOCAL_UPLOADS=true
   ```
2. Chạy `mvn spring-boot:run` và chờ log `Đã chuyển X / Y ảnh từ ... sang nơi lưu mới`.
3. Đặt lại `MIGRATE_LOCAL_UPLOADS=false`. Từ giờ ở máy cũng nên để `STORAGE_TYPE=db`, vì máy và Render dùng chung DB Neon. Nếu để `local` thì ảnh upload ở máy sẽ không hiện trên web deploy.

Chạy lại bước này nhiều lần cũng không sao: ảnh đã có trong DB thì được bỏ qua.

## Bước 3. Render (Backend)

**Cách A: Blueprint** (dùng `render.yaml`)
1. Render Dashboard → **New → Blueprint** → kết nối GitHub → chọn repo `kiniem-api`.
2. Render hỏi các biến có `sync: false`. Điền theo bảng ở dưới (`STORAGE_TYPE=db` đã có sẵn trong file). `CORS_ORIGINS` tạm để `https://example.pages.dev`, bước 5 sẽ sửa lại.

**Cách B: Tạo tay**
1. **New → Web Service** → chọn repo → **Language: Docker**, Region **Singapore**, Instance **Free**.
2. **Advanced → Health Check Path**: `/api/auth/providers`.
3. Thêm các biến môi trường ở bảng dưới.

| Biến | Giá trị |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Lấy ở bước 1 |
| `JWT_SECRET` | Chuỗi ngẫu nhiên ≥ 32 ký tự. Blueprint tự sinh. Tạo tay thì dùng `openssl rand -base64 48` |
| `COOKIE_SECURE` | `true` |
| `CORS_ORIGINS` | `https://<project>.pages.dev,https://*.<project>.pages.dev` |
| `STORAGE_TYPE` | `db` |
| `GOOGLE_CLIENT_ID`, `FACEBOOK_APP_ID`, `FACEBOOK_APP_SECRET` | Tuỳ chọn, để trống thì nút đăng nhập tương ứng bị ẩn |

Deploy xong, Render cho URL dạng `https://memories-api-xxxx.onrender.com`. Mở `https://memories-api-xxxx.onrender.com/api/auth/providers`, thấy JSON `{"googleClientId":...}` là BE đã chạy.

> Lần build đầu mất khoảng 5–8 phút (tải Maven dependencies). Nếu log báo hết RAM (`OOMKilled`) thì báo lại để giảm thêm bộ nhớ.

## Bước 4. Cloudflare Pages (Frontend)

1. Cloudflare Dashboard → **Workers & Pages → Create → Pages → Connect to Git** → chọn repo `kiniem-web`.
2. Cấu hình build:
   - Framework preset: **React (Vite)** (hoặc *None*)
   - Build command: `npm run build`
   - Build output directory: `dist`
3. **Environment variables** (cả *Production* và *Preview*):
   - `BACKEND_URL` = `https://memories-api-xxxx.onrender.com` (URL Render, **không** có `/` ở cuối)
   - `NODE_VERSION` = `20`
4. **Save and Deploy**. Xong sẽ có URL `https://<project>.pages.dev`.

Thư mục `functions/` ở gốc repo được Cloudflare tự nhận thành Pages Function. Không cần cấu hình thêm.

> Không dùng GitHub thì có thể upload thẳng từ máy: `npx wrangler login` rồi `npm run build && npx wrangler pages deploy dist --project-name <project>`, chạy ở thư mục gốc FE để wrangler thấy thư mục `functions/`. Sau đó thêm `BACKEND_URL` trong **Settings → Variables** và deploy lại.

## Bước 5. Nối lại các phần

1. **Render**: sửa `CORS_ORIGINS` thành domain Pages thật, ví dụ `https://kiniem.pages.dev,https://*.kiniem.pages.dev`, rồi **Save, rebuild and deploy**.
2. **Google** (nếu dùng): Google Cloud Console → **Credentials** → OAuth client → **Authorized JavaScript origins** → thêm `https://<project>.pages.dev`.
3. **Facebook** (nếu dùng):
   - **App settings → Basic**: *App domains* = `<project>.pages.dev`. Mục *Website → Site URL* = `https://<project>.pages.dev/`.
   - **Facebook Login → Settings → Allowed Domains for the JavaScript SDK**: thêm `https://<project>.pages.dev`.
   - Chuyển app sang **Live** để mọi người đăng nhập được (xem mục 1.1 trong `PROJECT_NOTES.md`).

## Bước 6. Kiểm tra

- [ ] Mở `https://<project>.pages.dev`. Lần đầu có thể quay khoảng 1 phút vì Render đang khởi động.
- [ ] Đăng ký tài khoản mới, F5 trang vẫn còn đăng nhập.
- [ ] Ảnh cũ (đã chuyển ở bước 2) vẫn hiện đủ.
- [ ] Thêm ảnh có phân loại, ảnh hiện ra. Bấm **Manual Deploy → Restart service** trên Render rồi tải lại trang, ảnh vẫn còn.
- [ ] Mở thẳng `https://<project>.pages.dev/abc` (route không tồn tại) phải về trang Album hoặc Login, không bị 404.
- [ ] Đăng xuất rồi vào lại.
- [ ] Mở tab ẩn danh, đăng nhập user khác: không thấy ảnh của user đầu.

---

## Giới hạn của gói free

| Dịch vụ | Giới hạn | Ảnh hưởng |
|---|---|---|
| Render Free | Ngủ sau **15 phút** không có request, 512MB RAM, 750 giờ/tháng | Lần mở đầu tiên sau khi ngủ chờ 30–60 giây. Có thể dùng [cron-job.org](https://cron-job.org) gọi `https://<service>.onrender.com/api/auth/providers` mỗi 10 phút để giữ BE luôn thức (750 giờ đủ cho 1 service chạy cả tháng) |
| Cloudflare Pages Functions | 100.000 request/ngày | Mỗi lần tải ảnh là 1 request. Ảnh được cache trong trình duyệt 7 ngày nên khó chạm giới hạn |
| Neon Free | **0,5GB** cho cả DB, có giới hạn lưu lượng dữ liệu ra mỗi tháng, tự ngủ khi không dùng | Ảnh đã nén khoảng 200–400KB nên chứa được khoảng **1.500–2.500 ảnh**. Ảnh được cache trong trình duyệt 7 ngày nên ít khi phải tải lại từ DB. Lần query đầu sau khi ngủ chậm khoảng 1 giây |

### Xem DB đã dùng bao nhiêu
Chạy trong **Neon Console → SQL Editor**:
```sql
SELECT pg_size_pretty(pg_database_size(current_database())) AS tong,
       pg_size_pretty(pg_total_relation_size('memory_images')) AS anh,
       (SELECT count(*) FROM memory_images) AS so_anh;
```
Khi gần đầy 0,5GB thì có 2 lựa chọn:
- Nâng gói Neon.
- Chuyển ảnh sang Cloudflare R2: đặt `STORAGE_TYPE=r2` và các biến `R2_*` (xem `.env.example`). Bước chuyển ảnh từ DB sang R2 **chưa có sẵn**, cần viết thêm.

## Cập nhật sau này

- Push code lên nhánh `main` thì Render và Cloudflare Pages **tự build và deploy lại**.
- Đổi biến môi trường trên Render cần **Save, rebuild and deploy**. Trên Cloudflare Pages cần deploy lại (**Deployments → Retry deployment**).
