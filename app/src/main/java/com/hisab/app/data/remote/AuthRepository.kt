package com.hisab.app.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await

/**
 * Phase 1: this class works standalone and is ready to call, but nothing in
 * the app calls it yet — there's no login screen. Until it's wired in
 * (Phase 2/3), the app runs on a local-only identity (see LocalUserProvider)
 * so Dashboard and Add Expense work fully offline with no sign-in required.
 */
class AuthRepository(
    private val firebaseAuth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val currentUser: FirebaseUser? get() = firebaseAuth.currentUser
    val isSignedIn: Boolean get() = currentUser != null

    suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser> = try {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        Result.success(result.user!!)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser> = try {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        Result.success(result.user!!)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser> = try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        val result = firebaseAuth.signInWithCredential(credential).await()
        Result.success(result.user!!)
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> = try {
        firebaseAuth.sendPasswordResetEmail(email).await()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun signOut() = firebaseAuth.signOut()
}
