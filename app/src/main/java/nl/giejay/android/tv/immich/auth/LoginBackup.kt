package nl.giejay.android.tv.immich.auth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Base64
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Keeps the last login in a file on shared storage (`/sdcard/ImmichTV/login.json`) so it survives
 * an uninstall / reinstall - the app's private storage does not. Needs legacy external storage
 * (Fire OS / Android 9 and older). The password is AES-GCM encrypted with a key derived from this
 * device's ANDROID_ID: that stops casual copying to another device, but it is not strong secrecy
 * because other apps with storage access can read the file and the device id.
 * A plain `"passwordPlain"` field is also accepted when reading, so the file can be edited by hand.
 */
object LoginBackup {
    data class Login(val host: String, val email: String, val password: String)

    private const val SALT = "immichtv-login-v1"
    private val file: File get() = File(Environment.getExternalStorageDirectory(), "ImmichTV/login.json")

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < 23 ||
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    fun save(context: Context, login: Login): Boolean {
        if (!hasPermission(context)) return false
        return try {
            file.parentFile?.mkdirs()
            val json = JSONObject()
                .put("host", login.host)
                .put("email", login.email)
                .put("password", encrypt(context, login.password))
            file.writeText(json.toString(2))
            true
        } catch (e: Exception) {
            Timber.e(e, "Could not write login backup")
            false
        }
    }

    fun load(context: Context): Login? {
        if (!hasPermission(context) || !file.isFile) return null
        return try {
            val json = JSONObject(file.readText())
            val password = json.optString("password").takeIf { it.isNotEmpty() }?.let { decrypt(context, it) }
                ?: json.optString("passwordPlain")
            Login(json.optString("host"), json.optString("email"), password)
        } catch (e: Exception) {
            Timber.e(e, "Could not read login backup")
            null
        }
    }

    private fun key(context: Context): SecretKeySpec {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "immichtv"
        val spec = PBEKeySpec(deviceId.toCharArray(), SALT.toByteArray(), 10_000, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded, "AES")
    }

    private fun encrypt(context: Context, plain: String): String {
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(context), GCMParameterSpec(128, iv)) }
        return Base64.encodeToString(iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
    }

    private fun decrypt(context: Context, stored: String): String? = try {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, key(context), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        String(cipher.doFinal(bytes, 12, bytes.size - 12))
    } catch (e: Exception) {
        Timber.w(e, "Could not decrypt login backup (different device?)")
        null
    }
}
