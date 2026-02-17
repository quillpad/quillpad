# Security Policy

## Security Features

Quillpad implements comprehensive security measures to protect your notes and personal data:

### Data Encryption

- **Encrypted Credential Storage**: All cloud provider credentials (Nextcloud, OneDrive, Google Drive) are stored using Android's `EncryptedSharedPreferences` with:
  - Master Key: AES256-GCM encryption
  - Key Encryption: AES256-SIV scheme
  - Value Encryption: AES256-GCM scheme
  - Master key stored in AndroidKeyStore

### Network Security

- **HTTPS Only**: All network traffic uses HTTPS, cleartext traffic is explicitly blocked
- **Certificate Pinning (Prepared)**: Infrastructure ready for certificate pinning on cloud providers (OneDrive, Google Drive). Will be enabled with valid certificate pins when OAuth flows are implemented.
- **Self-Signed Certificate Support**: Optional support for self-signed certificates for Nextcloud users with custom installations

### Data Protection

- **No Automatic Backups**: Android automatic backups are disabled (`allowBackup=false`, `fullBackupContent=false`) to prevent unencrypted data exposure through system backups
- **Manual Backup Control**: Users have full control over backups through the app's built-in backup/restore feature
- **Scoped Storage**: Media files are stored in app-private directories with FileProvider for secure sharing

### Code Security

- **ProGuard Obfuscation**: Release builds use ProGuard with enhanced obfuscation rules to protect against reverse engineering:
  - Source file renaming
  - Code optimization and shrinking
  - Resource shrinking
- **Debug Log Removal**: All debug logging (Log.d, Log.v, Log.i) is automatically removed in release builds when minification is enabled (ProGuard/R8)
- **Crash Reporting**: Source file and line number information is preserved for crash analysis while hiding implementation details

### Permissions

Quillpad follows the principle of least privilege:
- **Minimal Permissions**: Only requests permissions that are strictly necessary
- **Runtime Permission Requests**: Sensitive permissions are requested at runtime with clear explanations
- **No Excessive Access**: Does not request access to contacts, SMS, phone state, or other unnecessary device information

## Reporting a Vulnerability

If you discover a security vulnerability in Quillpad, please report it responsibly:

1. **Do Not** create a public GitHub issue for security vulnerabilities
2. Email the maintainers at: sixface@msn.com
3. Provide detailed information about the vulnerability:
   - Description of the issue
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if any)

We will acknowledge receipt of your report within 48 hours and provide updates on the fix timeline.

## Security Best Practices for Users

To keep your notes secure:

1. **Use Strong Passwords**: Set strong, unique passwords for cloud sync services
2. **Keep App Updated**: Always use the latest version of Quillpad to benefit from security updates
3. **Verify Certificates**: When using Nextcloud with self-signed certificates, verify the certificate fingerprint
4. **Review Permissions**: Regularly review and revoke permissions that are no longer needed
5. **Secure Your Device**: Use a device PIN/password and keep your Android OS updated

## Security Update Policy

- **Critical Security Issues**: Fixed and released within 1-7 days
- **High Severity Issues**: Fixed and released within 7-30 days
- **Medium/Low Severity Issues**: Fixed in the next regular release

## Third-Party Dependencies

We regularly monitor and update third-party dependencies for security vulnerabilities using:
- GitHub's Dependabot
- Manual security audits
- GitHub Advisory Database checks

Current security-sensitive dependencies:
- `androidx.security:security-crypto:1.1.0` - For encrypted credential storage
- Microsoft Graph SDK and MSAL - For OneDrive integration
- Google Drive API and Auth Library - For Google Drive integration
- All dependencies are checked against the GitHub Advisory Database before inclusion

## Compliance

Quillpad is designed with privacy and security in mind:
- **No Analytics**: No usage analytics or tracking
- **No Third-Party Services**: Notes are never sent to third-party services without explicit user consent
- **Open Source**: Full source code is available for security audits
- **User Control**: Users have complete control over their data and sync preferences

## Version History

### Recent Security Improvements (v1.5.8+)
- Prepared certificate pinning infrastructure for OneDrive and Google Drive (to be enabled with OAuth implementation)
- Enhanced ProGuard rules with source file obfuscation
- Disabled automatic Android backups
- Removed debug logging in release builds (when minification enabled)
- Added UI, configuration classes, and backend stubs for OneDrive and Google Drive sync providers
- All cloud credentials stored using EncryptedSharedPreferences
