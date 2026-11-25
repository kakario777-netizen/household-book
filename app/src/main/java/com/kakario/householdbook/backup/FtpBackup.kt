package com.kakario.householdbook.backup

import android.content.Context
import com.kakario.householdbook.data.Store
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.util.TrustManagerUtils
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.time.Duration
import java.util.Date
import java.util.Locale

/** FTP 서버 접속 정보. 비밀번호는 폰 보안 저장소(Keystore) 키로 암호화해서 저장 */
data class FtpConf(
    val host: String,
    val port: Int = 21,
    val user: String = "",
    val password: String = "",
    val encoding: String = "UTF-8",   // 한글 폴더 이름이 깨지면 EUC-KR
    val secure: Boolean = false,      // FTPS (보안 연결)
    val folder: String = "",          // 백업을 넣을 폴더
) {
    // 로그·오류에 비밀번호가 찍히지 않게
    override fun toString() = "FtpConf(host=$host, port=$port, folder=$folder)"
}

/**
 * FTP 서버(NAS) 백업·동기화.
 *  - 고른 폴더에 하루 한 파일(가계부_2026-11-25.json), 최근 30개만 남김
 *  - 접속 정보는 이 폰에만 저장 (백업 파일에도 안 들어감)
 */
object FtpBackup {
    const val KEEP = 30

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("book", Context.MODE_PRIVATE)

    fun load(ctx: Context): FtpConf? {
        val p = prefs(ctx)
        val host = p.getString("bk_ftp_host", "") ?: ""
        if (host.isEmpty()) return null
        return FtpConf(
            host = host,
            port = p.getInt("bk_ftp_port", 21),
            user = p.getString("bk_ftp_user", "") ?: "",
            password = SecretBox.decrypt(p.getString("bk_ftp_pw", "") ?: ""),
            encoding = p.getString("bk_ftp_enc", "UTF-8") ?: "UTF-8",
            secure = p.getBoolean("bk_ftp_secure", false),
            folder = p.getString("bk_ftp_folder", "") ?: "",
        )
    }

    fun save(ctx: Context, c: FtpConf?) {
        val e = prefs(ctx).edit()
        if (c == null) {
            listOf("bk_ftp_host", "bk_ftp_port", "bk_ftp_user", "bk_ftp_pw", "bk_ftp_enc", "bk_ftp_secure", "bk_ftp_folder")
                .forEach { e.remove(it) }
        } else {
            e.putString("bk_ftp_host", c.host.trim()).putInt("bk_ftp_port", c.port)
                .putString("bk_ftp_user", c.user.trim()).putString("bk_ftp_pw", SecretBox.encrypt(c.password))
                .putString("bk_ftp_enc", c.encoding).putBoolean("bk_ftp_secure", c.secure)
                .putString("bk_ftp_folder", c.folder)
        }
        e.apply()
    }

    /** 자동 백업이 켜져 있는가 (서버와 폴더까지 정해짐) */
    fun enabled(ctx: Context) = load(ctx)?.folder?.isNotEmpty() == true

    /* ---------- 접속 ---------- */

    class FtpError(msg: String) : IOException(msg)

