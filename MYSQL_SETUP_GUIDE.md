# MySQL Database Setup Guide

## ✅ Configuration Complete!

Your application is now configured to use **MySQL** database named `adaptiveexam`.

---

## 📋 Prerequisites

You need **MySQL Server** installed on your machine.

### Option 1: MySQL Workbench (Recommended for Windows)
Download from: https://dev.mysql.com/downloads/mysql/

### Option 2: XAMPP (Includes MySQL + phpMyAdmin)
Download from: https://www.apachefriends.org/

---

## 🚀 Setup Steps

### 1. **Install MySQL** (if not already installed)

**For XAMPP:**
- Install XAMPP
- Start MySQL from XAMPP Control Panel
- Default credentials: username=`root`, password=`(empty)`

**For MySQL Standalone:**
- Install MySQL Server
- Set root password during installation
- Start MySQL service

---

### 2. **Create Database** (Optional - Auto-created)

The database `adaptiveexam` will be **automatically created** when you run the application.

But if you want to create it manually:

```sql
CREATE DATABASE adaptiveexam;
USE adaptiveexam;
```

---

### 3. **Update Password** (if needed)

If your MySQL root user has a password, update `application.properties`:

```properties
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD_HERE
```

---

### 4. **Verify MySQL is Running**

**Windows:**
```powershell
# Check if MySQL service is running
Get-Service -Name MySQL*

# Or test connection
mysql -u root -p
```

**XAMPP:**
- Open XAMPP Control Panel
- Ensure MySQL shows "Running" status

---

## 📊 Database Schema

When you run the application, these tables will be **automatically created**:

### Table: `users`
```sql
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    enabled BOOLEAN DEFAULT FALSE,
    verification_token VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 🔧 Connection Details

| Setting | Value |
|---------|-------|
| **Host** | localhost |
| **Port** | 3306 |
| **Database** | adaptiveexam |
| **Username** | root |
| **Password** | (empty or your password) |
| **JDBC URL** | `jdbc:mysql://localhost:3306/adaptiveexam` |

---

## ✅ Test Connection

### Method 1: MySQL Command Line
```bash
mysql -u root -p
# Enter password (or just press Enter if no password)

SHOW DATABASES;
# Should list 'adaptiveexam' after first run
```

### Method 2: phpMyAdmin (if using XAMPP)
```
http://localhost/phpmyadmin
```

---

## 🎯 Benefits of MySQL over H2

✅ **Persistent Storage**: Data survives application restarts  
✅ **No Re-registration**: Users stay registered  
✅ **Production Ready**: Use same database in production  
✅ **Better Performance**: Optimized for larger datasets  
✅ **Scalability**: Can handle concurrent users  
✅ **Data Backup**: Easy to backup and restore  

---

## 🐛 Troubleshooting

### Error: "Connection refused"
**Solution:** Start MySQL service
```powershell
# Windows Service
net start MySQL80

# Or use XAMPP Control Panel
```

### Error: "Access denied for user 'root'"
**Solution:** Update password in `application.properties`

### Error: "Unknown database 'adaptiveexam'"
**Solution:** The database will be auto-created. If not, create manually:
```sql
CREATE DATABASE adaptiveexam;
```

### Error: "Communications link failure"
**Solution:** 
1. Check if MySQL is running on port 3306
2. Verify firewall allows connections
3. Try: `telnet localhost 3306`

---

## 📝 Common MySQL Commands

```sql
-- Show all databases
SHOW DATABASES;

-- Use the database
USE adaptiveexam;

-- Show all tables
SHOW TABLES;

-- View users table
SELECT * FROM users;

-- Count registered users
SELECT COUNT(*) FROM users;

-- Delete all data (reset)
TRUNCATE TABLE users;

-- Drop database (start fresh)
DROP DATABASE adaptiveexam;
CREATE DATABASE adaptiveexam;
```

---

## 🔐 Security Note

**For Production:**
```properties
# Change these values!
spring.datasource.username=your_secure_username
spring.datasource.password=your_strong_password

# Use SSL
spring.datasource.url=jdbc:mysql://localhost:3306/adaptiveexam?useSSL=true&requireSSL=true
```

---

## 🚀 Run Your Application

```powershell
mvn spring-boot:run
```

**First Run:**
- Database `adaptiveexam` is created automatically
- Tables are created based on entities
- You can now register users!

**Subsequent Runs:**
- All registered users persist
- No need to re-register
- Data is preserved

---

## 📂 View Data with MySQL Workbench

1. Open MySQL Workbench
2. Connect to localhost:3306
3. Navigate to `adaptiveexam` database
4. View `users` table
5. See all registered students and teachers

---

## ✨ You're All Set!

Your application now uses **MySQL** with the database name **adaptiveexam**.

**Next Steps:**
1. Start MySQL service
2. Run your Spring Boot application
3. Register users at `http://localhost:8080/register`
4. Users will persist across restarts! 🎉

---

**Need help?** Check MySQL logs at:
- XAMPP: `C:\xampp\mysql\data\mysql_error.log`
- Standalone: Check MySQL installation directory
