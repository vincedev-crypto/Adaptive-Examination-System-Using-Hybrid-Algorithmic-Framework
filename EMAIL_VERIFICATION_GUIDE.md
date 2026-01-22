# Email Verification Setup Guide

## ✅ What's Been Implemented

The email verification system is now fully implemented! Users will receive a verification email after registration and must click the link to activate their account.

### Implementation Details:

1. **EmailService** - Sends verification emails with unique token links
2. **UserService Updates** - Creates users with `enabled=false`, generates UUID tokens, sends verification emails
3. **Verification Endpoint** - `/verify?token={UUID}` endpoint handles email verification
4. **Verification Pages** - Success and failure pages for user feedback
5. **Security Configuration** - `/verify**` endpoint is publicly accessible

## 🔧 Configuration Required

Before testing, you MUST configure the email settings in `application.properties`:

### Option 1: Gmail (Recommended for Testing)

1. **Enable 2-Factor Authentication** on your Gmail account
2. **Generate App Password**:
   - Go to: https://myaccount.google.com/apppasswords
   - Select "Mail" and your device
   - Copy the 16-character password
3. **Update application.properties**:
   ```properties
   spring.mail.username=your-email@gmail.com
   spring.mail.password=your-16-char-app-password
   ```

### Option 2: Outlook/Hotmail

```properties
spring.mail.host=smtp-mail.outlook.com
spring.mail.port=587
spring.mail.username=your-email@outlook.com
spring.mail.password=your-password
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true
```

### Option 3: Other SMTP Server

Update the host, port, username, and password according to your email provider's SMTP settings.

## 📧 How It Works

### Registration Flow:
1. User fills out registration form (student or teacher)
2. System creates user with `enabled=false`
3. System generates a unique UUID verification token
4. System sends email with verification link: `http://localhost:8080/verify?token={UUID}`
5. User sees success message: "Registration successful! Please check your email to verify your account."

### Verification Flow:
1. User clicks link in email
2. System looks up user by token
3. If found: Sets `enabled=true`, clears token, shows success page
4. If not found: Shows failure page (invalid/expired link)

### Login Protection:
- Users with `enabled=false` cannot login
- Spring Security's `CustomUserDetailsService` checks `user.isEnabled()`
- Unverified users will see "Invalid email or password" error

## 🧪 Testing Steps

1. **Configure Email** (see above)
2. **Start Application**: `mvn spring-boot:run`
3. **Register Account**: Navigate to http://localhost:8080/register
4. **Check Email**: Look for verification email (check spam folder!)
5. **Click Verification Link**: Opens success page
6. **Login**: Try logging in at http://localhost:8080/login

## 📝 Important Notes

### Email Deliverability:
- Gmail may block emails from localhost initially
- Check spam/junk folder
- For production, use a proper email service (SendGrid, AWS SES, etc.)

### Token Security:
- Tokens are UUID (128-bit random)
- Tokens stored in database, cleared after verification
- No expiration implemented yet (24-hour expiry mentioned in email but not enforced)

### Future Enhancements:
1. **Token Expiration**: Add `tokenExpiryDate` field to User entity
2. **Resend Verification**: Add endpoint to resend verification email
3. **Password Reset**: EmailService already has `sendPasswordResetEmail()` method ready
4. **Rate Limiting**: Prevent spam registrations from same email/IP

## 🐛 Troubleshooting

### Email Not Sending:
- Check console for errors
- Verify SMTP credentials are correct
- Test SMTP connection with telnet: `telnet smtp.gmail.com 587`
- Ensure firewall allows outbound port 587

### "Invalid email or password" after verification:
- Check database: `SELECT * FROM users WHERE email='your-email@school.edu';`
- Verify `enabled` column is `1` (true)
- Check password was hashed correctly

### Verification Link Not Working:
- Verify `/verify**` is in SecurityConfig permitAll() list
- Check token in database matches token in URL
- Look for spaces or special characters in copied URL

## 📂 Modified Files

1. **src/main/java/com/exam/service/EmailService.java** (NEW)
2. **src/main/java/com/exam/service/UserService.java** (UPDATED)
3. **src/main/java/com/exam/Controller/AuthController.java** (UPDATED)
4. **src/main/java/com/exam/config/SecurityConfig.java** (UPDATED)
5. **src/main/resources/templates/verification-success.html** (NEW)
6. **src/main/resources/templates/verification-failure.html** (NEW)
7. **src/main/resources/application.properties** (UPDATED)
8. **pom.xml** (UPDATED - added spring-boot-starter-mail)

## ✨ Ready to Test!

Once you've configured the email settings in `application.properties`, restart the application and try registering a new account!