    /** 접속해서 block 실행 후 닫음. 오류는 한국어로 */
    fun <T> session(c: FtpConf, block: (FTPClient) -> T): T {
        val client: FTPClient = if (c.secure) FTPSClient(false).apply {
            // NAS는 보통 자체 서명 인증서라 인증서 검사는 생략 (내용 암호화는 그대로)
            trustManager = TrustManagerUtils.getAcceptAllTrustManager()
        } else FTPClient()
        try {
            client.controlEncoding = c.encoding     // 한글 폴더 이름 (UTF-8 / EUC-KR)
            client.connectTimeout = 10_000
            client.setDefaultTimeout(15_000)
            client.connect(c.host, c.port)
            if (!FTPReply.isPositiveCompletion(client.replyCode)) throw FtpError("서버가 접속을 거부했어요 (응답 ${client.replyCode})")
            client.soTimeout = 20_000
            client.setDataTimeout(Duration.ofSeconds(30))
            val user = c.user.ifBlank { "anonymous" }
            val pass = if (c.user.isBlank()) "guest@" else c.password
            if (!client.login(user, pass)) throw FtpError("로그인에 실패했어요. 아이디와 비밀번호를 확인해 주세요.")
            (client as? FTPSClient)?.let { it.execPBSZ(0); it.execPROT("P") }
            if (c.encoding.equals("UTF-8", true)) runCatching { client.sendCommand("OPTS UTF8 ON") }
            client.setFileType(FTP.BINARY_FILE_TYPE)
            client.enterLocalPassiveMode()
            client.isRemoteVerificationEnabled = false
            return block(client)
        } catch (e: FtpError) {
            throw e
        } catch (e: UnknownHostException) {
            throw FtpError("주소를 찾을 수 없어요. 서버 주소를 확인해 주세요.")
        } catch (e: SocketTimeoutException) {
            throw FtpError("서버 응답이 없어요. 외부 접속이면 포트포워딩(${c.port}번, 패시브 포트)을 확인해 주세요.")
        } catch (e: ConnectException) {
            throw FtpError("서버에 연결할 수 없어요. 주소·포트를 확인해 주세요.")
        } catch (e: IOException) {
            throw FtpError("연결 중 문제가 생겼어요: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            runCatching { if (client.isConnected) { client.logout(); client.disconnect() } }
        }
    }

    /** 처음 접속 폴더 */
    fun home(c: FtpConf): String = session(c) { it.printWorkingDirectory()?.ifBlank { null } ?: "/" }

    /** 폴더 안의 하위 폴더 이름들 */
    fun listDirs(c: FtpConf, path: String): List<String> = session(c) { cl ->
        if (!cl.changeWorkingDirectory(path)) throw FtpError("폴더를 열 수 없어요: $path")
        cl.listFiles().filterNotNull()
            .filter { it.isDirectory && it.name != "." && it.name != ".." && !it.name.startsWith(".") }
            .map { it.name }.sortedWith(String.CASE_INSENSITIVE_ORDER)
    }

    fun makeDir(c: FtpConf, path: String) = session(c) { cl ->
        if (!cl.makeDirectory(path)) throw FtpError("폴더를 못 만들었어요 (쓰기 권한 확인, 응답 ${cl.replyCode})")
    }

    fun join(dir: String, name: String) = if (dir.endsWith("/")) dir + name else "$dir/$name"

    fun parent(path: String): String {
        val p = path.trimEnd('/')
        val i = p.lastIndexOf('/')
        return if (i <= 0) "/" else p.substring(0, i)
    }

    /* ---------- 백업 / 목록 / 복원 ---------- */

    data class BackupFile(val name: String, val size: Long)

    /** 오늘 날짜 파일로 올림 (같은 날 다시 하면 덮어씀). 돌려주는 값 = 파일 이름 */
    fun backupNow(ctx: Context): String {
        val c = load(ctx) ?: throw FtpError("FTP 서버가 등록돼 있지 않아요")
        if (c.folder.isEmpty()) throw FtpError("백업 폴더를 골라 주세요")
        val name = "가계부_" + SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date()) + ".json"
        val data = Store.exportAll(ctx).toString().toByteArray(Charsets.UTF_8)
        session(c) { cl ->
            if (!cl.changeWorkingDirectory(c.folder)) throw FtpError("백업 폴더를 열 수 없어요: ${c.folder}")
            // 임시 이름으로 올린 뒤 바꿔서, 중간에 끊겨도 기존 파일이 깨지지 않게
            val tmp = "$name.part"
            if (!cl.storeFile(tmp, ByteArrayInputStream(data))) throw FtpError("파일을 못 올렸어요 (쓰기 권한 확인, 응답 ${cl.replyCode})")
            cl.deleteFile(name)
            if (!cl.rename(tmp, name)) throw FtpError("파일 이름을 못 바꿨어요 (응답 ${cl.replyCode})")
            // 오래된 백업 정리 (최근 30개)
            backupsIn(cl).drop(KEEP).forEach { cl.deleteFile(it.name) }
        }
        return name
    }

    private fun backupsIn(cl: FTPClient): List<BackupFile> =
        cl.listFiles().filterNotNull()
            .filter { it.isFile && it.name.startsWith("가계부_") && it.name.endsWith(".json") }
            .map { BackupFile(it.name, it.size) }
            .sortedByDescending { it.name }

    fun list(ctx: Context): List<BackupFile> {
        val c = load(ctx) ?: return emptyList()
        return session(c) { cl ->
            if (!cl.changeWorkingDirectory(c.folder)) throw FtpError("백업 폴더를 열 수 없어요: ${c.folder}")
            backupsIn(cl)
        }
    }

    fun restore(ctx: Context, name: String) {
        val c = load(ctx) ?: throw FtpError("FTP 서버가 등록돼 있지 않아요")
        val bytes = session(c) { cl ->
            if (!cl.changeWorkingDirectory(c.folder)) throw FtpError("백업 폴더를 열 수 없어요: ${c.folder}")
            val out = ByteArrayOutputStream()
            if (!cl.retrieveFile(name, out)) throw FtpError("파일을 못 받았어요 (응답 ${cl.replyCode})")
            out.toByteArray()
        }
        Store.importAll(ctx, JSONObject(bytes.toString(Charsets.UTF_8)))
    }
}
