package com.example.network

import kotlinx.serialization.Serializable
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

@Serializable
data class GitHubOwner(
    val login: String,
    val avatar_url: String? = null
)

@Serializable
data class GitHubRepo(
    val id: Long,
    val name: String,
    val full_name: String,
    val private: Boolean,
    val html_url: String,
    val description: String? = null,
    val stargazers_count: Int = 0,
    val language: String? = null,
    val default_branch: String = "main",
    val updated_at: String? = null,
    val pushed_at: String? = null,
    val owner: GitHubOwner? = null
)

@Serializable
data class GitHubFile(
    val name: String,
    val path: String,
    val sha: String,
    val type: String,
    val download_url: String? = null
)

@Serializable
data class GitHubUser(
    val login: String,
    val avatar_url: String,
    val name: String? = null,
    val email: String? = null
)

@Serializable
data class GitHubHeadCommit(
    val id: String? = null,
    val message: String? = null,
    val timestamp: String? = null
)

@Serializable
data class GitHubWorkflowRun(
    val id: Long,
    val name: String? = null,
    val status: String, // e.g., "queued", "in_progress", "completed"
    val conclusion: String? = null, // e.g., "success", "failure", "cancelled"
    val html_url: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
    val run_started_at: String? = null,
    val head_branch: String? = null,
    val head_commit: GitHubHeadCommit? = null,
    val run_number: Int = 0
)

@Serializable
data class GitHubWorkflowRunsResponse(
    val total_count: Int = 0,
    val workflow_runs: List<GitHubWorkflowRun> = emptyList()
)

@Serializable
data class GitHubWorkflow(
    val id: Long,
    val name: String,
    val path: String,
    val state: String
)

@Serializable
data class GitHubWorkflowsResponse(
    val total_count: Int = 0,
    val workflows: List<GitHubWorkflow> = emptyList()
)

@Serializable
data class GitHubArtifact(
    val id: Long,
    val name: String,
    val size_in_bytes: Long = 0,
    val archive_download_url: String,
    val expired: Boolean = false,
    val created_at: String? = null
)

@Serializable
data class GitHubArtifactsResponse(
    val total_count: Int = 0,
    val artifacts: List<GitHubArtifact> = emptyList()
)

@Serializable
data class GitHubReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long = 0,
    val browser_download_url: String,
    val url: String? = null,
    val content_type: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null
)

@Serializable
data class GitHubRelease(
    val id: Long,
    val tag_name: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val created_at: String? = null,
    val published_at: String? = null,
    val assets: List<GitHubReleaseAsset> = emptyList()
)

@Serializable
data class WorkflowDispatchBody(
    val ref: String = "main"
)

@Serializable
data class GitHubSearchRepoResponse(
    val total_count: Int = 0,
    val incomplete_results: Boolean = false,
    val items: List<GitHubRepo> = emptyList()
)

interface GitHubApiService {
    @GET("user")
    suspend fun getUserProfile(
        @Header("Authorization") token: String?
    ): GitHubUser

    @GET("user/repos")
    suspend fun getUserRepos(
        @Header("Authorization") token: String?,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100
    ): List<GitHubRepo>

    @GET("users/{username}/repos")
    suspend fun getPublicUserRepos(
        @Header("Authorization") token: String? = null,
        @Path("username") username: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100
    ): List<GitHubRepo>

    @GET("orgs/{org}/repos")
    suspend fun getPublicOrgRepos(
        @Header("Authorization") token: String? = null,
        @Path("org") org: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100
    ): List<GitHubRepo>

    @GET("search/repositories")
    suspend fun searchRepositories(
        @Header("Authorization") token: String? = null,
        @Query("q") query: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100
    ): GitHubSearchRepoResponse

    @GET("repos/{owner}/{repo}")
    suspend fun getSingleRepo(
        @Header("Authorization") token: String? = null,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRepo

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getRepoContents(
        @Header("Authorization") token: String? = null,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String
    ): List<GitHubFile>

    @GET("repos/{owner}/{repo}/actions/runs")
    suspend fun getWorkflowRuns(
        @Header("Authorization") token: String? = null,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 20
    ): GitHubWorkflowRunsResponse

    @GET("repos/{owner}/{repo}/actions/workflows")
    suspend fun getWorkflows(
        @Header("Authorization") token: String? = null,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubWorkflowsResponse

    @POST("repos/{owner}/{repo}/actions/workflows/build.yml/dispatches")
    suspend fun triggerBuildWorkflow(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body body: WorkflowDispatchBody = WorkflowDispatchBody()
    ): Response<Unit>

    @POST("repos/{owner}/{repo}/actions/workflows/{workflow_id}/dispatches")
    suspend fun dispatchWorkflowById(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("workflow_id") workflowId: String,
        @Body body: WorkflowDispatchBody = WorkflowDispatchBody()
    ): Response<Unit>

    @POST("repos/{owner}/{repo}/actions/runs/{run_id}/rerun")
    suspend fun rerunWorkflowRun(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): Response<Unit>

    @GET("repos/{owner}/{repo}/actions/artifacts")
    suspend fun getAllRepoArtifacts(
        @Header("Authorization") token: String? = null,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 30
    ): GitHubArtifactsResponse

    @GET("repos/{owner}/{repo}/actions/runs/{run_id}/artifacts")
    suspend fun getRunArtifacts(
        @Header("Authorization") token: String? = null,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("run_id") runId: Long
    ): GitHubArtifactsResponse

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getReleases(
        @Header("Authorization") token: String? = null,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 10
    ): List<GitHubRelease>

    @Streaming
    @GET
    suspend fun downloadArtifactZip(
        @Header("Authorization") token: String? = null,
        @Url downloadUrl: String
    ): ResponseBody

    @Streaming
    @GET
    suspend fun downloadReleaseAsset(
        @Header("Authorization") token: String? = null,
        @Header("Accept") accept: String = "application/octet-stream",
        @Url url: String
    ): ResponseBody

    @GET("repos/{owner}/{repo}/releases")
    suspend fun getRepoReleases(
        @Header("Authorization") token: String? = null,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("per_page") perPage: Int = 10
    ): List<GitHubRelease>

    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Header("Authorization") token: String? = null,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubRelease
}
