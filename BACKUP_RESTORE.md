# Backup & restore — user guide

**Open:** Home → ⋮ (More) → Backup & restore.

## Make a backup
1. Tap **Save to a file** (pick a folder with the system file picker) or **Share** (send it to another app).
2. Optionally set a password. A password-protected file cannot be opened without it and **the password cannot be recovered**. Leave it empty for an unprotected file.
3. Keep the file somewhere private. It contains all your financial data; Hisab never uploads it anywhere and the Share option uses a private, non-exported file provider.

## Restore
1. Tap **Choose a backup file**, pick the `.hisab` file (enter the password if asked).
2. Read the preview: when it was made, what it contains, and what you currently have.
3. Confirm **Replace my data**. Restore **replaces** everything (nothing is merged). Hisab first saves a safety copy of your current data inside the app (the 3 newest are kept), and if anything fails your data stays exactly as it was.

## Refused files
Not a Hisab backup, damaged/edited (checksum mismatch), made by a newer version of the app, duplicate ids, invalid amounts, or a wrong password — you get a message and nothing is changed.

## File format
UTF-8 JSON: `app`, `formatVersion` (1), `schemaVersion` (3), `createdAtMillis`, `appVersion`, per-table `counts`, `checksum` (SHA-256 of the tables block) and `tables`. Encrypted files are a JSON wrapper (`salt`, `iv`, `data`, AES-256-GCM, PBKDF2-HMAC-SHA256 210,000 iterations). Older schema versions restore with defaults for columns that did not exist yet.
