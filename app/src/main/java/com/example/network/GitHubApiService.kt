package com.example.network

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    val full_name: String,
    val private: Boolean,
    val html_url: String,
    val description: String? = null
)

@Serializable
data class GitHubFile(
    val name: String,
    val path: String,
    val sha: String,
    val type: String,
    val download_url: String? = null
)

interface GitHubApiService {
    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") token: String
    ): List<GitHubRepo>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getRepoContents(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): List<GitHubFile>
}
