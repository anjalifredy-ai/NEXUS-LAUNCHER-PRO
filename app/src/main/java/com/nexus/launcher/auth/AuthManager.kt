package com.nexus.launcher.auth

import com.nexus.launcher.data.model.Account
import com.nexus.launcher.data.model.OfflineAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auth orchestrator. Full Microsoft / Ely.by flows come in next batches.
 * Currently supports offline accounts so LaunchPipeline can compile and run.
 */
@Singleton
class AuthManager @Inject constructor() {

    private val _activeAccount = MutableStateFlow<Account?>(null)
    val activeAccount: StateFlow<Account?> = _activeAccount.asStateFlow()

    private val accounts = mutableListOf<Account>()

    fun getActiveAccount(): Account? = _activeAccount.value

    suspend fun validateActiveAccount(): Boolean {
        val account = _activeAccount.value ?: return false
        // Offline always valid; MSA/Ely.by token refresh will be added later
        return true
    }

    fun loginOffline(username: String): OfflineAccount {
        val account = OfflineAccount(
            id = UUID.randomUUID().toString(),
            username = username,
            uuid = UUID.nameUUIDFromBytes("OfflinePlayer:$username".toByteArray()).toString().replace("-", ""),
            accessToken = "0"
        )
        accounts.add(account)
        _activeAccount.value = account
        return account
    }

    fun setActiveAccount(account: Account) {
        _activeAccount.value = account
    }

    fun getAllAccounts(): List<Account> = accounts.toList()

    fun logout() {
        _activeAccount.value = null
    }
}
