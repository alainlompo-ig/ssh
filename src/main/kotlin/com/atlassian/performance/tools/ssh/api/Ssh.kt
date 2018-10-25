package com.atlassian.performance.tools.ssh.api

import com.atlassian.performance.tools.jvmtasks.api.ExponentialBackoff
import com.atlassian.performance.tools.jvmtasks.api.IdempotentAction
import com.atlassian.performance.tools.ssh.SshjConnection
import net.schmizz.sshj.SSHClient
import java.time.Duration

/**
 * Connects to [host] via SSH.
 *
 * @param host remote SSH server we're connecting to.
 * @param connectivityPatience how many times we're going to try to connect to the server.
 */
data class Ssh(
    val host: SshHost,
    private val connectivityPatience: Int
) {

    /**
     * Connects to [host] via SSH with up to 4 retries.
     *
     * @param host remote SSH server we're connecting to.
     */
    constructor(
        host: SshHost
    ) : this(
        host = host,
        connectivityPatience = 4
    )

    /**
     * Connects to [host].
     *
     * @return A new [SshConnection].
     */
    fun newConnection(): SshConnection {
        return SshjConnection(
            prepareClient(),
            host.userName
        )
    }

    private fun prepareClient(): SSHClient {
        val ssh = SSHClient()
        ssh.connection.keepAlive.keepAliveInterval = 60
        ssh.addHostKeyVerifier { _, _, _ -> true }
        waitForConnectivity(ssh)
        host.authentication.authenticate(host.userName, ssh)
        return ssh
    }

    private fun waitForConnectivity(
        ssh: SSHClient
    ) {
        val address = host.ipAddress
        val port = host.port
        IdempotentAction("connect to $address on port $port") {
            ssh.connect(
                address,
                port
            )
        }.retry(
            maxAttempts = connectivityPatience,
            backoff = ExponentialBackoff(
                baseBackoff = Duration.ofSeconds(1)
            )
        )
    }
}