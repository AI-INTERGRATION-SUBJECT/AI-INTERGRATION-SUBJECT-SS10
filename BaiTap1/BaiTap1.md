# BÀI TẬP 1: PHÂN TÍCH & LỰA CHỌN GIẢI PHÁP TRIỂN KHAI LANGFUSE (LLMOPS)

---

## 📊 **1. BẢNG SO SÁNH 3 PHƯƠNG ÁN TRIỂN KHAI LANGFUSE SELF-HOST**

| Tiêu chí | Phương án A (PostgreSQL Only trên Docker) | Phương án B (Postgres + ClickHouse trên Docker) | Phương án C (External Database AWS RDS + ClickHouse) |
| :--- | :--- | :--- | :--- |
| **Bảo mật dữ liệu (Data Privacy)** |  Trung bình. Dữ liệu tài chính nằm trong Docker local, dễ mất nếu container hỏng. |  Khá tốt. Nằm hoàn toàn trong hạ tầng mạng nội bộ Docker. | 🌟 **Rất cao (Doanh nghiệp)**. Dữ liệu được cô lập trong VPC bảo mật, mã hóa tại chỗ (At-Rest Encryption). |
| **Tài nguyên (CPU/RAM/Storage)** | ⚡ **Thấp nhất** (Chỉ tốn ~1-2 GB RAM), thích hợp máy Dev local. | ⚠️ **Cao** (Tốn ~4-8 GB RAM cho ClickHouse + Postgres), dễ gây ngẽn RAM local. | 🎯 **Tối ưu**. Phân tải DB ra server riêng, server Langfuse chỉ tốn tài nguyên chạy Web/API. |
| **Độ phức tạp triển khai** |  **Đơn giản nhất**. Chỉ cần 1 file Docker Compose ngắn. | ⚠️ **Trung bình**. Cần quản lý cấu hình kết nối giữa 2 CSDL khác nhau trong Docker. |  **Chuẩn Enterprise**. Cần cấu hình Security Group, VPC Peering và Environment Variables. |
| **Khả năng Sao lưu & Phục hồi** | ❌ **Kém**. Phụ thuộc vào file mount volume thủ công của Docker. | ❌ **Trung bình - Rủi ro**. Phục hồi Postgres & ClickHouse đồng bộ khi sập đĩa khá phức tạp. | 🌟 **Hoàn hảo**. Tận dụng tính năng Automated Backup, Multi-AZ Failover, Point-In-Time Restore của AWS RDS. |

---

## 🎯 **2. BÀI PHÂN TÍCH LỰA CHỌN GIẢI PHÁP TỐI ƯU CHO RIKKEIPAY**

### **Đáp án Lựa chọn:** **Phương án C** (Triển khai Langfuse Self-Host kết nối CSDL PostgreSQL External / Managed Database RDS + ClickHouse).

### **Lý do lựa chọn dưới góc nhìn Kỹ sư Tích hợp AI (LLMOps Engineer):**

1. **Đảm bảo Tiêu chuẩn An toàn Dữ liệu Ngân hàng (Banking Security & Compliance):**
   - Hệ thống RikkeiPay xử lý hàng ngàn giao dịch chuyển tiền và truy vấn tài chính mỗi ngày. Dữ liệu Trace từ Langfuse chứa các nội dung câu thoại, tài khoản và thông tin nhạy cảm của khách hàng.
   - Việc kết nối tới CSDL External Managed PostgreSQL (như AWS RDS Postgres) nằm trong **Mạng nội bộ cô lập (Private VPC)** đảm bảo tuân thủ nghiêm ngặt chuẩn bảo mật dữ liệu ngân hàng, mã hóa SSL/TLS khi truyền và mã hóa KMS trên đĩa cứng.

2. **Khả năng Mở rộng & Tách biệt Tải (High Scalability & Performance):**
   - ClickHouse chịu trách nhiệm xử lý hàng triệu bản ghi Traces/Logs OLAP tốc độ cao với khả năng nén dữ liệu cực tốt.
   - External PostgreSQL đảm nhận lưu trữ các Metadata, User Sessions và Prompt Templates bền vững mà không bị tranh chấp tài nguyên CPU/RAM với ứng dụng Web của Langfuse.

3. **Chịu lỗi & Sao lưu Tự động (Automated Disaster Recovery):**
   - Khi xảy ra sự cố hỏng hóc container hoặc nâng cấp bản vá (Rolling Update), dữ liệu ngân hàng không bao giờ bị mất nhờ cơ chế sao lưu liên tục (Point-in-Time Restore) và khôi phục tự động Multi-AZ của CSDL Managed.

---

## ❌ **3. PHÂN TÍCH NHƯỢC ĐIỂM VÀ RỦI RO CỦA CÁC PHƯƠNG ÁN BỊ LOẠI BỎ**

### **3.1. Rủi ro của Phương án A (PostgreSQL Only trên Docker Local):**
- ❌ **Nút thắt cổ chai hiệu năng (Performance Bottleneck):** PostgreSQL thuần túy không được thiết kế để truy vấn phân tích chuỗi thời gian (Time-series OLAP) lớn. Khi số lượng Traces vượt quá 100,000 bản ghi, các Dashboard hiển thị Latency, Token Usage và Cost trên Langfuse sẽ bị lag, timeout hoặc gây nghẽn RAM nghiêm trọng.
- ❌ **Chỉ hợp với Dev Local:** Phương án A chỉ phù hợp cho sinh viên hoặc lập trình viên test thử ở máy cá nhân, hoàn toàn **KHÔNG ĐỦ TIÊU CHUẨN** để triển khai Production cho ngân hàng.

### **3.2. Rủi ro của Phương án B (Postgres + ClickHouse hoàn toàn trong Docker):**
- ❌ **Rủi ro Mất mát Dữ liệu (Data Loss Risk):** Việc tự host cả 2 CSDL Postgres và ClickHouse bên trong container Docker trên cùng 1 server ảo làm tăng nguy cơ hỏng hóc đĩa cứng (Disk Corruption). Khi đĩa bị đầy hoặc container crash, việc phục hồi dữ liệu đồng bộ giữa Postgres và ClickHouse rất phức tạp.
- ❌ **Lãng phí Tài nguyên Server:** Máy chủ chạy Docker phải gánh đồng thời 3 service nặng (Langfuse App, Postgres DB, ClickHouse DB), dẫn đến nguy cơ OOM (Out Of Memory) Kills bất ngờ khi lưu lượng truy cập ngân hàng tăng đột biến.
