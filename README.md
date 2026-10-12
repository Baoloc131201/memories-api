# Memories API (Spring Boot)

Java 21 · Spring Boot 3.3 · Spring Security + JWT · JPA · PostgreSQL (Neon)

## Chạy
1. Copy `.env.example` → `.env`, điền `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`.
2. `mvn spring-boot:run` (hoặc `mvn package` rồi `java -jar target/memories-api-1.0.0.jar`)
3. API chạy ở `http://localhost:8081`. Bảng được tự tạo (`ddl-auto=update`).

## API
| Method | Path | Mô tả |
|---|---|---|
| POST | /api/auth/register | `{username, displayName, password}` → user + cookie HttpOnly |
| POST | /api/auth/login | `{username, password}` → user + cookie HttpOnly |
| POST | /api/auth/logout | Thu hồi token, xoá cookie |
| GET | /api/auth/me | User hiện tại |
| GET | /api/memories | Danh sách kỉ niệm |
| POST | /api/memories | multipart: `file`, `title`, `description?`, `memoryDate?` |
| PUT | /api/memories/{id} | `{title, description, memoryDate}` |
| DELETE | /api/memories/{id} | Xoá |
| GET | /api/memories/{id}/image | Ảnh (cần đăng nhập) |

Ảnh lưu ở thư mục `./uploads` trên máy chạy server; metadata lưu trong PostgreSQL.

Chi tiết đầy đủ (API, FE, cấu hình, lịch sử thay đổi): xem `PROJECT_NOTES.md`.
